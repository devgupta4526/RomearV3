from datetime import date
from typing import Literal
from uuid import UUID

from fastapi import APIRouter, HTTPException, Request, Response
from psycopg.types.json import Jsonb
from pydantic import Field

from accounts import authenticated_account
from community import Input
from database import connect

router = APIRouter(prefix="/v1/community/library")
CITIES = {"Rome", "Milan", "Florence", "Naples", "Bologna", "Venice"}


class Draft(Input):
    title: str = Field(min_length=3, max_length=80)
    description: str = Field(min_length=10, max_length=2000)
    date: date
    location: str | None = Field(default=None, min_length=3, max_length=120)
    discount: int | None = Field(default=None, ge=1, le=90)


@router.get("")
def library(request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        rows = db.execute(
            "SELECT kind, id, payload FROM account_library WHERE account_id = %s ORDER BY updated_at DESC, id",
            (me,),
        ).fetchall()
    result = {"events": [], "offers": [], "stamps": []}
    for kind, identifier, payload in rows:
        if kind == "stamps":
            result[kind].append(identifier)
        else:
            result[kind].append(dict(payload, id=identifier))
    return result


@router.put("/stamps/{city}", status_code=204)
def stamp(city: str, request: Request):
    if city not in CITIES:
        raise HTTPException(404, "City unavailable")
    with connect() as db:
        me = authenticated_account(db, request)
        db.execute(
            "INSERT INTO account_library (account_id, kind, id, payload) VALUES (%s, 'stamps', %s, '{}'::jsonb) "
            "ON CONFLICT DO NOTHING", (me, city),
        )
    return Response(status_code=204)


@router.put("/{kind}/{identifier}", status_code=204)
def save_draft(kind: Literal["events", "offers"], identifier: UUID, draft: Draft, request: Request):
    if (kind == "events" and (draft.location is None or draft.discount is not None)) or (
        kind == "offers" and (draft.discount is None or draft.location is not None)
    ):
        raise HTTPException(422, "Check the draft details")
    with connect() as db:
        me = authenticated_account(db, request)
        db.execute("SELECT id FROM account WHERE id = %s FOR NO KEY UPDATE", (me,))
        if not db.execute(
            """
            INSERT INTO account_library (account_id, kind, id, payload)
            SELECT %(me)s, %(kind)s, %(id)s, %(payload)s
            WHERE EXISTS (SELECT 1 FROM account_library WHERE account_id = %(me)s AND kind = %(kind)s AND id = %(id)s)
               OR (SELECT count(*) FROM account_library WHERE account_id = %(me)s AND kind = %(kind)s) < 50
            ON CONFLICT (account_id, kind, id) DO UPDATE SET payload = EXCLUDED.payload, updated_at = now()
            RETURNING id
            """, {"me": me, "kind": kind, "id": str(identifier), "payload": Jsonb(draft.model_dump(mode="json"))},
        ).fetchone():
            raise HTTPException(409, "Your draft limit has been reached")
    return Response(status_code=204)


@router.delete("/{kind}/{identifier}", status_code=204)
def remove(kind: Literal["stamps", "events", "offers"], identifier: str, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        db.execute("DELETE FROM account_library WHERE account_id = %s AND kind = %s AND id = %s",
                   (me, kind, identifier))
    return Response(status_code=204)
