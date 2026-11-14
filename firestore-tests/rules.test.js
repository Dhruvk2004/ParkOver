// Firestore rules + booking-transaction tests, run against the local emulator.
// The helpers below copy BookingRepository.kt (create / cancel / extend) step for step,
// so these tests exercise the same writes the app makes. Keep them in sync.
import { test, before, beforeEach, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, collection, query, where,
  runTransaction, Timestamp,
} from 'firebase/firestore';

const BUCKET_MS = 30 * 60_000;
const HOUR = 3_600_000;
const PARKING = 'p1';
const ACTIVE = ['PENDING', 'CONFIRMED', 'ACTIVE'];

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-parkover',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8'), host: '127.0.0.1', port: 8085 },
  });
});
after(async () => env?.cleanup());

beforeEach(async () => {
  await env.clearFirestore();
  // Seed availability as the app does: 2 four-wheeler bays, floor 1 has 2.
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'parking_availability', PARKING), {
      spotId: PARKING,
      availableSpotsTwoWheeler: 5,
      availableSpotsFourWheeler: 2,
      availableSpotsHeavy: 0,
      floorAvailability: { '1': 2 },
      lastUpdated: Timestamp.now(),
    });
  });
});

// One Firestore instance per user: calling .firestore() twice on a context throws.
const dbs = new Map();
const db = (uid) => {
  if (!dbs.has(uid)) dbs.set(uid, env.authenticatedContext(uid).firestore());
  return dbs.get(uid);
};
let anonDb;
const anon = () => (anonDb ??= env.unauthenticatedContext().firestore());

// ---- Copies of BookingRepository.kt ----

const bucketOf = (ms) => Math.floor(ms / BUCKET_MS);
function lockBuckets(entryMs, exitMs) {
  if (exitMs <= entryMs) return [];
  const out = [];
  for (let b = bucketOf(entryMs); b <= Math.floor((exitMs - 1) / BUCKET_MS); b++) out.push(b);
  return out;
}
const lockId = (parkingId, spot, bucket) => `${parkingId}_${spot}_${bucket}`;
const lockData = (parkingId, spotNumber, bucket, bookingId, userId) => ({
  parkingId, spotNumber, bucket, bookingId, userId,
  expiresAt: Timestamp.fromMillis((bucket + 1) * BUCKET_MS),
});
let idCounter = 0;
const newBookingId = () => `PK-TEST-${++idCounter}-${Date.now() % 100000}`;

function futureStart(hoursAhead = 2) {
  return (bucketOf(Date.now()) + hoursAhead * 2) * BUCKET_MS; // on a half hour, in the future
}

async function createBooking(fs, uid, { spot = 'L1-01', floor = 1, type = 'FOUR_WHEELER', start = futureStart(), hours = 2 } = {}) {
  const id = newBookingId();
  const entry = start, exit = start + hours * HOUR;
  const booking = {
    id, userId: uid, parkingId: PARKING, spotNumber: spot, floorNumber: floor, floorName: 'Level 1',
    vehicleType: type, entryTime: Timestamp.fromMillis(entry), exitTime: Timestamp.fromMillis(exit),
    durationHours: hours, bookingStatus: 'CONFIRMED', createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
  };
  const buckets = lockBuckets(entry, exit);
  await runTransaction(fs, async (tx) => {
    const lockRefs = buckets.map((b) => doc(fs, 'slot_locks', lockId(PARKING, spot, b)));
    for (const ref of lockRefs) {
      if ((await tx.get(ref)).exists()) throw new Error(`Spot ${spot} was just booked for that time.`);
    }
    const availRef = doc(fs, 'parking_availability', PARKING);
    const avail = await tx.get(availRef);
    const updates = {};
    if (avail.exists()) {
      const field = countField(type);
      const current = avail.get(field) ?? 0;
      if (current <= 0) throw new Error('This parking is full for your vehicle type.');
      updates[field] = current - 1;
      const floors = avail.get('floorAvailability');
      if (floors && String(floor) in floors) {
        if (floors[String(floor)] <= 0) throw new Error('Floor full');
        updates.floorAvailability = { ...floors, [String(floor)]: floors[String(floor)] - 1 };
      }
      updates.lastUpdated = Timestamp.now();
      updates.lastBookingId = id;
    }
    tx.set(doc(fs, 'bookings', id), booking);
    buckets.forEach((b, i) => tx.set(lockRefs[i], lockData(PARKING, spot, b, id, uid)));
    if (Object.keys(updates).length) tx.update(availRef, updates);
  });
  return booking;
}

