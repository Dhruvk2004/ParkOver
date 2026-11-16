// Phase 5 load tests on the Firestore emulator (real rules, the app's own booking writes from
// ../booking.js). Measures latency and checks the invariants that matter under load:
// no bay double-booked, free counts never negative or above capacity, counts == capacity -
// active bookings, every active booking holds its locks and no orphan locks remain.
//
//   npm run load                      (all scenarios)
//   SCENARIO=rush npm run load        (one: rush | sustained | fanout | storm)
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDocs, collection, onSnapshot, Timestamp } from 'firebase/firestore';
import { createBooking, cancelBooking, extendBooking, futureStart, lockBuckets, lockId, occupancyId, bucketOf, BUCKET_MS, HOUR, ACTIVE } from '../booking.js';

const env = await initializeTestEnvironment({
  projectId: 'demo-parkover-load',
  firestore: { rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: 8085 },
});
const ctx = new Map();
const db = (uid) => { if (!ctx.has(uid)) ctx.set(uid, env.authenticatedContext(uid).firestore()); return ctx.get(uid); };
const admin = async (fn) => { let out; await env.withSecurityRulesDisabled(async (c) => { out = await fn(c.firestore()); }); return out; };

// ---- helpers ----
const pct = (xs, p) => { if (!xs.length) return 0; const s = [...xs].sort((a, b) => a - b); return s[Math.min(s.length - 1, Math.floor((p / 100) * s.length))]; };
const stats = (xs) => ({ n: xs.length, p50: pct(xs, 50), p95: pct(xs, 95), p99: pct(xs, 99), max: xs.length ? Math.max(...xs) : 0 });
const timed = async (fn) => { const t = performance.now(); try { const v = await fn(); return { ok: true, ms: performance.now() - t, v }; } catch (e) { return { ok: false, ms: performance.now() - t, e }; } };
const kind = (e) => /just booked|taken/i.test(e?.message) ? 'conflict' : /full/i.test(e?.message) ? 'full' : /closed|Only active/i.test(e?.message) ? 'closed' : 'error';
const rnd = (n) => Math.floor(Math.random() * n);
const bay = (i) => `L1-${String(i).padStart(2, '0')}`;

/**
 * Invariants, read as admin at the end of a scenario. capacity: { parkingId: car bays }.
 * For every occupancy slot: count == active bookings covering it, never < 0, never > capacity.
 */
async function checkInvariants(capacity) {
  const [occ, bookings, locks] = await admin(async (fs) => Promise.all(
    ['occupancy', 'bookings', 'slot_locks'].map(async (c) => (await getDocs(collection(fs, c))).docs.map((d) => ({ _id: d.id, ...d.data() })))));
  const problems = [];
  const active = bookings.filter((b) => ACTIVE.includes(b.bookingStatus));
  const expected = new Map();
  for (const b of active) for (const bucket of lockBuckets(b.entryTime.toMillis(), b.exitTime.toMillis())) {
    const k = occupancyId(b.parkingId, bucket); expected.set(k, (expected.get(k) ?? 0) + 1);
  }
  for (const o of occ) {
    const n = o.FOUR_WHEELER ?? 0, want = expected.get(o._id) ?? 0, cap = capacity[o.parkingId];
    if (n < 0) problems.push(`${o._id}: negative ${n}`);
    if (cap != null && n > cap) problems.push(`${o._id}: ${n} above capacity ${cap}`);
    if (n !== want) problems.push(`${o._id}: count ${n} but ${want} active bookings`);
    if ((o.floors?.['1'] ?? 0) !== n) problems.push(`${o._id}: floor count ${o.floors?.['1']} != ${n}`);
  }
  for (const [k, want] of expected) if (!occ.some((o) => o._id === k)) problems.push(`${k}: ${want} bookings but no occupancy doc`);
  // No two active bookings overlap on the same bay.
  const byBay = new Map();
  for (const b of active) { const k = `${b.parkingId}/${b.spotNumber}`; (byBay.get(k) ?? byBay.set(k, []).get(k)).push(b); }
  for (const [k, list] of byBay) for (let i = 0; i < list.length; i++) for (let j = i + 1; j < list.length; j++) {
    const x = list[i], y = list[j];
    if (x.entryTime.toMillis() < y.exitTime.toMillis() && y.entryTime.toMillis() < x.exitTime.toMillis()) problems.push(`${k}: ${x.id} and ${y.id} overlap`);
  }
  // Every active booking holds all its locks; every lock belongs to an active booking.
  const lockIds = new Set(locks.map((l) => l._id));
  for (const b of active) for (const bucket of lockBuckets(b.entryTime.toMillis(), b.exitTime.toMillis()))
    if (!lockIds.has(lockId(b.parkingId, b.spotNumber, bucket))) problems.push(`${b.id}: missing lock ${bucket}`);
  const activeIds = new Set(active.map((b) => b.id));
  for (const l of locks) if (!activeIds.has(l.bookingId)) problems.push(`orphan lock ${l._id} (booking ${l.bookingId})`);
  return { problems, active: active.length, bookings: bookings.length, locks: locks.length, slots: occ.length };
}

