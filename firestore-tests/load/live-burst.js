// Phase 5: a small, capped burst against the LIVE dev project (parkover-b042b) to measure real
// latency and real contention. ~600 Firestore operations (Spark free tier: 50k reads + 20k
// writes a day). Bookings are 7 days ahead on Govandi bays G-80..G-100 and all cancelled;
// throwaway accounts are deleted; every touched occupancy slot must end where it started, and
// today's (current slot) count must not move at all.
//
//   npm run load:live
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, createUserWithEmailAndPassword, deleteUser } from 'firebase/auth';
import { getFirestore, doc, getDocFromServer, terminate } from 'firebase/firestore';
import { createBooking, cancelBooking, BUCKET_MS, HOUR, bucketOf, lockBuckets, occupancyId } from '../booking.js';

const gs = JSON.parse(readFileSync(new URL('../../app/google-services.json', import.meta.url), 'utf8'));
const config = {
  apiKey: gs.client[0].api_key[0].current_key,
  projectId: gs.project_info.project_id,
  appId: gs.client[0].client_info.mobilesdk_app_id,
};
const PARKING = 'spot_082'; // Govandi Station Parking
const USERS = 5, BOOKINGS_EACH = 4;

const pct = (xs, p) => { const s = [...xs].sort((a, b) => a - b); return s.length ? +s[Math.min(s.length - 1, Math.floor((p / 100) * s.length))].toFixed(0) : 0; };
const stats = (xs) => ({ n: xs.length, p50: pct(xs, 50), p95: pct(xs, 95), max: xs.length ? +Math.max(...xs).toFixed(0) : 0 });
const timed = async (fn) => { const t = performance.now(); try { const v = await fn(); return { ok: true, ms: performance.now() - t, v }; } catch (e) { return { ok: false, ms: performance.now() - t, e }; } };
const carsIn = async (db, bucket) => { const s = await getDocFromServer(doc(db, 'occupancy', occupancyId(PARKING, bucket))); return s.exists() ? s.get('FOUR_WHEELER') ?? 0 : 0; };
const sumCars = async (db, buckets) => (await Promise.all(buckets.map((b) => carsIn(db, b)))).reduce((a, b) => a + b, 0);

const drivers = [];
const made = [];
const report = { project: config.projectId, parking: PARKING };
try {
  for (let i = 0; i < USERS; i++) {
    const app = initializeApp(config, `load-${i}`);
    const auth = getAuth(app);
    const email = `load-${randomBytes(5).toString('hex')}@parkover.test`;
    const cred = await createUserWithEmailAndPassword(auth, email, `Ld-${randomBytes(12).toString('hex')}`);
    drivers.push({ app, auth, db: getFirestore(app), uid: cred.user.uid });
  }
  // 7 days ahead at 10:00 IST, away from any real use.
  const day = Math.floor(Date.now() / (24 * HOUR)) * 24 * HOUR + 7 * 24 * HOUR;
  const base = day + 4.5 * HOUR;
  const touched = lockBuckets(base, base + 14 * HOUR);
  const nowSlot = bucketOf(Date.now());
  const before = { slots: await sumCars(drivers[0].db, touched), now: await carsIn(drivers[0].db, nowSlot) };
  report.before = before;

  // A) latency: each driver makes 4 bookings on its own bays, all drivers at once.
  const create = [], cancel = [];
  await Promise.all(drivers.map(async (d, i) => {
    for (let j = 0; j < BOOKINGS_EACH; j++) {
      const r = await timed(() => createBooking(d.db, d.uid, { parkingId: PARKING, spot: `G-${80 + i * BOOKINGS_EACH + j}`, floor: 0, start: base + j * 2 * HOUR, hours: 1 }));
      create.push(r.ms);
      if (r.ok) made.push({ d, b: r.v }); else report.createErrors = [...(report.createErrors ?? []), r.e.message.slice(0, 120)];
    }
  }));
  report.mid = { slots: await sumCars(drivers[0].db, touched), now: await carsIn(drivers[0].db, nowSlot) };

  // B) real contention: all 5 drivers want bay G-100 for the same hour.
  const race = await Promise.all(drivers.map((d) => timed(() => createBooking(d.db, d.uid, { parkingId: PARKING, spot: 'G-100', floor: 0, start: base + 12 * HOUR, hours: 1 }))));
  race.filter((r) => r.ok).forEach((r, i) => made.push({ d: drivers.find((x) => x.uid === r.v.userId), b: r.v }));
  report.race = { winners: race.filter((r) => r.ok).length, losers: race.filter((r) => !r.ok).map((r) => r.e.message.slice(0, 80)), latency: stats(race.map((r) => r.ms)) };

  // Cancel everything we made, all at once.
  await Promise.all(made.map(async ({ d, b }) => { const r = await timed(() => cancelBooking(d.db, d.uid, b)); cancel.push(r.ms); if (!r.ok) report.cancelErrors = [...(report.cancelErrors ?? []), r.e.message.slice(0, 120)]; }));
  report.after = { slots: await sumCars(drivers[0].db, touched), now: await carsIn(drivers[0].db, nowSlot) };
  report.latencyMs = { create: stats(create), cancel: stats(cancel) };
  report.restored = report.after.slots === before.slots;
  report.nowUntouched = report.mid.now === before.now && report.after.now === before.now;
  // 20 one-hour bookings = 40 slot counts (each spans 2 half-hours).
  report.pass = report.restored && report.nowUntouched && report.race.winners === 1 && !report.createErrors && !report.cancelErrors
    && report.mid.slots === before.slots + USERS * BOOKINGS_EACH * 2;
} finally {
  for (const d of drivers) {
    await deleteUser(d.auth.currentUser).catch((e) => (report.cleanupErrors = [...(report.cleanupErrors ?? []), e.message]));
    await terminate(d.db).catch(() => {});
    await deleteApp(d.app).catch(() => {});
  }
  report.accountsDeleted = drivers.length - (report.cleanupErrors?.length ?? 0);
}
console.log(JSON.stringify(report, null, 1));
mkdirSync(new URL('./results/', import.meta.url), { recursive: true });
writeFileSync(new URL(`./results/live-${new Date().toISOString().replace(/[:.]/g, '-')}.json`, import.meta.url), JSON.stringify(report, null, 2));
process.exit(report.pass ? 0 : 1);
