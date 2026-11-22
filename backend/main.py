import hashlib
import json
import os
import re
from contextlib import asynccontextmanager
from pathlib import Path

import psycopg
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from psycopg.types.json import Jsonb
from starlette.concurrency import run_in_threadpool
from accounts import router as account_router
from database import connect

MAX_BODY = 128 * 1024
TOKEN = re.compile(r"Bearer ([a-f0-9]{64})")


@asynccontextmanager
async def lifespan(app: FastAPI):
    schema = Path(__file__).with_name("schema.sql").read_text(encoding="utf-8")

    def migrate():
        with connect() as db:
            db.execute(schema)

    await run_in_threadpool(migrate)
    yield


app = FastAPI(title="ROMEAR API", lifespan=lifespan, docs_url=None, redoc_url=None)
app.include_router(account_router)


@app.exception_handler(psycopg.Error)
async def database_error(request, exc):
    return JSONResponse({"detail": "Storage temporarily unavailable"}, status_code=503)


def installation(request: Request) -> bytes:
    match = TOKEN.fullmatch(request.headers.get("authorization", ""))
    if match is None:
        raise HTTPException(401, "Installation credential required")
    return hashlib.sha256(bytes.fromhex(match[1])).digest()


@app.get("/health")
def health():
    # A liveness probe must not wake Neon on every Render health check.
    return {"status": "ok"}


@app.get("/v1/state")
def get_state(request: Request):
    key = installation(request)
    with connect() as db:
        row = db.execute(
            "SELECT revision, state FROM installation_state WHERE token_hash = %s", (key,)
        ).fetchone()
    if row is None:
        raise HTTPException(404, "State has not been saved")
    return JSONResponse({"revision": row[0], "state": row[1]}, headers={"Cache-Control": "no-store"})


def store(key, revision, state):
    with connect() as db:
        values = (revision, Jsonb(state), key, revision)
        updated = db.execute(
            """
            UPDATE installation_state SET revision = %s, state = %s
            WHERE token_hash = %s AND revision < %s RETURNING revision
            """,
            values,
        ).fetchone()
        if updated:
            return updated[0]

        current = db.execute(
            "SELECT revision FROM installation_state WHERE token_hash = %s", (key,)
        ).fetchone()
        if current and current[0] >= revision:
            return current[0]

        # Serialize registration so concurrent new installations cannot exceed the cap.
        db.execute("SELECT pg_advisory_xact_lock(610017)")
        created = db.execute(
            """
            INSERT INTO installation_state (token_hash, revision, state)
            SELECT %s, %s, %s
            WHERE (SELECT count(*) FROM installation_state) < %s
               OR EXISTS (SELECT 1 FROM installation_state WHERE token_hash = %s)
            ON CONFLICT (token_hash) DO UPDATE SET revision = EXCLUDED.revision, state = EXCLUDED.state
            WHERE installation_state.revision < EXCLUDED.revision
            RETURNING revision
            """,
            (key, revision, Jsonb(state), int(os.getenv("MAX_INSTALLATIONS", "100")), key),
        ).fetchone()
        if created:
            return created[0]
        current = db.execute(
            "SELECT revision FROM installation_state WHERE token_hash = %s", (key,)
        ).fetchone()
        if current:
            return current[0]
        raise HTTPException(503, "Installation limit reached")


@app.put("/v1/state")
async def put_state(request: Request):
    key = installation(request)
    body = bytearray()
    async for chunk in request.stream():
        if len(body) + len(chunk) > MAX_BODY:
            raise HTTPException(413, "State is too large")
        body.extend(chunk)
    try:
        data = json.loads(body.decode("utf-8"))
        revision, state = data["revision"], data["state"]
        if type(revision) is not int or not 0 < revision <= 2**63 - 1 or not isinstance(state, dict):
            raise ValueError
        # PostgreSQL JSONB rejects non-JSON numbers, NUL and unpaired surrogates.
        json.dumps(state, allow_nan=False, ensure_ascii=False).encode("utf-8")
    except (ValueError, TypeError, KeyError, RecursionError):
        raise HTTPException(422, "Expected a positive revision and a JSON state object")
    try:
        accepted = await run_in_threadpool(store, key, revision, state)
    except psycopg.DataError:
        raise HTTPException(422, "State contains values PostgreSQL cannot store")
    return {"revision": accepted}
