// Firestore rules + booking-transaction tests, run against the local emulator.
// ./booking.js copies BookingRepository.kt (create / cancel / extend) step for step, so these
// tests exercise the same writes the app makes.
import { test, before, beforeEach, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, collection, query, where, runTransaction, Timestamp, increment } from 'firebase/firestore';
import { HOUR, bucketOf, lockBuckets, lockId, lockData, occupancyId, futureStart, createBooking, cancelBooking, extendBooking } from './booking.js';

const PARKING = 'p1';
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-parkover',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8085 },
  });
});
after(async () => env?.cleanup());
beforeEach(async () => env.clearFirestore());

// One Firestore instance per user: calling .firestore() twice on a context throws.
const dbs = new Map();
const db = (uid) => { if (!dbs.has(uid)) dbs.set(uid, env.authenticatedContext(uid).firestore()); return dbs.get(uid); };
let anonDb;
const anon = () => (anonDb ??= env.unauthenticatedContext().firestore());

async function adminRead(path) {
  let data;
  await env.withSecurityRulesDisabled(async (ctx) => { const s = await getDoc(doc(ctx.firestore(), path)); data = s.exists() ? s.data() : undefined; });
  return data;
}
async function adminList(col) {
  let list = [];
  await env.withSecurityRulesDisabled(async (ctx) => { list = (await getDocs(collection(ctx.firestore(), col))).docs.map((d) => ({ _id: d.id, ...d.data() })); });
  return list;
}
const occAt = async (bucket, parking = PARKING) => adminRead(`occupancy/${occupancyId(parking, bucket)}`);
const cars = (o) => o?.FOUR_WHEELER ?? 0;

// ---- BOOK: the booking transaction ----

test('BOOK-01 happy path: booking, locks and +1 occupancy for every slot of the stay', async () => {
  const start = futureStart();
  const b = await assertSucceeds(createBooking(db('alice'), 'alice', { start, hours: 2 }));
  for (const bucket of lockBuckets(start, start + 2 * HOUR)) {
    const o = await occAt(bucket);
    assert.equal(cars(o), 1);
    assert.equal(o.floors['1'], 1);
  }
  assert.equal((await adminList('slot_locks')).length, 4);
  assert.equal(b.bookingStatus, 'CONFIRMED');
});

test('FREE-NOW a booking for later never touches the current slot', async () => {
  const now = bucketOf(Date.now());
  await createBooking(db('alice'), 'alice', { start: futureStart(24), hours: 2 }); // tomorrow
  assert.equal(await occAt(now), undefined, 'today\'s slot must stay empty');
  assert.equal(await occAt(now + 1), undefined);
});

test('FREE-NOW a stay running now counts in the current slot', async () => {
  const nowStart = bucketOf(Date.now()) * 30 * 60_000; // started at the top of this half hour
  await createBooking(db('alice'), 'alice', { start: nowStart, hours: 1 });
  assert.equal(cars(await occAt(bucketOf(Date.now()))), 1);
});

test('BOOK-08 full at one time, still bookable at another (time-aware capacity)', async () => {
  const cap = { type: 1, floor: 10 };
  const ten = futureStart(4);
  await createBooking(db('alice'), 'alice', { spot: 'L1-01', start: ten, hours: 2, capacity: cap });
  await assert.rejects(createBooking(db('bob'), 'bob', { spot: 'L1-02', start: ten + HOUR, hours: 2, capacity: cap }), /full/);
  await assertSucceeds(createBooking(db('bob'), 'bob', { spot: 'L1-02', start: ten + 2 * HOUR, hours: 2, capacity: cap }));
});

test('BOOK-06 overlapping booking on the same bay is rejected; back-to-back is fine', async () => {
  const start = futureStart();
  await createBooking(db('alice'), 'alice', { start, hours: 2 });
  await assert.rejects(createBooking(db('bob'), 'bob', { start: start + HOUR, hours: 2 }), /just booked/);
  await assertSucceeds(createBooking(db('bob'), 'bob', { start: start + 2 * HOUR, hours: 1 }));
});