async function cancelBooking(fs, uid, booking) {
  const buckets = lockBuckets(booking.entryTime.toMillis(), booking.exitTime.toMillis());
  await runTransaction(fs, async (tx) => {
    const bookingRef = doc(fs, 'bookings', booking.id);
    const fresh = await tx.get(bookingRef);
    if (!fresh.exists() || fresh.get('userId') !== uid) throw new Error('Booking not found.');
    if (!ACTIVE.includes(fresh.get('bookingStatus'))) throw new Error('This booking is already closed.');
    const lockSnaps = [];
    for (const b of buckets) lockSnaps.push(await tx.get(doc(fs, 'slot_locks', lockId(PARKING, booking.spotNumber, b))));
    const availRef = doc(fs, 'parking_availability', PARKING);
    const avail = await tx.get(availRef);
    tx.update(bookingRef, { bookingStatus: 'CANCELLED', updatedAt: Timestamp.now() });
    lockSnaps.filter((s) => s.exists() && s.get('bookingId') === booking.id).forEach((s) => tx.delete(s.ref));
    if (avail.exists()) {
      const field = countField(booking.vehicleType);
      const updates = { [field]: (avail.get(field) ?? 0) + 1, lastUpdated: Timestamp.now(), lastBookingId: booking.id };
      const floors = avail.get('floorAvailability');
      const key = String(booking.floorNumber);
      if (floors && key in floors) updates.floorAvailability = { ...floors, [key]: floors[key] + 1 };
      tx.update(availRef, updates);
    }
  });
}

async function extendBooking(fs, uid, booking, hours) {
  const oldExit = booking.exitTime.toMillis();
  const newExit = oldExit + hours * HOUR;
  const held = new Set(lockBuckets(booking.entryTime.toMillis(), oldExit));
  const extra = lockBuckets(oldExit, newExit).filter((b) => !held.has(b));
  await runTransaction(fs, async (tx) => {
    const ref = doc(fs, 'bookings', booking.id);
    const fresh = await tx.get(ref);
    if (!fresh.exists() || fresh.get('userId') !== uid) throw new Error('Booking not found.');
    if (!ACTIVE.includes(fresh.get('bookingStatus'))) throw new Error('Only active bookings can be extended.');
    const lockRefs = extra.map((b) => doc(fs, 'slot_locks', lockId(PARKING, booking.spotNumber, b)));
    for (const r of lockRefs) if ((await tx.get(r)).exists()) throw new Error('Spot taken after your slot.');
    tx.set(ref, { ...booking, exitTime: Timestamp.fromMillis(newExit), durationHours: booking.durationHours + hours, updatedAt: Timestamp.now() });
    extra.forEach((b, i) => tx.set(lockRefs[i], lockData(PARKING, booking.spotNumber, b, booking.id, uid)));
  });
}

function countField(t) {
  return t === 'TWO_WHEELER' ? 'availableSpotsTwoWheeler' : t === 'HEAVY' ? 'availableSpotsHeavy' : 'availableSpotsFourWheeler';
}

async function availability() {
  let data;
  await env.withSecurityRulesDisabled(async (ctx) => {
    data = (await getDoc(doc(ctx.firestore(), 'parking_availability', PARKING))).data();
  });
  return data;
}

async function allBookings() {
  let list = [];
  await env.withSecurityRulesDisabled(async (ctx) => {
    list = (await getDocs(collection(ctx.firestore(), 'bookings'))).docs.map((d) => d.data());
  });
  return list;
}

// ---- BOOK: the booking transaction ----

test('BOOK-01 happy path: booking, locks and availability -1 succeed together', async () => {
  const b = await assertSucceeds(createBooking(db('alice'), 'alice'));
  const a = await availability();
  assert.equal(a.availableSpotsFourWheeler, 1);
  assert.equal(a.floorAvailability['1'], 1);
  assert.equal(b.bookingStatus, 'CONFIRMED');
});

test('BOOK-06 overlapping booking on the same bay is rejected', async () => {
  const start = futureStart();
  await createBooking(db('alice'), 'alice', { start, hours: 2 });
  await assert.rejects(createBooking(db('bob'), 'bob', { start: start + HOUR, hours: 2 }), /just booked/);
  // Back-to-back (no overlap) is fine
  await assertSucceeds(createBooking(db('bob'), 'bob', { start: start + 2 * HOUR, hours: 1 }));
});

test('BOOK-07 two users racing for the same bay and time: exactly one wins', async () => {
  const start = futureStart();
  const results = await Promise.allSettled([
    createBooking(db('alice'), 'alice', { start }),
    createBooking(db('bob'), 'bob', { start }),
  ]);
  assert.equal(results.filter((r) => r.status === 'fulfilled').length, 1, JSON.stringify(results.map((r) => r.reason?.message)));
  assert.equal((await allBookings()).length, 1);
  assert.equal((await availability()).availableSpotsFourWheeler, 1);
});

