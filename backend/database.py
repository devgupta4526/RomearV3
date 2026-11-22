import os

import psycopg


def connect():
    return psycopg.connect(
        os.environ["DATABASE_URL"],
        connect_timeout=8,
        client_encoding="UTF8",
        options="-c statement_timeout=8000",
    )
