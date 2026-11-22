import hashlib
import hmac
import json
import os
import re
import secrets
from urllib.error import URLError
from urllib.request import Request, urlopen
from uuid import UUID, uuid4

from fastapi import APIRouter, HTTPException, Request as ApiRequest, Response
from pydantic import BaseModel, Field
from psycopg.types.json import Jsonb
from psycopg.errors import UniqueViolation

from database import connect

router = APIRouter(prefix="/v1/auth")
EMAIL = re.compile(r"[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9-]+(?:\.[a-z0-9-]+)+", re.ASCII)


class ChallengeRequest(BaseModel):
    email: str = Field(max_length=254)


class VerificationRequest(BaseModel):
    challenge: UUID
    code: str = Field(pattern=r"^[0-9]{6}$")


class AccountProfile(BaseModel):
    name: str = Field(min_length=2, max_length=50)
    username: str = Field(pattern=r"^[a-zA-Z0-9_.]{3,24}$")
    bio: str = Field(max_length=160)
    city: str = Field(min_length=1, max_length=80)
    interests: list[str] = Field(max_length=20)
    discoverable: bool | None = None


def code_digest(secret: str, challenge: UUID, code: str) -> bytes:
    return hmac.digest(secret.encode(), f"{challenge}:{code}".encode(), "sha256")


def send_email(email: str, code: str, api_key: str, sender: str) -> None:
    payload = json.dumps({
        "from": sender,
        "to": [email],
        "subject": "Your ROMEAR verification code",
        "text": f"Your verification code is {code}. It expires in 10 minutes.",
    }).encode()
    request = Request(
        "https://api.resend.com/emails",
        data=payload,
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
    )
    try:
        with urlopen(request, timeout=8):
            pass
    except (URLError, TimeoutError) as exc:
        raise HTTPException(503, "Unable to send a code right now") from exc


def signing_secret() -> str:
    secret = os.getenv("AUTH_CODE_SECRET", "")
    if len(secret) < 32:
        raise HTTPException(503, "Email verification is unavailable")
    return secret


@router.post("/challenges", status_code=201)
def create_challenge(data: ChallengeRequest, response: Response):
    email = data.email.strip().lower()
    if not EMAIL.fullmatch(email) or email.endswith(("@example.com", ".invalid")):
        raise HTTPException(422, "Enter a valid email address")
    secret = signing_secret()
    api_key, sender = os.getenv("RESEND_API_KEY"), os.getenv("AUTH_EMAIL_FROM")
    if not api_key or not sender:
        raise HTTPException(503, "Email verification is unavailable")
    challenge = uuid4()
    code = f"{secrets.randbelow(1_000_000):06d}"
    with connect() as db:
        db.execute("SELECT pg_advisory_xact_lock(610018)")
        db.execute("DELETE FROM email_challenge WHERE created_at < now() - interval '1 day'")
        total, recent, latest = db.execute(
            """
            SELECT count(*), count(*) FILTER (WHERE email = %s AND created_at > now() - interval '1 hour'),
                   count(*) FILTER (WHERE email = %s AND created_at > now() - interval '1 minute')
            FROM email_challenge
            """,
            (email, email),
        ).fetchone()
        if total >= int(os.getenv("AUTH_DAILY_LIMIT", "100")) or recent >= 5 or latest:
            raise HTTPException(429, "Too many code requests. Try again later.")
        db.execute(
            "INSERT INTO email_challenge (id, email, digest) VALUES (%s, %s, %s)",
            (challenge, email, code_digest(secret, challenge, code)),
        )
    send_email(email, code, api_key, sender)
    with connect() as db:
        db.execute("UPDATE email_challenge SET delivered = true WHERE id = %s", (challenge,))
    response.headers["Cache-Control"] = "no-store"
    return {"challenge": challenge, "expires_in": 600}


@router.post("/verify")
def verify(data: VerificationRequest, response: Response):
    digest = code_digest(signing_secret(), data.challenge, data.code)
    token = secrets.token_hex(32)
    account = None
    result = None
    with connect() as db:
        row = db.execute(
            """
            UPDATE email_challenge SET attempts = attempts + 1
            WHERE id = %s AND delivered AND attempts < 5 AND NOT consumed
              AND created_at > now() - interval '10 minutes'
            RETURNING email, digest
            """,
            (data.challenge,),
        ).fetchone()
        if row and hmac.compare_digest(row[1], digest):
            db.execute("UPDATE email_challenge SET consumed = true WHERE id = %s", (data.challenge,))
            account = db.execute(
                """
                INSERT INTO account (id, email) VALUES (%s, %s)
                ON CONFLICT (email) DO UPDATE SET email = EXCLUDED.email RETURNING id
                """,
                (uuid4(), row[0]),
            ).fetchone()[0]
            result = issue_session(db, account, token)
    if account is None:
        raise HTTPException(401, "Code is invalid or expired")
    response.headers["Cache-Control"] = "no-store"
    return result


def issue_session(db, account: UUID, token: str):
    saved = db.execute("SELECT profile FROM account_profile WHERE account_id = %s", (account,)).fetchone()
    db.execute("DELETE FROM account_session WHERE expires_at <= now()")
    db.execute(
        """
        INSERT INTO account_session (token_hash, account_id, expires_at)
        VALUES (%s, %s, now() + interval '30 days')
        """, (hashlib.sha256(bytes.fromhex(token)).digest(), account),
    )
    return {"account_id": account, "token": token, "profile": saved[0] if saved else None}