test('BOOK-07 two users racing for the same bay and time: exactly one wins', async () => {
  const start = futureStart();
  const results = await Promise.allSettled([
    createBooking(db('alice'), 'alice', { start }),
    createBooking(db('bob'), 'bob', { start }),
  ]);
  assert.equal(results.filter((r) => r.status === 'fulfilled').length, 1);
  assert.equal((await adminList('bookings')).length, 1);
  assert.equal(cars(await occAt(bucketOf(start))), 1);
});

test('LOAD-01 ten users racing for 2 car bays at one time: never overbooked', async () => {
  const start = futureStart();
  const cap = { type: 2, floor: 20 };
  const results = await Promise.allSettled(Array.from({ length: 10 }, (_, i) =>
    createBooking(db(`u${i}`), `u${i}`, { start, spot: `L1-0${(i % 5) + 1}`, capacity: cap })));
  const ok = results.filter((r) => r.status === 'fulfilled').length;
  assert.ok(ok >= 1 && ok <= 2, `${ok} bookings for 2 bays`);
  for (const bucket of lockBuckets(start, start + 2 * HOUR)) assert.equal(cars(await occAt(bucket)), ok);
  const spots = (await adminList('bookings')).map((b) => b.spotNumber);
  assert.equal(new Set(spots).size, spots.length, 'same bay booked twice');
});

test('LIMITS a full-day (24 h, 48 slots) booking, cancel and extend fit the rules limits', async () => {
  const start = futureStart(30);
  const b = await assertSucceeds(createBooking(db('alice'), 'alice', { start, hours: 24 }));
  assert.equal(cars(await occAt(bucketOf(start) + 47)), 1);
  await assertSucceeds(extendBooking(db('alice'), 'alice', b, 1));
  await assertSucceeds(cancelBooking(db('alice'), 'alice', b));
  assert.equal(cars(await occAt(bucketOf(start) + 49)), 0);
});

test('NET-07 a booking written without its slot lock is refused by the rules', async () => {
  const start = futureStart();
  await assertFails(setDoc(doc(db('mallory'), 'bookings', 'PK-SNEAKY'), {
    id: 'PK-SNEAKY', userId: 'mallory', parkingId: PARKING, spotNumber: 'L1-01', floorNumber: 1,
    vehicleType: 'FOUR_WHEELER', bookingStatus: 'CONFIRMED',
    entryTime: Timestamp.fromMillis(start), exitTime: Timestamp.fromMillis(start + HOUR), createdAt: Timestamp.now(),
  }));
});

test('NET-07 a booking cannot reuse a lock owned by someone else', async () => {
  const alice = await createBooking(db('alice'), 'alice', { start: futureStart(), hours: 1 });
  await assertFails(setDoc(doc(db('mallory'), 'bookings', alice.id), { ...alice, userId: 'mallory' }));
});

test('BKL-05 cancel frees the bay and the slots once; a second cancel fails', async () => {
  const start = futureStart();
  const b = await createBooking(db('alice'), 'alice', { start });
  await assertSucceeds(cancelBooking(db('alice'), 'alice', b));
  for (const bucket of lockBuckets(start, start + 2 * HOUR)) assert.equal(cars(await occAt(bucket)), 0);
  await assert.rejects(cancelBooking(db('alice'), 'alice', b), /already/);
  assert.equal(cars(await occAt(bucketOf(start))), 0, 'never below zero');
  assert.equal((await adminList('slot_locks')).length, 0);
  await assertSucceeds(createBooking(db('bob'), 'bob', { start }));
});

test('BKL-05 a cancelled booking cannot be reactivated', async () => {
  const b = await createBooking(db('alice'), 'alice');
  await cancelBooking(db('alice'), 'alice', b);
  await assertFails(updateDoc(doc(db('alice'), 'bookings', b.id), { bookingStatus: 'CONFIRMED' }));
});