test('LOAD-01 ten users racing for the last two bays: never overbooked, count never negative', async () => {
  const start = futureStart();
  const users = Array.from({ length: 10 }, (_, i) => `u${i}`);
  const results = await Promise.allSettled(
    users.map((u, i) => createBooking(db(u), u, { start, spot: `L1-0${(i % 3) + 1}` })),
  );
  const ok = results.filter((r) => r.status === 'fulfilled').length;
  const a = await availability();
  assert.ok(ok <= 2, `${ok} bookings for 2 bays`);
  assert.equal(a.availableSpotsFourWheeler, 2 - ok);
  assert.ok(a.availableSpotsFourWheeler >= 0);
  const spots = (await allBookings()).map((b) => b.spotNumber);
  assert.equal(new Set(spots).size, spots.length, 'same bay booked twice');
});

test('NET-07 a booking written without its slot lock is refused by the rules', async () => {
  const fs = db('mallory');
  const start = futureStart();
  await assertFails(setDoc(doc(fs, 'bookings', 'PK-SNEAKY'), {
    id: 'PK-SNEAKY', userId: 'mallory', parkingId: PARKING, spotNumber: 'L1-01', floorNumber: 1,
    vehicleType: 'FOUR_WHEELER', bookingStatus: 'CONFIRMED',
    entryTime: Timestamp.fromMillis(start), exitTime: Timestamp.fromMillis(start + HOUR), createdAt: Timestamp.now(),
  }));
});

test('NET-07 a booking cannot reuse a lock owned by someone else', async () => {
  const start = futureStart();
  const alice = await createBooking(db('alice'), 'alice', { start, hours: 1 });
  const fs = db('mallory');
  // Mallory claims Alice's bookingId inside her own booking doc to borrow the lock check.
  await assertFails(setDoc(doc(fs, 'bookings', alice.id), { ...alice, userId: 'mallory' }));
});

test('BKL-05 cancel frees the bay and gives the spot back once; second cancel fails', async () => {
  const start = futureStart();
  const b = await createBooking(db('alice'), 'alice', { start });
  await assertSucceeds(cancelBooking(db('alice'), 'alice', b));
  let a = await availability();
  assert.equal(a.availableSpotsFourWheeler, 2);
  assert.equal(a.floorAvailability['1'], 2);
  await assert.rejects(cancelBooking(db('alice'), 'alice', b), /already/);
  a = await availability();
  assert.equal(a.availableSpotsFourWheeler, 2, 'double cancel gave the spot back twice');
  // Bay is bookable again
  await assertSucceeds(createBooking(db('bob'), 'bob', { start }));
});

test('BKL-05 a cancelled booking cannot be reactivated', async () => {
  const b = await createBooking(db('alice'), 'alice');
  await cancelBooking(db('alice'), 'alice', b);
  await assertFails(updateDoc(doc(db('alice'), 'bookings', b.id), { bookingStatus: 'CONFIRMED' }));
});

test('BKL-06/07 extend works into free time and is refused into someone else\'s booking', async () => {
  const start = futureStart();
  const alice = await createBooking(db('alice'), 'alice', { start, hours: 1 });
  await assertSucceeds(extendBooking(db('alice'), 'alice', alice, 1));
  const extended = { ...alice, exitTime: Timestamp.fromMillis(start + 2 * HOUR), durationHours: 2 };
  await createBooking(db('bob'), 'bob', { start: start + 2 * HOUR, hours: 1 });
  await assert.rejects(extendBooking(db('alice'), 'alice', extended, 1), /taken/);
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

test('SEC-06 signed-out users cannot change availability', async () => {
  await assertFails(updateDoc(doc(anon(), 'parking_availability', PARKING), { availableSpotsFourWheeler: 99 }));
});

test('SEC-07 signed-in users cannot set availability to any value', async () => {
  const fs = db('mallory');
  await assertFails(updateDoc(doc(fs, 'parking_availability', PARKING), { availableSpotsFourWheeler: 9999 }));
  await assertFails(updateDoc(doc(fs, 'parking_availability', PARKING), { availableSpotsFourWheeler: 0 }));
  await assertFails(updateDoc(doc(fs, 'parking_availability', PARKING), { availableSpotsFourWheeler: 1, lastBookingId: 'made-up' }));
});

test('SEC-07 availability -1 is refused when it points at an existing booking', async () => {
  const b = await createBooking(db('mallory'), 'mallory');
  await assertFails(updateDoc(doc(db('mallory'), 'parking_availability', PARKING), {
    availableSpotsFourWheeler: 0, lastBookingId: b.id, lastUpdated: Timestamp.now(),
  }));
});

test('SEC-07 cancel cannot give back a different vehicle type or floor', async () => {
  const b = await createBooking(db('alice'), 'alice');
  const fs = db('alice');
  await assertFails(runTransaction(fs, async (tx) => {
    const avail = await tx.get(doc(fs, 'parking_availability', PARKING));
    tx.update(doc(fs, 'bookings', b.id), { bookingStatus: 'CANCELLED' });
    tx.update(doc(fs, 'parking_availability', PARKING), {
      availableSpotsTwoWheeler: avail.get('availableSpotsTwoWheeler') + 1, lastBookingId: b.id,
    });
  }));
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
