"""Run against disposable local PostgreSQL with ROMEAR_TEST_DATABASE_URL set."""
import json
import base64
import os
import secrets
import socket
import threading
import time
import unittest
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta
from io import BytesIO
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen
from uuid import uuid4

import uvicorn
from PIL import Image

from database import connect
from main import app


class CommunityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        database_url = os.getenv("ROMEAR_TEST_DATABASE_URL", "")
        if urlsplit(database_url).hostname not in ("127.0.0.1", "localhost", "::1"):
            raise unittest.SkipTest("Set ROMEAR_TEST_DATABASE_URL to disposable local PostgreSQL")
        os.environ["DATABASE_URL"] = database_url
        os.environ["ENABLE_DEVICE_ACCOUNTS"] = "true"
        with socket.socket() as socket_probe:
            socket_probe.bind(("127.0.0.1", 0))
            cls.port = socket_probe.getsockname()[1]
        cls.server = uvicorn.Server(uvicorn.Config(app, host="127.0.0.1", port=cls.port, log_level="error"))
        cls.worker = threading.Thread(target=cls.server.run, daemon=True)
        cls.worker.start()
        for _ in range(100):
            if cls.server.started:
                break
            time.sleep(0.1)
        if not cls.server.started:
            raise RuntimeError("Local API did not start")

    @classmethod
    def tearDownClass(cls):
        cls.server.should_exit = True
        cls.worker.join(10)

    def setUp(self):
        self.accounts = []
        self.day = (date.today() + timedelta(days=2)).isoformat()

    def tearDown(self):
        with connect() as db:
            for account in self.accounts:
                db.execute("DELETE FROM account WHERE id = %s", (account,))

    def call(self, method, path, token=None, data=None):
        headers = {"Content-Type": "application/json"} if data is not None else {}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = Request(
            f"http://127.0.0.1:{self.port}/v1/{path}",
            json.dumps(data).encode() if data is not None else None,
            headers, method=method,
        )
        try:
            with urlopen(request, timeout=15) as response:
                return response.status, json.load(response) if response.status != 204 else None
        except HTTPError as response:
            with response:
                return response.status, json.load(response)

    def account(self, discoverable=True):
        key = secrets.token_hex(32)
        code, session = self.call("POST", "auth/device", key)
        self.assertEqual(code, 200)
        self.accounts.append(session["account_id"])
        self.assertEqual(self.call("PUT", "auth/profile", session["token"], {
            "name": "Integration Test", "username": "qa_" + secrets.token_hex(5),
            "bio": "", "city": "Rome", "interests": ["Music"], "discoverable": discoverable,
        })[0], 204)
        return key, session["account_id"], session["token"]

    def test_device_identity_private_plans_and_public_visibility(self):
        key, me, token = self.account()
        _, invisible, stranger = self.account(discoverable=False)
        _, public, _ = self.account()
        _, rows = self.call("GET", "community/people", token)
        ids = {row["id"] for row in rows["people"]}
        self.assertIn(public, ids)
        self.assertNotIn(invisible, ids)
        self.assertEqual(self.call("GET", f"community/people/{public}", token)[1]["person"]["id"], public)
        self.assertEqual(self.call("GET", f"community/people/{invisible}", token)[0], 404)
        self.assertNotIn(me, ids)
        self.assertEqual(self.call("PUT", f"community/following/{public}", token)[0], 204)
        self.assertTrue(next(p["following"] for p in self.call("GET", "community/people", token)[1]["people"] if p["id"] == public))
        plan = {"id": str(uuid4()), "item_id": "riverside", "day": self.day, "time_label": "1:30 PM", "guests": 2}
        self.assertEqual(self.call("PUT", "community/plans", token, plan)[0], 204)
        self.assertEqual(self.call("PUT", "community/plans", token, plan)[0], 204)
        self.assertEqual(self.call("PUT", "community/plans", token, dict(plan, id=str(uuid4())))[0], 409)
        self.assertEqual(self.call("DELETE", f"community/plans/{plan['id']}", stranger)[0], 404)
        self.assertEqual(self.call("GET", "community/plans", stranger), (200, {"plans": []}))
        _, restored = self.call("POST", "auth/device", key)
        self.assertEqual(restored["account_id"], me)
        self.assertEqual(restored["profile"]["name"], "Integration Test")
        self.assertEqual(self.call("GET", "community/plans", token)[0], 401)
        token = restored["token"]
        self.assertEqual(self.call("DELETE", f"community/plans/{plan['id']}", token)[0], 204)
        self.assertTrue(self.call("GET", "community/plans", token)[1]["plans"][0]["cancelled"])

    def test_direct_messages_are_private_and_retries_are_idempotent(self):
        _, sender, first = self.account()
        _, peer, second = self.account()
        _, private, third = self.account(discoverable=False)
        data = {"id": str(uuid4()), "body": "Coffee tomorrow?"}
        self.assertEqual(self.call("PUT", f"community/messages/{private}", first, data)[0], 404)
        self.assertEqual(self.call("PUT", f"community/messages/{peer}", first, dict(data, body="a" * 17_000))[0], 413)
        self.assertEqual(self.call("PUT", f"community/messages/{peer}", first, data)[0], 204)
        self.assertEqual(self.call("PUT", f"community/messages/{peer}", first, data)[0], 204)
        self.assertEqual(self.call("PUT", f"community/messages/{peer}", first, dict(data, body="Changed"))[0], 409)
        history = self.call("GET", f"community/messages/{sender}", second)[1]["messages"]
        self.assertEqual(len(history), 1)
        self.assertFalse(history[0]["mine"])
        self.assertEqual(history[0]["body"], data["body"])
        self.assertEqual(self.call("GET", f"community/messages/{sender}", third)[1]["messages"], [])
        self.assertEqual(self.call("GET", "community/inbox", second)[1]["conversations"][0]["peer"], sender)

    def test_pool_approvals_serialize_against_capacity(self):
        _, _, host = self.account()
        _, first, first_token = self.account()
        _, second, second_token = self.account()
        pool = {"id": str(uuid4()), "title": "Riverside walk", "location": "The park",
                "description": "A relaxed walk along the Tiber.", "day": self.day, "time_label": "4:00 PM", "capacity": 2}
        self.assertEqual(self.call("PUT", "community/pools", host, pool)[0], 204)
        self.assertEqual(self.call("PUT", "community/pools", host, pool)[0], 204)
        root = f"community/pools/{pool['id']}"
        for token in (first_token, second_token):
            self.assertEqual(self.call("PUT", root + "/request", token)[0], 204)
        self.assertEqual(len(self.call("GET", "community/pool-requests", host)[1]["requests"]), 2)
        self.assertEqual(self.call("PUT", f"{root}/requests/{second}", first_token, {"status": "approved"})[0], 404)
        with ThreadPoolExecutor(max_workers=2) as workers:
            results = list(workers.map(lambda applicant: self.call(
                "PUT", f"{root}/requests/{applicant}", host, {"status": "approved"}
            )[0], (first, second)))
        self.assertEqual(sorted(results), [204, 409])
        self.assertEqual(self.call("DELETE", root, first_token)[0], 404)
        self.assertEqual(self.call("DELETE", root, host)[0], 204)
        self.assertEqual(self.call("GET", "community/pool-requests", host), (200, {"requests": []}))

    def test_group_chat_requires_current_club_membership(self):
        _, first, owner = self.account()
        _, _, member = self.account()
        _, _, outsider = self.account()
        root = "community/clubs/runners"
        self.assertEqual(self.call("GET", root + "/messages", outsider)[0], 403)
        for token in (owner, member):
            self.assertEqual(self.call("PUT", root + "/membership", token)[0], 204)
        self.assertEqual(self.call("GET", root + "/members", member)[1]["people"][0]["id"], first)
        data = {"id": str(uuid4()), "body": "See you at the run"}
        self.assertEqual(self.call("PUT", root + "/messages", owner, data)[0], 204)
        self.assertEqual(self.call("PUT", root + "/messages", owner, data)[0], 204)
        self.assertEqual(self.call("PUT", root + "/messages", outsider, data)[0], 403)
        history = self.call("GET", root + "/messages", member)[1]["messages"]
        self.assertEqual([row["body"] for row in history], [data["body"]])
        self.assertEqual(self.call("DELETE", root + "/membership", member)[0], 204)
        self.assertEqual(self.call("GET", root + "/messages", member)[0], 403)

    def test_post_photo_comments_visibility_and_deletion(self):
        _, author_id, author = self.account()
        _, _, reader = self.account()
        root = "community/posts"
        post_id = str(uuid4())
        with BytesIO() as image:
            Image.new("RGB", (4, 4), "white").save(image, format="JPEG")
            photo = base64.b64encode(image.getvalue()).decode("ascii")
        payload = {"id": post_id, "body": "A photo from the park", "category": "Culture", "photo": photo}
        self.assertEqual(self.call("PUT", root, author, dict(payload, photo="invalid!"))[0], 422)
        self.assertEqual(self.call("PUT", root, author, payload)[0], 204)
        self.assertEqual(self.call("PUT", root, author, payload)[0], 204)
        path = f"{root}/{post_id}"
        self.assertEqual(self.call("GET", path + "/photo")[0], 401)
        self.assertEqual(self.call("GET", path + "/photo", reader), (200, {"jpeg": photo}))
        self.assertEqual(self.call("GET", root + f"?author={author_id}", reader)[1]["posts"][0]["id"], post_id)
        self.assertEqual(self.call("PUT", path + "/reaction/save", reader)[0], 204)
        self.assertEqual(self.call("GET", root + "?view=saved", author)[1]["posts"], [])
        self.assertEqual(self.call("GET", root + "?view=saved", reader)[1]["posts"][0]["id"], post_id)
        self.assertEqual(self.call("PUT", path + "/reaction/like", reader)[0], 204)
        comment = {"id": str(uuid4()), "body": "Great photo"}
        self.assertEqual(self.call("PUT", path + "/comments", reader, comment)[0], 204)
        self.assertEqual(self.call("PUT", path + "/comments", reader, comment)[0], 204)
        result = self.call("GET", path, author)[1]
        self.assertEqual(result["post"]["likes"], 1)
        self.assertEqual(len(result["comments"]), 1)
        self.assertEqual(self.call("DELETE", path, reader)[0], 404)
        self.assertEqual(self.call("DELETE", "auth/account", author)[0], 204)
        self.assertEqual(self.call("GET", "auth/session", author)[0], 401)
        self.assertEqual(self.call("GET", path, reader)[0], 404)

    def test_account_library_keeps_stamps_and_drafts_private(self):
        _, _, first = self.account()
        _, _, other = self.account()
        root = "community/library"
        item = f"{root}/events/{uuid4()}"
        event = {"title": "Evening walk", "description": "Notes for an unpublished event", "location": "Rome", "date": self.day}
        self.assertEqual(self.call("PUT", item, first, event)[0], 204)
        self.assertEqual(self.call("PUT", item, first, dict(event, title="Evening plan"))[0], 204)
        self.assertEqual(self.call("PUT", root + "/stamps/Rome", first)[0], 204)
        self.assertEqual(self.call("DELETE", item, other)[0], 204)
        self.assertEqual(self.call("GET", root, other)[1], {"stamps": [], "events": [], "offers": []})
        saved = self.call("GET", root, first)[1]
        self.assertEqual(saved["events"][0]["title"], "Evening plan")
        self.assertEqual(saved["stamps"], ["Rome"])
        self.assertEqual(self.call("DELETE", item, first)[0], 204)
        self.assertEqual(self.call("GET", root, first)[1]["events"], [])