test('BKL-06 extend adds occupancy only for the extra slots; refused into someone else\'s time', async () => {
  const start = futureStart();
  const alice = await createBooking(db('alice'), 'alice', { start, hours: 1 });
  await assertSucceeds(extendBooking(db('alice'), 'alice', alice, 1));
  assert.equal(cars(await occAt(bucketOf(start))), 1, 'existing slot not counted twice');
  assert.equal(cars(await occAt(bucketOf(start) + 3)), 1);
  await createBooking(db('bob'), 'bob', { start: start + 2 * HOUR, hours: 1 });
  await assert.rejects(extendBooking(db('alice'), 'alice', alice, 1), /taken/);
});

test('BKL-06 cancelling an extended booking gives back every slot (no orphans)', async () => {
  const start = futureStart();
  const b = await createBooking(db('alice'), 'alice', { start, hours: 1 });
  await extendBooking(db('alice'), 'alice', b, 2);
  await cancelBooking(db('alice'), 'alice', b); // stale copy on purpose
  for (const bucket of lockBuckets(start, start + 3 * HOUR)) assert.equal(cars(await occAt(bucket)), 0);
  assert.equal((await adminList('slot_locks')).length, 0);
});

test('BKL-07 extending by editing the booking without locking the new time is refused', async () => {
  const start = futureStart();
  const alice = await createBooking(db('alice'), 'alice', { start, hours: 1 });
  await assertFails(updateDoc(doc(db('alice'), 'bookings', alice.id), { exitTime: Timestamp.fromMillis(start + 5 * HOUR) }));
});

test('BOOK booking cannot be moved to another bay or parking', async () => {
  const alice = await createBooking(db('alice'), 'alice');
  await assertFails(updateDoc(doc(db('alice'), 'bookings', alice.id), { spotNumber: 'L1-02' }));
  await assertFails(updateDoc(doc(db('alice'), 'bookings', alice.id), { parkingId: 'p2' }));
});

// ---- SEC: privacy and tampering ----

test('SEC-03 users can read only their own bookings', async () => {
  const b = await createBooking(db('alice'), 'alice');
  await assertSucceeds(getDoc(doc(db('alice'), 'bookings', b.id)));
  await assertFails(getDoc(doc(db('bob'), 'bookings', b.id)));
  await assertSucceeds(getDocs(query(collection(db('alice'), 'bookings'), where('userId', '==', 'alice'))));
  await assertFails(getDocs(query(collection(db('bob'), 'bookings'), where('parkingId', '==', PARKING))));
});

test('SEC-04 cannot create a booking for another user', async () => {
  await assert.rejects(createBooking(db('mallory'), 'alice'));
});

test('SEC-05 bookings cannot be deleted', async () => {
  const b = await createBooking(db('alice'), 'alice');
  await assertFails(deleteDoc(doc(db('alice'), 'bookings', b.id)));
});

test('SEC-06 occupancy is public to read, but signed-out users cannot write it', async () => {
  const b = await createBooking(db('alice'), 'alice');
  const id = occupancyId(PARKING, bucketOf(b.entryTime.toMillis()));
  await assertSucceeds(getDoc(doc(anon(), 'occupancy', id)));
  await assertFails(updateDoc(doc(anon(), 'occupancy', id), { FOUR_WHEELER: 0 }));
});

