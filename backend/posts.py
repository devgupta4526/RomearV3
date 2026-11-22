import base64
import binascii
from io import BytesIO
from typing import Literal
from uuid import UUID

from fastapi import APIRouter, HTTPException, Request, Response
from PIL import Image, UnidentifiedImageError
from psycopg.rows import dict_row
from pydantic import Field

from accounts import authenticated_account
from community import Input
from database import connect

router = APIRouter(prefix="/v1/community/posts")


class Post(Input):
    id: UUID
    body: str = Field(min_length=1, max_length=2000)
    category: Literal["Running", "Cafés", "Culture", "Travel"]
    sample: Literal["run", "portrait", "market", "cafe"] | None = None
    photo: str | None = Field(default=None, max_length=349528)


class Comment(Input):
    id: UUID
    body: str = Field(min_length=1, max_length=1000)


def validate_photo(encoded: str) -> bytes:
    try:
        photo = base64.b64decode(encoded, validate=True)
        if len(photo) > 262144:
            raise ValueError
        with Image.open(BytesIO(photo), formats=["JPEG"]) as image:
            if not 1 <= image.width <= 1920 or not 1 <= image.height <= 1920:
                raise ValueError
            image.load()
        return photo
    except (ValueError, binascii.Error, UnidentifiedImageError, Image.DecompressionBombError, OSError) as exc:
        raise HTTPException(422, "Use a valid JPEG photo up to 256 KiB") from exc


PROJECTION = """
SELECT p.id, p.owner_id, p.body, p.category, p.sample, p.created_at, p.photo IS NOT NULL AS has_photo,
       p.owner_id = %(me)s AS mine, a.profile->>'name' AS author, a.profile->>'city' AS city,
       EXISTS (SELECT 1 FROM account_follow f WHERE f.account_id = %(me)s AND f.peer_id = p.owner_id) AS following,
       EXISTS (SELECT 1 FROM post_reaction r WHERE r.post_id = p.id AND r.account_id = %(me)s AND r.kind = 'save') AS saved,
       EXISTS (SELECT 1 FROM post_reaction r WHERE r.post_id = p.id AND r.account_id = %(me)s AND r.kind = 'like') AS liked,
       (SELECT count(*) FROM post_reaction r WHERE r.post_id = p.id AND r.kind = 'like') AS likes,
       (SELECT count(*) FROM post_comment c WHERE c.post_id = p.id) AS comments
FROM community_post p JOIN account_profile a ON a.account_id = p.owner_id
WHERE (a.discoverable OR p.owner_id = %(me)s)
"""


def require_post(db, post_id, me):
    if not db.execute(
        "SELECT 1 FROM community_post p JOIN account_profile a ON a.account_id = p.owner_id "
        "WHERE p.id = %s AND (a.discoverable OR p.owner_id = %s) FOR SHARE OF p",
        (post_id, me),
    ).fetchone():
        raise HTTPException(404, "Post unavailable")


@router.get("")
def feed(request: Request, view: Literal["all", "saved", "following"] = "all", author: UUID | None = None):
    with connect() as db:
        me = authenticated_account(db, request)
        with db.cursor(row_factory=dict_row) as cursor:
            rows = cursor.execute(
                "SELECT * FROM (" + PROJECTION + ") f "
                "WHERE (%(author)s::uuid IS NULL OR f.owner_id = %(author)s) "
                "AND (%(view)s = 'all' OR (%(view)s = 'saved' AND saved) OR "
                "(%(view)s = 'following' AND (following OR mine))) "
                "ORDER BY f.created_at DESC, f.id DESC LIMIT 80",
                {"me": me, "view": view, "author": author},
            ).fetchall()
    return {"posts": rows}


