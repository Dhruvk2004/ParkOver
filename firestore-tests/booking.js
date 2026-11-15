// The app's booking writes (BookingRepository.kt: create / cancel / extend), copied step for
// step for the rules tests and the load tests. Keep in sync with the Kotlin.
import { doc, runTransaction, Timestamp, increment, FieldPath } from 'firebase/firestore';

/** Field/value pairs for a ±1 change of a parking's free counts (increment, like the app). */
function countChange(field, floorKey, delta, bookingId) {
  const pairs = [new FieldPath(field), increment(delta)];
  if (floorKey != null) pairs.push(new FieldPath('floorAvailability', floorKey), increment(delta));
  pairs.push(new FieldPath('lastUpdated'), Timestamp.now(), new FieldPath('lastBookingId'), bookingId);
  return pairs;
}

/** Runs again (fresh reads) when refused mid-rush, like BookingRepository.retryIfRefused. */
export async function retryIfRefused(fn) {
  for (let i = 0; i < 2; i++) {
    try { return await fn(); } catch (e) { if (e?.code !== 'permission-denied') throw e; }
  }
  return fn();
}

export const BUCKET_MS = 30 * 60_000;
export const HOUR = 3_600_000;
export const ACTIVE = ['PENDING', 'CONFIRMED', 'ACTIVE'];

export const bucketOf = (ms) => Math.floor(ms / BUCKET_MS);
export function lockBuckets(entryMs, exitMs) {
  if (exitMs <= entryMs) return [];
  const out = [];
  for (let b = bucketOf(entryMs); b <= Math.floor((exitMs - 1) / BUCKET_MS); b++) out.push(b);
  return out;
}
export const lockId = (parkingId, spot, bucket) => `${parkingId}_${spot}_${bucket}`;
export const lockData = (parkingId, spotNumber, bucket, bookingId, userId) => ({
  parkingId, spotNumber, bucket, bookingId, userId,
  expiresAt: Timestamp.fromMillis((bucket + 1) * BUCKET_MS),
});
let idCounter = 0;
export const newBookingId = () => `PK-TEST-${process.pid}-${++idCounter}-${Date.now() % 100000}`;

export function futureStart(hoursAhead = 2) {
  return (bucketOf(Date.now()) + hoursAhead * 2) * BUCKET_MS; // on a half hour, in the future
}

export async function createBooking(fs, uid, { parkingId = 'p1', spot = 'L1-01', floor = 1, type = 'FOUR_WHEELER', start = futureStart(), hours = 2 } = {}) {
  const id = newBookingId();
  const entry = start, exit = start + hours * HOUR;
  const booking = {
    id, userId: uid, parkingId, spotNumber: spot, floorNumber: floor, floorName: 'Level 1',
    vehicleType: type, entryTime: Timestamp.fromMillis(entry), exitTime: Timestamp.fromMillis(exit),
    durationHours: hours, bookingStatus: 'CONFIRMED', createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
  };
  const buckets = lockBuckets(entry, exit);
  await retryIfRefused(() => runTransaction(fs, async (tx) => {
    const lockRefs = buckets.map((b) => doc(fs, 'slot_locks', lockId(parkingId, spot, b)));
    for (const ref of lockRefs) {
      if ((await tx.get(ref)).exists()) throw new Error(`Spot ${spot} was just booked for that time.`);
    }
    const availRef = doc(fs, 'parking_availability', parkingId);
    const avail = await tx.get(availRef);
    let change = null;
    if (avail.exists()) {
      const field = countField(type);
      if ((avail.get(field) ?? 0) <= 0) throw new Error('This parking is full for your vehicle type.');
      const floors = avail.get('floorAvailability');
      const key = String(floor);
      const tracks = !!floors && key in floors;
      if (tracks && floors[key] <= 0) throw new Error('Floor full');
      change = countChange(field, tracks ? key : null, -1, id);
    }
    tx.set(doc(fs, 'bookings', id), booking);
    buckets.forEach((b, i) => tx.set(lockRefs[i], lockData(parkingId, spot, b, id, uid)));
    if (change) tx.update(availRef, ...change);
  }));
  return booking;
}

export async function cancelBooking(fs, uid, booking) {
  // Like the app: everything from the booking as stored now, not the (maybe stale) copy.
  await retryIfRefused(() => runTransaction(fs, async (tx) => {
    const bookingRef = doc(fs, 'bookings', booking.id);
    const fresh = await tx.get(bookingRef);
    if (!fresh.exists() || fresh.get('userId') !== uid) throw new Error('Booking not found.');
    if (!ACTIVE.includes(fresh.get('bookingStatus'))) throw new Error('This booking is already closed.');
    const cur = fresh.data();
    const buckets = lockBuckets(cur.entryTime.toMillis(), cur.exitTime.toMillis());
    const lockSnaps = [];
    for (const b of buckets) lockSnaps.push(await tx.get(doc(fs, 'slot_locks', lockId(cur.parkingId, cur.spotNumber, b))));
    const availRef = doc(fs, 'parking_availability', cur.parkingId);
    const avail = await tx.get(availRef);
    tx.update(bookingRef, { bookingStatus: 'CANCELLED', updatedAt: Timestamp.now() });
    lockSnaps.filter((s) => s.exists() && s.get('bookingId') === cur.id).forEach((s) => tx.delete(s.ref));
    if (avail.exists()) {
      const floors = avail.get('floorAvailability');
      const key = String(cur.floorNumber);
      tx.update(availRef, ...countChange(countField(cur.vehicleType), floors && key in floors ? key : null, +1, cur.id));
    }
  }));
}

export async function extendBooking(fs, uid, booking, hours) {
  // Like the app: from the stored end time, so quick repeated extends add up.
  return retryIfRefused(() => runTransaction(fs, async (tx) => {
    const ref = doc(fs, 'bookings', booking.id);
    const fresh = await tx.get(ref);
    if (!fresh.exists() || fresh.get('userId') !== uid) throw new Error('Booking not found.');
    if (!ACTIVE.includes(fresh.get('bookingStatus'))) throw new Error('Only active bookings can be extended.');
    const cur = fresh.data();
    const oldExit = cur.exitTime.toMillis();
    const newExit = oldExit + hours * HOUR;
    const held = new Set(lockBuckets(cur.entryTime.toMillis(), oldExit));
    const extra = lockBuckets(oldExit, newExit).filter((b) => !held.has(b));
    const lockRefs = extra.map((b) => doc(fs, 'slot_locks', lockId(cur.parkingId, cur.spotNumber, b)));
    const snaps = [];
    for (const r of lockRefs) snaps.push(await tx.get(r));
    // Own lock (quick second extend) isn't a clash; only someone else's is.
    if (snaps.some((s) => s.exists() && s.get('bookingId') !== cur.id)) throw new Error('Spot taken after your slot.');
    const updated = { ...cur, exitTime: Timestamp.fromMillis(newExit), durationHours: cur.durationHours + hours, updatedAt: Timestamp.now() };
    tx.set(ref, updated);
    extra.forEach((b, i) => { if (!snaps[i].exists()) tx.set(lockRefs[i], lockData(cur.parkingId, cur.spotNumber, b, cur.id, uid)); });
    return updated;
  }));
}

export function countField(t) {
  return t === 'TWO_WHEELER' ? 'availableSpotsTwoWheeler' : t === 'HEAVY' ? 'availableSpotsHeavy' : 'availableSpotsFourWheeler';
}