test('SEC-07 occupancy cannot be forged', async () => {
  const b = await createBooking(db('alice'), 'alice');
  const bucket = bucketOf(b.entryTime.toMillis());
  const id = occupancyId(PARKING, bucket);
  const fs = db('mallory');
  // Arbitrary values, made-up bookings, someone else's booking:
  await assertFails(updateDoc(doc(fs, 'occupancy', id), { FOUR_WHEELER: 0 }));
  await assertFails(updateDoc(doc(fs, 'occupancy', id), { FOUR_WHEELER: increment(-1), 'floors.1': increment(-1), lastBookingId: 'made-up' }));
  await assertFails(updateDoc(doc(fs, 'occupancy', id), { FOUR_WHEELER: increment(-1), 'floors.1': increment(-1), lastBookingId: b.id }));
  // Blocking a whole slot without booking:
  await assertFails(setDoc(doc(fs, 'occupancy', occupancyId(PARKING, bucket + 20)), { parkingId: PARKING, bucket: bucket + 20, FOUR_WHEELER: 99, floors: { 1: 99 }, lastBookingId: 'x' }));
});

test('SEC-07 even the owner can only move a count by ±1 inside the booking\'s own stay', async () => {
  const b = await createBooking(db('alice'), 'alice', { hours: 1 });
  const fs = db('alice');
  const outside = bucketOf(b.exitTime.toMillis()) + 3;
  // +1 on a slot outside the stay, tied to an existing (not newly created) booking:
  await assertFails(setDoc(doc(fs, 'occupancy', occupancyId(PARKING, outside)), { parkingId: PARKING, bucket: outside, FOUR_WHEELER: 1, floors: { 1: 1 }, lastBookingId: b.id }));
  // Cancel that tries to give back 2 at once:
  await assertFails(runTransaction(fs, async (tx) => {
    const id = occupancyId(PARKING, bucketOf(b.entryTime.toMillis()));
    tx.update(doc(fs, 'bookings', b.id), { bookingStatus: 'CANCELLED' });
    tx.update(doc(fs, 'occupancy', id), { FOUR_WHEELER: increment(-2), 'floors.1': increment(-2), lastBookingId: b.id });
  }));
});

test('SEC-07 legacy single counters are read-only now', async () => {
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'parking_availability', PARKING), { availableSpotsFourWheeler: 5 }));
  await assertSucceeds(getDoc(doc(anon(), 'parking_availability', PARKING)));
  await assertFails(updateDoc(doc(db('alice'), 'parking_availability', PARKING), { availableSpotsFourWheeler: 4 }));
});

test('SEC slot locks: readable when signed in, not by guests; cannot forge or steal', async () => {
  const b = await createBooking(db('alice'), 'alice');
  const bucket = bucketOf(b.entryTime.toMillis());
  const id = lockId(PARKING, b.spotNumber, bucket);
  await assertSucceeds(getDoc(doc(db('bob'), 'slot_locks', id)));
  await assertFails(getDoc(doc(anon(), 'slot_locks', id)));
  await assertFails(deleteDoc(doc(db('bob'), 'slot_locks', id)));
  await assertFails(setDoc(doc(db('bob'), 'slot_locks', id), lockData(PARKING, b.spotNumber, bucket, 'x', 'bob')));
  await assertFails(setDoc(doc(db('bob'), 'slot_locks', 'wrong-id'), lockData(PARKING, 'L1-09', bucket, 'x', 'bob')));
});

test('SEC-01/02 users and vehicles stay private', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const fs = ctx.firestore();
    await setDoc(doc(fs, 'users', 'alice'), { name: 'Alice' });
    await setDoc(doc(fs, 'vehicles', 'v1'), { userId: 'alice', number: 'MH01AB1234' });
  });
  await assertFails(getDoc(doc(db('bob'), 'users', 'alice')));
  await assertFails(getDoc(doc(db('bob'), 'vehicles', 'v1')));
  await assertSucceeds(getDoc(doc(db('alice'), 'vehicles', 'v1')));
});

test('SEC-08 support tickets cannot spoof another user', async () => {
  await assertFails(setDoc(doc(db('mallory'), 'support_tickets', 't1'), { userId: 'alice', message: 'hi' }));
  await assertSucceeds(setDoc(doc(db('alice'), 'support_tickets', 't2'), { userId: 'alice', message: 'hi' }));
});
