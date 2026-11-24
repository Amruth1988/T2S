import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { DatabaseSync } from "node:sqlite";
import { DELETE_USAGE_SQL, RESERVE_USAGE_SQL, RESERVE_SARVAM_USER_SQL } from "../src/usage-sql.js";

const migration = readFileSync(new URL("../migrations/0001_user_provider_usage.sql", import.meta.url), "utf8");
const migration2 = readFileSync(new URL("../migrations/0002_provider_control_plane.sql", import.meta.url), "utf8");

function createDatabase() {
  const db = new DatabaseSync(":memory:");
  db.exec(migration);
  db.exec(migration2);
  return db;
}

function reserve(db, provider, uidHash, day, characters, month = day.slice(0, 7)) {
  const result = db.prepare(RESERVE_USAGE_SQL).run(
    provider, uidHash, day, characters, month, characters, new Date().toISOString()
  );
  return Number(result.changes) === 1;
}

function row(db, provider, uidHash) {
  return db.prepare("SELECT * FROM user_provider_usage WHERE provider = ? AND uid_hash = ?").get(provider, uidHash);
}

test("existing per-account quota still atomically enforces daily and monthly ceilings", () => {
  const db = createDatabase();
  assert.equal(reserve(db, "india", "user-a", "2026-11-24", 9999), true);
  assert.equal(reserve(db, "india", "user-a", "2026-11-24", 2), false);
  assert.equal(Number(row(db, "india", "user-a").daily_characters), 9999);
  db.close();
});

test("Sarvam per-account reservation caps at 10 calls and 10,000 characters per UTC day", () => {
  const db = createDatabase();
  const statement = db.prepare(RESERVE_SARVAM_USER_SQL);
  for (let i = 0; i < 10; i++) {
    assert.equal(Number(statement.run("uid-a", "2026-11-24", 0, new Date().toISOString()).changes), 1);
  }
  assert.equal(Number(statement.run("uid-a", "2026-11-24", 1, new Date().toISOString()).changes), 0);
  assert.equal(Number(statement.run("uid-b", "2026-11-24", 10000, new Date().toISOString()).changes), 1);
  assert.equal(Number(statement.run("uid-b", "2026-11-24", 1, new Date().toISOString()).changes), 0);
  db.close();
});

test("provider limits cannot be enabled with zero ceilings", () => {
  const db = createDatabase();
  assert.throws(() => db.prepare(`
    INSERT INTO provider_limits(provider, enabled, daily_characters, monthly_characters, daily_requests, updated_by, updated_at)
    VALUES ('sarvam', 1, 0, 1000, 10, 'admin', 'now')
  `).run());
  db.close();
});

test("provider keys store ciphertext and metadata only, with no plaintext column", () => {
  const db = createDatabase();
  const columns = db.prepare("PRAGMA table_info(provider_credentials)").all().map((column) => column.name);
  assert.ok(columns.includes("encrypted_key"));
  assert.ok(!columns.includes("api_key"));
  assert.ok(!columns.includes("plaintext_key"));
  db.close();
});

test("account deletion clears only the matching hashed UID quota records", () => {
  const db = createDatabase();
  reserve(db, "india", "hash-a", "2026-11-24", 1);
  reserve(db, "india", "hash-b", "2026-11-24", 1);
  db.prepare("INSERT INTO sarvam_user_usage(uid_hash, day, calls, characters, updated_at) VALUES ('hash-a', '2026-11-24', 1, 1, 'now')").run();
  db.prepare("INSERT INTO sarvam_user_usage(uid_hash, day, calls, characters, updated_at) VALUES ('hash-b', '2026-11-24', 1, 1, 'now')").run();
  db.prepare(DELETE_USAGE_SQL).run("hash-a");
  db.prepare("DELETE FROM sarvam_user_usage WHERE uid_hash = ?").run("hash-a");
  assert.equal(row(db, "india", "hash-a"), undefined);
  assert.equal(db.prepare("SELECT * FROM sarvam_user_usage WHERE uid_hash = 'hash-a'").get(), undefined);
  assert.ok(row(db, "india", "hash-b"));
  assert.ok(db.prepare("SELECT * FROM sarvam_user_usage WHERE uid_hash = 'hash-b'").get());
  db.close();
});
