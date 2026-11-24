import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { DatabaseSync } from "node:sqlite";
import { DELETE_USAGE_SQL, RESERVE_USAGE_SQL } from "../src/usage-sql.js";

const migration = readFileSync(new URL("../migrations/0001_user_provider_usage.sql", import.meta.url), "utf8");

function createDatabase() {
  const db = new DatabaseSync(":memory:");
  db.exec(migration);
  return db;
}

function reserve(db, provider, uidHash, day, characters, month = day.slice(0, 7)) {
  const result = db.prepare(RESERVE_USAGE_SQL).run(
    provider, uidHash, day, characters, month, characters, new Date().toISOString()
  );
  return Number(result.changes) === 1;
}

function row(db, provider, uidHash) {
  return db.prepare(
    "SELECT * FROM user_provider_usage WHERE provider = ? AND uid_hash = ?"
  ).get(provider, uidHash);
}

test("daily character and request ceilings reject the over-limit reservation atomically", () => {
  const db = createDatabase();
  assert.equal(reserve(db, "india", "user-a", "2026-11-24", 9999), true);
  assert.equal(reserve(db, "india", "user-a", "2026-11-24", 2), false);
  assert.equal(Number(row(db, "india", "user-a").daily_characters), 9999);

  const db2 = createDatabase();
  for (let i = 0; i < 30; i++) {
    assert.equal(reserve(db2, "vakyam", "user-b", "2026-11-24", 1), true);
  }
  assert.equal(reserve(db2, "vakyam", "user-b", "2026-11-24", 1), false);
  assert.equal(Number(row(db2, "vakyam", "user-b").daily_requests), 30);
  db.close();
  db2.close();
});

test("daily counters reset on a new UTC day while monthly characters continue", () => {
  const db = createDatabase();
  assert.equal(reserve(db, "india", "user-a", "2026-11-23", 10000), true);
  assert.equal(reserve(db, "india", "user-a", "2026-11-24", 10000), true);
  const current = row(db, "india", "user-a");
  assert.equal(Number(current.daily_characters), 10000);
  assert.equal(Number(current.daily_requests), 1);
  assert.equal(Number(current.monthly_characters), 20000);
  db.close();
});

test("monthly ceiling resets in a new month and blocks excess within the same month", () => {
  const db = createDatabase();
  db.prepare(`
    INSERT INTO user_provider_usage
      (provider, uid_hash, day, daily_characters, daily_requests, month, monthly_characters, updated_at)
    VALUES ('india', 'user-a', '2026-11-01', 0, 0, '2026-11', 99000, '2026-11-01T00:00:00.000Z')
  `).run();
  assert.equal(reserve(db, "india", "user-a", "2026-11-24", 1001), false);
  assert.equal(reserve(db, "india", "user-a", "2026-12-01", 10000), true);
  const current = row(db, "india", "user-a");
  assert.equal(current.month, "2026-12");
  assert.equal(Number(current.monthly_characters), 10000);
  db.close();
});

test("accounts and providers have independent quota rows", () => {
  const db = createDatabase();
  assert.equal(reserve(db, "india", "user-a", "2026-11-24", 10000), true);
  assert.equal(reserve(db, "india", "user-b", "2026-11-24", 10000), true);
  assert.equal(reserve(db, "vakyam", "user-a", "2026-11-24", 10000), true);
  assert.equal(reserve(db, "india", "user-a", "2026-11-24", 1), false);
  assert.equal(Number(row(db, "india", "user-b").daily_characters), 10000);
  assert.equal(Number(row(db, "vakyam", "user-a").daily_characters), 10000);
  db.close();
});

test("concurrent reservations cannot exceed the 30-request daily ceiling", async () => {
  const db = createDatabase();
  const outcomes = await Promise.all(Array.from({ length: 60 }, async () =>
    reserve(db, "india", "user-a", "2026-11-24", 1)
  ));
  assert.equal(outcomes.filter(Boolean).length, 30);
  assert.equal(outcomes.filter((value) => !value).length, 30);
  assert.equal(Number(row(db, "india", "user-a").daily_requests), 30);
  db.close();
});

test("account deletion removes only rows for the supplied UID hash", () => {
  const db = createDatabase();
  reserve(db, "india", "hash-a", "2026-11-24", 1);
  reserve(db, "vakyam", "hash-a", "2026-11-24", 1);
  reserve(db, "india", "hash-b", "2026-11-24", 1);
  const result = db.prepare(DELETE_USAGE_SQL).run("hash-a");
  assert.equal(Number(result.changes), 2);
  assert.equal(row(db, "india", "hash-a"), undefined);
  assert.equal(row(db, "vakyam", "hash-a"), undefined);
  assert.ok(row(db, "india", "hash-b"));
  db.close();
});