// ---- scenarios ----
const scenarios = {
  // LOAD-01: 50 drivers hit the last 2 bays at the same instant (half on the same bay).
  async rush() {
    const start = futureStart();
    const cap = { type: 2, floor: 2 };
    const runs = await Promise.all(Array.from({ length: 50 }, (_, i) =>
      timed(() => createBooking(db(`rush-${i}`), `rush-${i}`, { parkingId: 'rush', spot: i % 2 ? bay(1) : bay(1 + (i % 5)), start, capacity: cap }))));
    const ok = runs.filter((r) => r.ok).length;
    const outcomes = runs.reduce((m, r) => (m[r.ok ? 'ok' : kind(r.e)] = (m[r.ok ? 'ok' : kind(r.e)] ?? 0) + 1, m), {});
    const errorSamples = [...new Set(runs.filter((r) => !r.ok && kind(r.e) === 'error').map((r) => `${r.e.code ?? ''} ${r.e.message}`.slice(0, 160)))].slice(0, 3);
    return { drivers: 50, errorSamples, bays: 2, outcomes, latencyMs: stats(runs.map((r) => r.ms)), expect: 'ok == 2', pass: ok === 2 && (await checkInvariants({ rush: 2 })).problems.length === 0, ...(await checkInvariants({ rush: 2 })) };
  },

  // LOAD-02: 100 drivers, 20 parkings × 10 bays, 45 s of book / cancel / extend churn.
  async sustained() {
    const P = 20, CAP = 10, D = 100, SECONDS = Number(process.env.SECONDS ?? 45);
    const capacity = Object.fromEntries(Array.from({ length: P }, (_, p) => [`p${p}`, CAP]));
    const cap = { type: CAP, floor: CAP };
    const lat = { create: [], cancel: [], extend: [] }, outcomes = {};
    const count = (k) => (outcomes[k] = (outcomes[k] ?? 0) + 1);
    const until = Date.now() + SECONDS * 1000;
    await Promise.all(Array.from({ length: D }, async (_, d) => {
      const uid = `driver-${d}`, fs = db(uid);
      while (Date.now() < until) {
        const start = futureStart(2 + rnd(48)); // within the next day, on a half hour
        const r = await timed(() => createBooking(fs, uid, { parkingId: `p${rnd(P)}`, spot: bay(1 + rnd(12)), start, hours: 1 + rnd(3), capacity: cap }));
        lat.create.push(r.ms); count(r.ok ? 'create ok' : `create ${kind(r.e)}`);
        if (!r.ok) continue;
        if (Math.random() < 0.2) { const x = await timed(() => extendBooking(fs, uid, r.v, 1, cap)); lat.extend.push(x.ms); count(x.ok ? 'extend ok' : `extend ${kind(x.e)}`); if (x.ok) r.v.exitTime = Timestamp.fromMillis(r.v.exitTime.toMillis() + HOUR); }
        if (Math.random() < 0.4) { const c = await timed(() => cancelBooking(fs, uid, r.v)); lat.cancel.push(c.ms); count(c.ok ? 'cancel ok' : `cancel ${kind(c.e)}`); }
      }
    }));
    const inv = await checkInvariants(capacity);
    const ops = lat.create.length + lat.cancel.length + lat.extend.length;
    return { drivers: D, parkings: P, seconds: SECONDS, ops, opsPerSec: +(ops / SECONDS).toFixed(1), outcomes,
      latencyMs: { create: stats(lat.create), cancel: stats(lat.cancel), extend: stats(lat.extend) }, pass: inv.problems.length === 0, ...inv };
  },

  // LOAD-03: 300 phones watch one parking; one booking; how fast does everyone see it?
  async fanout() {
    const L = 300;
    const slot = bucketOf(Date.now());
    const seen = [];
    let t0 = 0;
    const subs = await Promise.all(Array.from({ length: L }, (_, i) => new Promise((ready) => {
      let first = true;
      const unsub = onSnapshot(doc(db(`viewer-${i}`), 'occupancy', occupancyId('busy', slot)), (s) => {
        if (first) { first = false; ready(unsub); return; }
        if (s.exists() && s.get('FOUR_WHEELER') === 1 && t0) seen.push(performance.now() - t0);
      });
    })));
    t0 = performance.now();
    await createBooking(db('booker'), 'booker', { parkingId: 'busy', spot: bay(1), start: slot * BUCKET_MS, hours: 1 }); // parked now
    const deadline = Date.now() + 20_000;
    while (seen.length < L && Date.now() < deadline) await new Promise((r) => setTimeout(r, 50));
    subs.forEach((u) => u());
    return { listeners: L, received: seen.length, latencyMs: stats(seen), pass: seen.length === L && pct(seen, 95) < 3000 };
  },

  // LOAD-04: every booking gets 3 cancels and 2 extends at the same moment.
  async storm() {
    const made = [];
    for (let i = 0; i < 30; i++) made.push({ uid: `storm-${i}`, b: await createBooking(db(`storm-${i}`), `storm-${i}`, { parkingId: 'storm', spot: bay(1 + i), start: futureStart(4) }) });
    const results = await Promise.all(made.flatMap(({ uid, b }) => [
      ...Array.from({ length: 3 }, () => timed(() => cancelBooking(db(uid), uid, b)).then((r) => ['cancel', r])),
      ...Array.from({ length: 2 }, () => timed(() => extendBooking(db(uid), uid, b, 1)).then((r) => ['extend', r])),
    ]));
    const outcomes = results.reduce((m, [op, r]) => (m[`${op} ${r.ok ? 'ok' : kind(r.e)}`] = (m[`${op} ${r.ok ? 'ok' : kind(r.e)}`] ?? 0) + 1, m), {});
    const cancelsOk = outcomes['cancel ok'] ?? 0;
    const inv = await checkInvariants({ storm: 30 });
    const leftover = (await admin(async (fs) => (await getDocs(collection(fs, 'occupancy'))).docs.map((d) => d.data().FOUR_WHEELER ?? 0))).reduce((a, b) => a + b, 0);
    if (leftover !== 0) inv.problems.push(`occupancy not back to 0 after all cancels: ${leftover}`);
    return { bookings: 30, outcomes, latencyMs: stats(results.map(([, r]) => r.ms)), expect: 'exactly 30 cancels succeed; every slot back to 0',
      pass: cancelsOk === 30 && inv.problems.length === 0, ...inv };
  },
};

const pick = process.env.SCENARIO ? [process.env.SCENARIO] : Object.keys(scenarios);
const report = {};
for (const name of pick) {
  await env.clearFirestore();
  ctx.clear();
  const t = Date.now();
  report[name] = await scenarios[name]();
  report[name].wallSeconds = +((Date.now() - t) / 1000).toFixed(1);
  const { problems, ...rest } = report[name];
  console.log(`\n=== ${name}: ${report[name].pass ? 'PASS' : 'FAIL'}`);
  console.log(JSON.stringify({ ...rest, problems: problems?.slice(0, 10) }, null, 1));
}
mkdirSync(new URL('./results/', import.meta.url), { recursive: true });
writeFileSync(new URL(`./results/emulator-${new Date().toISOString().replace(/[:.]/g, '-')}.json`, import.meta.url), JSON.stringify(report, null, 2));
await env.cleanup();
process.exit(Object.values(report).every((r) => r.pass) ? 0 : 1);
