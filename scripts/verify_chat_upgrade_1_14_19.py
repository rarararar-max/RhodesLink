"""Exercise actual display-event SQL in an isolated legacy database; never opens user data."""
from pathlib import Path
import json
import re
import sqlite3
import sys

root = Path(__file__).resolve().parents[1]
baseline = Path(sys.argv[1]).resolve()
sql_path = Path("shared/src/commonMain/sqldelight/com/rhodes/privatechat/shared/db")
checks = []


def ddl(path):
    return re.split(r"^\w+:\s*$", path.read_text(encoding="utf-8-sig"), maxsplit=1, flags=re.M)[0]


def query(name):
    text = (root / sql_path / "ChatDisplayEvents.sq").read_text(encoding="utf-8-sig")
    return re.search(r"^" + name + r":\s*\n(.*?);", text, re.M | re.S)[1]


old = baseline / sql_path
assert old.is_dir() and list(old.glob("*.sq")), "A populated baseline is required"
assert {p.name for p in old.glob("*.sq")} == {p.name for p in (root / sql_path).glob("*.sq")}
for path in old.glob("*.sq"):
    assert ddl(path) == ddl(root / sql_path / path.name), path.name
assert {p.name: p.read_bytes() for p in old.glob("*.sqm")} == {
    p.name: p.read_bytes() for p in (root / sql_path).glob("*.sqm")
}
checks.append("Existing tables, indexes and migration files unchanged")

db = sqlite3.connect(":memory:")
for path in old.glob("*.sq"):
    db.executescript(ddl(path))
db.execute(query("insertDisplayEventIfAbsent"), (10, 0, "private", 1))
db.execute(query("insertDisplayEventIfAbsent"), (10, 1, "private", 2))
legacy = db.execute(query("getDisplayEvents"), ("private",)).fetchall()
now = 1_790_000_000_000
db.execute(query("insertDisplayEventIfAbsent"), (20, 0, "private", now))
db.execute(query("insertDisplayEventIfAbsent"), (20, 1, "private", now + 200))
db.execute(query("insertDisplayEventIfAbsent"), (30, 0, "group", now + 300))
assert db.execute(query("getDisplayEvents"), ("private",)).fetchall()[:2] == legacy
checks.append("Legacy sequence events and new timestamp events coexist without migration")
db.execute(query("insertDisplayEventIfAbsent"), (20, 1, "private", now + 5000))
assert db.execute(query("getDisplayEventOrder"), (20, 1)).fetchone() == (now + 200,)
checks.append("Repeated reveal writes preserve the first persisted reveal time")
assert db.execute(query("getDisplayEvents"), ("group",)).fetchall() == [(30, 0, now + 300)]
checks.append("Private and group display histories remain isolated")
backup = db.execute(query("getAllDisplayEvents")).fetchall()
restored = sqlite3.connect(":memory:")
restored.executescript(ddl(old / "ChatDisplayEvents.sq"))
for message_id, segment, session, reveal in backup:
    restored.execute(query("insertDisplayEventIfAbsent"), (message_id, segment, session, reveal))
assert restored.execute(query("getAllDisplayEvents")).fetchall() == backup
checks.append("Existing backup SQL round-trips both order formats without precision loss")
db.execute(query("deleteDisplayEvent"), (20, 0))
assert db.execute(query("getDisplayEventOrder"), (20, 1)).fetchone() == (now + 200,)
checks.append("Removing a bubble leaves the other segment reveal times intact")

report = {"checks": checks, "count": len(checks), "uses_real_user_database": False}
(root / "docs/chat-upgrade-verification-1.14.19.json").write_text(
    json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
)
print(json.dumps(report, ensure_ascii=False, indent=2))