@router.put("", status_code=204)
def publish(data: Post, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        if not db.execute("SELECT 1 FROM account_profile WHERE account_id = %s AND discoverable", (me,)).fetchone():
            raise HTTPException(403, "Publish your profile first")
        photo = None
        if data.photo:
            if data.sample:
                raise HTTPException(422, "Choose only one photo")
            photo = validate_photo(data.photo)
        db.execute("SELECT id FROM account WHERE id = %s FOR NO KEY UPDATE", (me,))
        old = db.execute("SELECT owner_id, body, category, photo, sample FROM community_post WHERE id = %s", (data.id,)).fetchone()
        fields = (me, data.body, data.category, photo, data.sample)
        if old:
            if old != fields:
                raise HTTPException(409, "Post ID already used")
            return Response(status_code=204)
        counts = db.execute(
            "SELECT count(*), count(photo) FROM community_post WHERE owner_id = %s", (me,)
        ).fetchone()
        if counts[0] >= 100 or (photo is not None and counts[1] >= 10):
            raise HTTPException(409, "Your post storage limit has been reached")
        if not db.execute(
            "INSERT INTO community_post (id, owner_id, body, category, photo, sample) "
            "VALUES (%s, %s, %s, %s, %s, %s) ON CONFLICT DO NOTHING RETURNING id",
            (data.id, *fields),
        ).fetchone():
            raise HTTPException(409, "Post ID already used")
    return Response(status_code=204)


@router.get("/{post_id}")
def post(post_id: UUID, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        with db.cursor(row_factory=dict_row) as cursor:
            row = cursor.execute(PROJECTION + " AND p.id = %(id)s", {"me": me, "id": post_id}).fetchone()
            if not row:
                raise HTTPException(404, "Post unavailable")
            comments = cursor.execute(
                "SELECT c.body, a.profile->>'name' AS author FROM post_comment c "
                "JOIN account_profile a ON a.account_id = c.account_id WHERE c.post_id = %s "
                "AND (a.discoverable OR c.account_id = %s) ORDER BY c.created_at DESC, c.id DESC LIMIT 100",
                (post_id, me),
            ).fetchall()
    return {"post": row, "comments": list(reversed(comments))}


@router.get("/{post_id}/photo")
def photo(post_id: UUID, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        require_post(db, post_id, me)
        result = db.execute("SELECT photo FROM community_post WHERE id = %s", (post_id,)).fetchone()[0]
        if result is None:
            raise HTTPException(404, "Photo unavailable")
    return {"jpeg": base64.b64encode(result).decode("ascii")}


@router.delete("/{post_id}", status_code=204)
def remove(post_id: UUID, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        if not db.execute("DELETE FROM community_post WHERE id = %s AND owner_id = %s RETURNING id", (post_id, me)).fetchone():
            raise HTTPException(404, "Post unavailable")
    return Response(status_code=204)


@router.put("/{post_id}/comments", status_code=204)
def comment(post_id: UUID, data: Comment, request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        require_post(db, post_id, me)
        db.execute("SELECT id FROM account WHERE id = %s FOR NO KEY UPDATE", (me,))
        previous = db.execute("SELECT body FROM post_comment WHERE post_id = %s AND account_id = %s AND id = %s",
                              (post_id, me, data.id)).fetchone()
        if previous:
            if previous[0] != data.body:
                raise HTTPException(409, "Comment ID already used")
            return Response(status_code=204)
        if not db.execute("SELECT 1 FROM account_profile WHERE account_id = %s AND discoverable", (me,)).fetchone():
            raise HTTPException(403, "Publish your profile first")
        if db.execute("SELECT count(*) FROM post_comment WHERE account_id = %s", (me,)).fetchone()[0] >= 1000:
            raise HTTPException(409, "Your comment limit has been reached")
        db.execute("INSERT INTO post_comment (post_id, account_id, id, body) VALUES (%s, %s, %s, %s)",
                   (post_id, me, data.id, data.body))
    return Response(status_code=204)


@router.api_route("/{post_id}/reaction/{kind}", methods=["PUT", "DELETE"], status_code=204)
def react(post_id: UUID, kind: Literal["like", "save"], request: Request):
    with connect() as db:
        me = authenticated_account(db, request)
        if request.method == "PUT":
            require_post(db, post_id, me)
            db.execute("INSERT INTO post_reaction VALUES (%s, %s, %s) ON CONFLICT DO NOTHING", (post_id, me, kind))
        else:
            db.execute("DELETE FROM post_reaction WHERE post_id = %s AND account_id = %s AND kind = %s", (post_id, me, kind))
    return Response(status_code=204)
