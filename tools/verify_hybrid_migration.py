"""Run after :app:assembleDebug. Compare migrated SQL schema with Room's actual generated schema."""
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/uz/callguard/app/data/CallGuardDatabase.kt').read_text()
generated = (root / 'app/build/generated/ksp/debug/kotlin/uz/callguard/app/data/CallGuardDatabase_Impl.kt').read_text()
expected = sqlite3.connect(':memory:')
actual = sqlite3.connect(':memory:')
creates = re.findall(r'connection.execSQL\("(CREATE (?:TABLE|UNIQUE INDEX)[^"\n]+)"\)', generated)
for sql in creates:
    expected.execute(sql)
    if not any(name in sql for name in ['spam_numbers', 'pending_spam_reports', 'sms_keywords', 'blocked_sms', 'spam_feeds']):
        actual.execute(sql)
actual.execute('CREATE TABLE spam_numbers (numberHash TEXT NOT NULL PRIMARY KEY, source TEXT NOT NULL, reportCount INTEGER NOT NULL, addedAtEpochMs INTEGER NOT NULL, phoneNumber TEXT)')
actual.execute('INSERT INTO spam_numbers VALUES (?, ?, ?, ?, ?)', ('local', 'manual', 3, 123456, '+998901234567'))
migration = source.split('val MIGRATION_4_5', 1)[1].split('@Volatile', 1)[0]
for sql in re.findall(r'db.execSQL\("([^"\n]+)"\)', migration):
    actual.execute(sql)
for table in ['spam_numbers', 'call_history', 'number_patterns', 'pending_spam_reports', 'sms_keywords', 'blocked_sms', 'spam_feeds']:
    assert actual.execute(f'PRAGMA table_info({table})').fetchall() == expected.execute(f'PRAGMA table_info({table})').fetchall(), table
assert actual.execute('SELECT phoneNumber, spamScore, isGlobal, updatedAt FROM spam_numbers WHERE numberHash = ?', ('local',)).fetchone() == ('+998901234567', 100, 0, 123456)
dao = (root / 'app/src/main/java/uz/callguard/app/data/SpamNumberDao.kt').read_text()
queries = re.findall(r'@Query\("([^"\n]+)"\)', dao)
refresh = next(q for q in queries if q.startswith('UPDATE spam_numbers SET spamScore'))
cleanup = next(q for q in queries if q.startswith('DELETE FROM spam_numbers WHERE isGlobal'))
actual.execute('INSERT INTO spam_numbers VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)', ('stale', 'global_sync', 1, 0, None, 90, 'fraud', 1, 1))
actual.execute('INSERT INTO spam_numbers VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)', ('fresh', 'global_sync', 1, 0, None, 30, 'advertising', 1, 900))
for key in ['local', 'fresh']:
    actual.execute(refresh, dict(hash=key, score=85, category='fraud', updatedAt=1000))
assert actual.execute('SELECT spamScore, isGlobal FROM spam_numbers WHERE numberHash="local"').fetchone() == (100, 0)
assert actual.execute('SELECT spamScore, updatedAt FROM spam_numbers WHERE numberHash="fresh"').fetchone() == (85, 1000)
actual.execute(cleanup, dict(timestamp=500))
assert actual.execute('SELECT numberHash FROM spam_numbers ORDER BY numberHash').fetchall() == [('fresh',), ('local',)]
assert actual.execute('PRAGMA index_list(sms_keywords)').fetchall() == expected.execute('PRAGMA index_list(sms_keywords)').fetchall()
actual.execute('INSERT OR IGNORE INTO sms_keywords(keyword, isRegex) VALUES (?, ?)', ('spam', 0))
actual.execute('INSERT OR IGNORE INTO sms_keywords(keyword, isRegex) VALUES (?, ?)', ('spam', 0))
assert actual.execute('SELECT COUNT(*) FROM sms_keywords').fetchone()[0] == 1
actual.execute('INSERT OR IGNORE INTO blocked_sms VALUES (?, ?, ?, ?, ?, ?, ?)', ('dedup', 'key', 'messenger', 'sender', 'spam', 'spam', 1000))
actual.execute('INSERT OR IGNORE INTO blocked_sms VALUES (?, ?, ?, ?, ?, ?, ?)', ('dedup', 'key', 'messenger', 'sender', 'spam', 'spam', 1000))
assert actual.execute('SELECT COUNT(*) FROM blocked_sms').fetchone()[0] == 1
assert actual.execute('SELECT id, url, isActive, lastSync FROM spam_feeds').fetchall() == [(1, 'https://1byte.uz/spam_base.json', 1, 0)]
# Fresh creation must seed the same recommended row synchronously.
callback = source.split('override fun onCreate(db:', 1)[1].split('CoroutineScope', 1)[0]
for sql in re.findall(r'db.execSQL\("([^"\n]+)"\)', callback):
    expected.execute(sql)
assert actual.execute('SELECT * FROM spam_feeds').fetchall() == expected.execute('SELECT * FROM spam_feeds').fetchall()
print('PASS: Room 4→5→6→7 schema, indexes and fresh/migrated default feed agree; old data, TTL and SMS dedup preserved.')