@router.post("/device")
def device_account(request: ApiRequest, response: Response, restore_only: bool = False):
    if os.getenv("ENABLE_DEVICE_ACCOUNTS", "true").lower() != "true":
        raise HTTPException(404, "Device sign-in is unavailable")
    key = session_hash(request)
    with connect() as db:
        db.execute("SELECT pg_advisory_xact_lock(610019)")
        existing = db.execute("SELECT account_id FROM device_account WHERE token_hash = %s", (key,)).fetchone()
        if existing:
            account = existing[0]
            db.execute("DELETE FROM account_session WHERE account_id = %s", (account,))
        else:
            if restore_only:
                raise HTTPException(404, "This device no longer has account access")
            total, recent = db.execute(
                "SELECT count(*), count(*) FILTER (WHERE created_at > now() - interval '1 hour') FROM device_account"
            ).fetchone()
            if total >= int(os.getenv("MAX_DEVICE_ACCOUNTS", "100")) or recent >= 20:
                raise HTTPException(429, "New registrations are temporarily limited")
            account = uuid4()
            db.execute("INSERT INTO account (id, email) VALUES (%s, %s)", (account, f"{account}@device.romear.invalid"))
            db.execute("INSERT INTO device_account (token_hash, account_id) VALUES (%s, %s)", (key, account))
        result = issue_session(db, account, secrets.token_hex(32))
    response.headers["Cache-Control"] = "no-store"
    return result


class TransferCode(BaseModel):
    code: str = Field(pattern=r"^[a-f0-9]{40}$")


@router.post("/device/transfer-code")
def create_transfer(request: ApiRequest, response: Response):
    code = secrets.token_hex(20)
    digest = hashlib.sha256(bytes.fromhex(code)).digest()
    with connect() as db:
        db.execute("SELECT pg_advisory_xact_lock(610019)")
        account = authenticated_account(db, request)
        if not db.execute("SELECT 1 FROM device_account WHERE account_id = %s", (account,)).fetchone():
            raise HTTPException(409, "Device transfer is available for device accounts")
        db.execute(
            """
            INSERT INTO device_transfer (account_id, code_hash, expires_at)
            VALUES (%s, %s, now() + interval '10 minutes')
            ON CONFLICT (account_id) DO UPDATE SET code_hash = EXCLUDED.code_hash,
                expires_at = EXCLUDED.expires_at, redeemed_by = NULL
            """, (account, digest),
        )
    response.headers["Cache-Control"] = "no-store"
    return {"code": code, "expires_in": 600}


@router.post("/device/transfer")
def redeem_transfer(data: TransferCode, request: ApiRequest, response: Response):
    receiving_key = session_hash(request)
    digest = hashlib.sha256(bytes.fromhex(data.code)).digest()
    with connect() as db:
        db.execute("SELECT pg_advisory_xact_lock(610019)")
        transfer = db.execute(
            "SELECT account_id, redeemed_by FROM device_transfer WHERE code_hash = %s AND expires_at > now()",
            (digest,),
        ).fetchone()
        if not transfer or (transfer[1] is not None and transfer[1] != receiving_key):
            raise HTTPException(404, "This transfer code is invalid or expired")
        account, redeemed_by = transfer
        existing = db.execute("SELECT account_id FROM device_account WHERE token_hash = %s", (receiving_key,)).fetchone()
        if existing and (existing[0] != account or redeemed_by is None):
            raise HTTPException(409, "Use the code on a different installation without an account")
        db.execute("UPDATE device_account SET token_hash = %s WHERE account_id = %s", (receiving_key, account))
        db.execute("UPDATE device_transfer SET redeemed_by = %s WHERE account_id = %s", (receiving_key, account))
        db.execute("DELETE FROM account_session WHERE account_id = %s", (account,))
        result = issue_session(db, account, secrets.token_hex(32))
    response.headers["Cache-Control"] = "no-store"
    return result


def session_hash(request: ApiRequest) -> bytes:
    token = re.fullmatch(r"Bearer ([a-f0-9]{64})", request.headers.get("authorization", ""))
    if token is None:
        raise HTTPException(401, "Sign in required")
    return hashlib.sha256(bytes.fromhex(token[1])).digest()


def authenticated_account(db, request: ApiRequest) -> UUID:
    row = db.execute(
        "SELECT account_id FROM account_session WHERE token_hash = %s AND expires_at > now()",
        (session_hash(request),),
    ).fetchone()
    if row is None:
        raise HTTPException(401, "Sign in required")
    return row[0]


@router.get("/session")
def session(request: ApiRequest, response: Response):
    with connect() as db:
        account = authenticated_account(db, request)
    response.headers["Cache-Control"] = "no-store"
    return {"account_id": account}


@router.put("/profile", status_code=204)
def update_profile(profile: AccountProfile, request: ApiRequest):
    if any(not 0 < len(interest) <= 40 for interest in profile.interests):
        raise HTTPException(422, "Invalid interests")
    try:
        with connect() as db:
            account = authenticated_account(db, request)
            db.execute(
                """
                INSERT INTO account_profile (account_id, profile, discoverable) VALUES (%s, %s, COALESCE(%s, false))
                ON CONFLICT (account_id) DO UPDATE SET profile = EXCLUDED.profile,
                    discoverable = COALESCE(%s, account_profile.discoverable)
                """,
                (account, Jsonb(profile.model_dump(exclude={"discoverable"})), profile.discoverable, profile.discoverable),
            )
    except UniqueViolation as exc:
        raise HTTPException(409, "That username is already taken") from exc
    return Response(status_code=204)


@router.delete("/session", status_code=204)
def revoke(request: ApiRequest):
    with connect() as db:
        db.execute("DELETE FROM account_session WHERE token_hash = %s", (session_hash(request),))
    return Response(status_code=204)


@router.delete("/account", status_code=204)
def delete_account(request: ApiRequest):
    with connect() as db:
        account = authenticated_account(db, request)
        db.execute("DELETE FROM account WHERE id = %s", (account,))
    return Response(status_code=204)
