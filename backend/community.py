from datetime import date, datetime
from typing import Literal
from uuid import UUID
from zoneinfo import ZoneInfo

from fastapi import APIRouter, HTTPException, Request, Response
from psycopg.rows import dict_row
from pydantic import BaseModel, ConfigDict, Field

from accounts import authenticated_account
from database import connect

router = APIRouter(prefix="/v1/community")
ROME = ZoneInfo("Europe/Rome")
TIME = r"^(?:[1-9]|1[0-2]):[0-5][0-9] (?:AM|PM)$"
BOOKABLE = {"market", "sunday-run", "coffee-meet", "riverside", "brew", "studio"}
CLUBS = ("runners", "cafe-circle", "tech", "urban")


class Input(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")


class Plan(Input):
    id: UUID
    item_id: str
    day: date
    time_label: str = Field(pattern=TIME)
    guests: int = Field(ge=1, le=8)


class Message(Input):
    id: UUID
    body: str = Field(min_length=1, max_length=2000)


class Pool(Input):
    id: UUID
    title: str = Field(min_length=3, max_length=80)
    location: str = Field(min_length=3, max_length=120)
    description: str = Field(min_length=20, max_length=2000)
    day: date
    time_label: str = Field(pattern=TIME)
    capacity: int = Field(ge=2, le=12)


class Decision(Input):
    status: Literal["approved", "declined", "pending"]


def public_peer(db, viewer: UUID, peer: UUID):
    if viewer == peer or not db.execute(
        "SELECT 1 FROM account_profile WHERE account_id = %s AND discoverable", (peer,)
    ).fetchone():
        raise HTTPException(404, "Profile unavailable")


def account_city(db, account):
    profile = db.execute("SELECT profile->>'city' FROM account_profile WHERE account_id = %s", (account,)).fetchone()
    if profile is None:
        raise HTTPException(409, "Complete your profile first")
    return profile[0]


@router.get("/people")
def people(request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        with db.cursor(row_factory=dict_row) as cursor:
            rows = cursor.execute(
                """
                SELECT p.account_id AS id, p.profile, (f.peer_id IS NOT NULL) AS following
                FROM account_profile p
                LEFT JOIN account_follow f ON f.account_id = %s AND f.peer_id = p.account_id
                WHERE p.discoverable AND p.account_id <> %s
                ORDER BY (p.profile->>'city' = %s) DESC, p.account_id LIMIT 100
                """, (me, me, account_city(db, me)),
            ).fetchall()
    return {"people": rows}


@router.put("/following/{peer}", status_code=204)
def follow(peer: UUID, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        public_peer(db, me, peer)
        db.execute("INSERT INTO account_follow VALUES (%s, %s) ON CONFLICT DO NOTHING", (me, peer))
    return Response(status_code=204)


@router.get("/people/{peer}")
def person(peer: UUID, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        with db.cursor(row_factory=dict_row) as cursor:
            row = cursor.execute(
                """
                SELECT p.account_id AS id, p.profile, (f.peer_id IS NOT NULL) AS following
                FROM account_profile p LEFT JOIN account_follow f ON f.account_id = %s AND f.peer_id = p.account_id
                WHERE p.account_id = %s AND p.discoverable
                """, (me, peer),
            ).fetchone()
        if not row:
            raise HTTPException(404, "Profile unavailable")
        return {"person": row}


@router.delete("/following/{peer}", status_code=204)
def unfollow(peer: UUID, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        db.execute("DELETE FROM account_follow WHERE account_id = %s AND peer_id = %s", (me, peer))
    return Response(status_code=204)


@router.get("/inbox")
def inbox(request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        with db.cursor(row_factory=dict_row) as cursor:
            rows = cursor.execute(
                """
                WITH messages AS (
                    SELECT recipient_id AS peer, body, sent_at, true AS mine, id
                    FROM direct_message WHERE sender_id = %(me)s
                    UNION ALL
                    SELECT sender_id, body, sent_at, false, id
                    FROM direct_message WHERE recipient_id = %(me)s
                ), recent AS (
                    SELECT DISTINCT ON (peer) * FROM messages ORDER BY peer, sent_at DESC, id DESC
                )
                SELECT recent.*, account_profile.profile FROM recent
                JOIN account_profile ON account_profile.account_id = recent.peer
                ORDER BY sent_at DESC, id DESC LIMIT 100
                """, {"me": me},
            ).fetchall()
    return {"conversations": rows}


@router.get("/messages/{peer}")
def messages(peer: UUID, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        with db.cursor(row_factory=dict_row) as cursor:
            rows = cursor.execute(
                """
                SELECT id, body, sent_at, sender_id = %(me)s AS mine FROM direct_message
                WHERE (sender_id = %(me)s AND recipient_id = %(peer)s)
                   OR (sender_id = %(peer)s AND recipient_id = %(me)s)
                ORDER BY sent_at DESC, id DESC LIMIT 100
                """, {"me": me, "peer": peer},
            ).fetchall()
    return {"messages": list(reversed(rows))}


@router.put("/messages/{peer}", status_code=204)
def send(peer: UUID, message: Message, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        if me == peer or not db.execute(
            "SELECT 1 FROM account_profile WHERE account_id = %s AND discoverable", (me,)
        ).fetchone():
            raise HTTPException(403, "Publish your profile to send messages")
        db.execute("SELECT id FROM account WHERE id = %s FOR NO KEY UPDATE", (me,))
        existing = db.execute(
            "SELECT recipient_id, body FROM direct_message WHERE sender_id = %s AND id = %s",
            (me, message.id),
        ).fetchone()
        if existing:
            if existing != (peer, message.body):
                raise HTTPException(409, "Message ID already used")
            return Response(status_code=204)
        if not db.execute(
            "SELECT 1 FROM direct_message WHERE sender_id = %s AND recipient_id = %s LIMIT 1", (peer, me)
        ).fetchone():
            public_peer(db, me, peer)
        lifetime, recent = db.execute(
            "SELECT count(*), count(*) FILTER (WHERE sent_at > now() - interval '1 minute') "
            "FROM direct_message WHERE sender_id = %s",
            (me,),
        ).fetchone()
        if lifetime >= 10_000 or recent >= 30:
            raise HTTPException(429, "Wait before sending more messages")
        db.execute(
            "INSERT INTO direct_message (sender_id, recipient_id, id, body) VALUES (%s, %s, %s, %s)",
            (me, peer, message.id, message.body),
        )
    return Response(status_code=204)


@router.get("/plans")
def plans(request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        with db.cursor(row_factory=dict_row) as cursor:
            rows = cursor.execute(
                "SELECT id, item_id, day, time_label, guests, cancelled FROM personal_plan "
                "WHERE account_id = %s ORDER BY day DESC, id DESC LIMIT 250", (me,),
            ).fetchall()
    return {"plans": rows}


@router.put("/plans", status_code=204)
def create_plan(plan: Plan, request: Request):
    if plan.item_id not in BOOKABLE:
        raise HTTPException(422, "This item is unavailable for personal plans")
    with connect() as db:
        me = authenticated_account(db, request)
        db.execute("SELECT id FROM account WHERE id = %s FOR NO KEY UPDATE", (me,))
        existing = db.execute(
            "SELECT account_id, item_id, day, time_label, guests FROM personal_plan WHERE id = %s", (plan.id,),
        ).fetchone()
        if existing:
            if existing != (me, plan.item_id, plan.day, plan.time_label, plan.guests):
                raise HTTPException(409, "Plan ID already used")
            return Response(status_code=204)
        if plan.day < datetime.now(ROME).date():
            raise HTTPException(422, "Choose today or a future date")
        if db.execute("SELECT count(*) FROM personal_plan WHERE account_id = %s", (me,)).fetchone()[0] >= 250:
            raise HTTPException(409, "Your account has reached its saved plan limit")
        row = db.execute(
            """
            INSERT INTO personal_plan (id, account_id, item_id, day, time_label, guests)
            VALUES (%s, %s, %s, %s, %s, %s) ON CONFLICT DO NOTHING RETURNING id
            """, (plan.id, me, plan.item_id, plan.day, plan.time_label, plan.guests),
        ).fetchone()
        if not row:
            raise HTTPException(409, "You already have that plan")
    return Response(status_code=204)


@router.delete("/plans/{plan_id}", status_code=204)
def cancel_plan(plan_id: UUID, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        if not db.execute(
            "UPDATE personal_plan SET cancelled = true WHERE id = %s AND account_id = %s RETURNING id",
            (plan_id, me),
        ).fetchone():
            raise HTTPException(404, "Plan unavailable")
    return Response(status_code=204)


@router.get("/pools")
def pools(request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        with db.cursor(row_factory=dict_row) as cursor:
            rows = cursor.execute(
                """
                SELECT p.id, p.title, p.location, p.description, p.day, p.time_label, p.capacity,
                       p.owner_id = %(me)s AS mine, r.status,
                       1 + (SELECT count(*) FROM pool_request a WHERE a.pool_id = p.id AND a.status = 'approved') AS joined
                FROM activity_pool p
                LEFT JOIN pool_request r ON r.pool_id = p.id AND r.account_id = %(me)s
                WHERE p.owner_id = %(me)s OR r.account_id IS NOT NULL
                   OR (p.city = %(city)s AND p.day >= (now() AT TIME ZONE 'Europe/Rome')::date)
                ORDER BY p.day, p.id LIMIT 200
                """, {"me": me, "city": account_city(db, me)},
            ).fetchall()
    return {"pools": rows}


@router.put("/pools", status_code=204)
def create_pool(pool: Pool, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        city = account_city(db, me)
        db.execute("SELECT id FROM account WHERE id = %s FOR NO KEY UPDATE", (me,))
        values = (me, city, pool.title, pool.location, pool.description, pool.day, pool.time_label, pool.capacity)
        existing = db.execute(
            "SELECT owner_id, city, title, location, description, day, time_label, capacity FROM activity_pool WHERE id = %s",
            (pool.id,),
        ).fetchone()
        if existing:
            if existing != values:
                raise HTTPException(409, "Pool ID already used")
            return Response(status_code=204)
        if pool.day < datetime.now(ROME).date():
            raise HTTPException(422, "Choose today or a future date")
        if db.execute("SELECT count(*) FROM activity_pool WHERE owner_id = %s", (me,)).fetchone()[0] >= 100:
            raise HTTPException(409, "Your account has reached its pool limit")
        if not db.execute(
            "INSERT INTO activity_pool (id, owner_id, city, title, location, description, day, time_label, capacity) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s) ON CONFLICT DO NOTHING RETURNING id",
            (pool.id, *values),
        ).fetchone():
            raise HTTPException(409, "Pool ID already used")
    return Response(status_code=204)


@router.delete("/pools/{pool_id}", status_code=204)
def remove_pool(pool_id: UUID, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        if not db.execute(
            "DELETE FROM activity_pool WHERE id = %s AND owner_id = %s RETURNING id", (pool_id, me)
        ).fetchone():
            raise HTTPException(404, "Pool unavailable")
    return Response(status_code=204)


@router.put("/pools/{pool_id}/request", status_code=204)
def join_request(pool_id: UUID, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        pool = db.execute(
            "SELECT owner_id, day FROM activity_pool WHERE id = %s AND city = %s FOR UPDATE",
            (pool_id, account_city(db, me)),
        ).fetchone()
        if not pool or pool[0] == me or pool[1] < datetime.now(ROME).date():
            raise HTTPException(404, "Pool unavailable")
        db.execute(
            "INSERT INTO pool_request (pool_id, account_id) VALUES (%s, %s) ON CONFLICT DO NOTHING",
            (pool_id, me),
        )
    return Response(status_code=204)


@router.delete("/pools/{pool_id}/request", status_code=204)
def withdraw_request(pool_id: UUID, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        db.execute("SELECT id FROM activity_pool WHERE id = %s FOR UPDATE", (pool_id,))
        db.execute("DELETE FROM pool_request WHERE pool_id = %s AND account_id = %s", (pool_id, me))
    return Response(status_code=204)


@router.get("/pool-requests")
def queue(request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        with db.cursor(row_factory=dict_row) as cursor:
            rows = cursor.execute(
                """
                SELECT r.pool_id, r.account_id, r.status, p.title, a.profile->>'name' AS name
                FROM pool_request r JOIN activity_pool p ON p.id = r.pool_id
                JOIN account_profile a ON a.account_id = r.account_id
                WHERE p.owner_id = %s AND p.day >= (now() AT TIME ZONE 'Europe/Rome')::date
                ORDER BY p.day, r.pool_id, r.account_id LIMIT 150
                """, (me,),
            ).fetchall()
    return {"requests": rows}


@router.put("/pools/{pool_id}/requests/{applicant}", status_code=204)
def review(pool_id: UUID, applicant: UUID, decision: Decision, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        pool = db.execute(
            "SELECT capacity, day FROM activity_pool WHERE id = %s AND owner_id = %s FOR UPDATE",
            (pool_id, me),
        ).fetchone()
        if not pool or pool[1] < datetime.now(ROME).date():
            raise HTTPException(404, "Pool unavailable")
        if decision.status == "approved":
            approved = db.execute(
                "SELECT count(*) FROM pool_request WHERE pool_id = %s AND status = 'approved' AND account_id <> %s",
                (pool_id, applicant),
            ).fetchone()[0]
            if approved >= pool[0] - 1:
                raise HTTPException(409, "This pool is full")
        if not db.execute(
            "UPDATE pool_request SET status = %s WHERE pool_id = %s AND account_id = %s RETURNING account_id",
            (decision.status, pool_id, applicant),
        ).fetchone():
            raise HTTPException(404, "Request unavailable")
    return Response(status_code=204)


@router.get("/clubs")
def clubs(request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        with db.cursor(row_factory=dict_row) as cursor:
            rows = cursor.execute(
                """
                SELECT catalogue.id, count(m.account_id) AS members,
                       count(m.account_id) FILTER (WHERE m.account_id = %s) > 0 AS joined
                FROM unnest(%s::text[]) AS catalogue(id)
                LEFT JOIN club_member m ON m.club_id = catalogue.id GROUP BY catalogue.id
                """, (me, list(CLUBS)),
            ).fetchall()
    return {"clubs": rows}


@router.put("/clubs/{club}/membership", status_code=204)
def join_club(club: str, request: Request):
    if club not in CLUBS:
        raise HTTPException(404, "Club unavailable")
    with connect() as db:
        me = authenticated_account(db, request)
        account_city(db, me)
        db.execute("INSERT INTO club_member VALUES (%s, %s) ON CONFLICT DO NOTHING", (club, me))
    return Response(status_code=204)


@router.delete("/clubs/{club}/membership", status_code=204)
def leave_club(club: str, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        db.execute("DELETE FROM club_member WHERE club_id = %s AND account_id = %s", (club, me))
    return Response(status_code=204)


def require_membership(db, club: str, me: UUID):
    if club not in CLUBS or not db.execute(
        "SELECT 1 FROM club_member WHERE club_id = %s AND account_id = %s FOR SHARE", (club, me)
    ).fetchone():
        raise HTTPException(403, "Join this club first")


@router.get("/clubs/{club}/members")
def club_members(club: str, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        require_membership(db, club, me)
        with db.cursor(row_factory=dict_row) as cursor:
            rows = cursor.execute(
                """
                SELECT p.account_id AS id, p.profile, (f.peer_id IS NOT NULL) AS following
                FROM club_member m JOIN account_profile p ON p.account_id = m.account_id AND p.discoverable
                LEFT JOIN account_follow f ON f.account_id = %s AND f.peer_id = m.account_id
                WHERE m.club_id = %s AND m.account_id <> %s ORDER BY m.account_id LIMIT 100
                """, (me, club, me),
            ).fetchall()
    return {"people": rows}


@router.get("/clubs/{club}/messages")
def club_messages(club: str, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        require_membership(db, club, me)
        with db.cursor(row_factory=dict_row) as cursor:
            rows = cursor.execute(
                """
                SELECT m.id, m.body, m.sent_at, m.sender_id = %s AS mine,
                       CASE WHEN p.discoverable THEN p.profile->>'name' ELSE 'Club member' END AS author
                FROM club_message m LEFT JOIN account_profile p ON p.account_id = m.sender_id
                WHERE m.club_id = %s ORDER BY m.sent_at DESC, m.id DESC LIMIT 100
                """, (me, club),
            ).fetchall()
    return {"messages": list(reversed(rows))}


@router.put("/clubs/{club}/messages", status_code=204)
def send_to_club(club: str, message: Message, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        require_membership(db, club, me)
        db.execute("SELECT id FROM account WHERE id = %s FOR NO KEY UPDATE", (me,))
        existing = db.execute(
            "SELECT club_id, body FROM club_message WHERE sender_id = %s AND id = %s", (me, message.id),
        ).fetchone()
        if existing:
            if existing != (club, message.body):
                raise HTTPException(409, "Message ID already used")
            return Response(status_code=204)
        lifetime, recent = db.execute(
            "SELECT count(*), count(*) FILTER (WHERE sent_at > now() - interval '1 minute') "
            "FROM club_message WHERE sender_id = %s", (me,),
        ).fetchone()
        if lifetime >= 10_000 or recent >= 30:
            raise HTTPException(429, "Wait before sending more messages")
        db.execute(
            "INSERT INTO club_message (club_id, sender_id, id, body) VALUES (%s, %s, %s, %s)",
            (club, me, message.id, message.body),
        )
    return Response(status_code=204)
