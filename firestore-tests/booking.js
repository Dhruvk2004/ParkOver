// The app's booking writes (BookingRepository.kt: create / cancel / extend), copied step for
// step for the rules tests and the load tests. Keep in sync with the Kotlin.
//
// Occupancy: occupancy/{parkingId}_{bucket} counts booked bays per 30-minute slot, per vehicle
// type and per floor. A booking adds +1 to every slot of its stay; "free now" is capacity minus
// the current slot's count, so bookings for later never change today's numbers.
import { doc, runTransaction, Timestamp, increment, FieldPath } from 'firebase/firestore';

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
export const occupancyId = (parkingId, bucket) => `${parkingId}_${bucket}`;
export const lockData = (parkingId, spotNumber, bucket, bookingId, userId) => ({
  parkingId, spotNumber, bucket, bookingId, userId,
  expiresAt: Timestamp.fromMillis((bucket + 1) * BUCKET_MS),
});
let idCounter = 0;
export const newBookingId = () => `PK-TEST-${process.pid}-${++idCounter}-${Date.now() % 100000}`;

export function futureStart(hoursAhead = 2) {
  return (bucketOf(Date.now()) + hoursAhead * 2) * BUCKET_MS; // on a half hour, in the future
}

/** Runs again (fresh reads) when refused mid-rush, like BookingRepository.retryIfRefused. */
export async function retryIfRefused(fn) {
  for (let i = 0; i < 2; i++) {
    try { return await fn(); } catch (e) { if (e?.code !== 'permission-denied') throw e; }
  }
  return fn();
}

/** +1/-1 on one slot for one booking (type and floor), creating the slot doc if needed. */
function writeOccupancy(tx, ref, snap, { parkingId, bucket, type, floorKey, delta, bookingId }) {
  if (snap.exists()) {
    tx.update(ref, new FieldPath(type), increment(delta), new FieldPath('floors', floorKey), increment(delta),
      'lastBookingId', bookingId, 'lastUpdated', Timestamp.now());
  } else {
    tx.set(ref, { parkingId, bucket, [type]: delta, floors: { [floorKey]: delta }, lastBookingId: bookingId, lastUpdated: Timestamp.now() });
  }
}

const count = (snap, type) => (snap.exists() ? snap.get(type) ?? 0 : 0);
const floorCount = (snap, key) => (snap.exists() ? snap.get('floors')?.[key] ?? 0 : 0);

/**
 * capacity (optional): { type: bays for this vehicle type, floor: bays on this floor }. The app
 * passes the parking's numbers; slots that are already full refuse the booking.
 */
export async function createBooking(fs, uid, { parkingId = 'p1', spot = 'L1-01', floor = 1, type = 'FOUR_WHEELER', start = futureStart(), hours = 2, capacity } = {}) {
  const id = newBookingId();
  const entry = start, exit = start + hours * HOUR;
  const booking = {
    id, userId: uid, parkingId, spotNumber: spot, floorNumber: floor, floorName: 'Level 1',
    vehicleType: type, entryTime: Timestamp.fromMillis(entry), exitTime: Timestamp.fromMillis(exit),
    durationHours: hours, bookingStatus: 'CONFIRMED', createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
  };
  const buckets = lockBuckets(entry, exit);
  const floorKey = String(floor);
  await retryIfRefused(() => runTransaction(fs, async (tx) => {
    const lockRefs = buckets.map((b) => doc(fs, 'slot_locks', lockId(parkingId, spot, b)));
    for (const ref of lockRefs) {
      if ((await tx.get(ref)).exists()) throw new Error(`Spot ${spot} was just booked for that time.`);
    }
    const occRefs = buckets.map((b) => doc(fs, 'occupancy', occupancyId(parkingId, b)));
    const occ = [];
    for (const ref of occRefs) occ.push(await tx.get(ref));
    if (capacity) {
      if (occ.some((s) => count(s, type) >= capacity.type)) throw new Error('This parking is full for your vehicle type at that time.');
      if (capacity.floor != null && occ.some((s) => floorCount(s, floorKey) >= capacity.floor)) throw new Error('This floor is full at that time.');
    }
    tx.set(doc(fs, 'bookings', id), booking);
    buckets.forEach((b, i) => tx.set(lockRefs[i], lockData(parkingId, spot, b, id, uid)));
    buckets.forEach((b, i) => writeOccupancy(tx, occRefs[i], occ[i], { parkingId, bucket: b, type, floorKey, delta: 1, bookingId: id }));
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
    const lockSnaps = [], occSnaps = [];
    for (const b of buckets) lockSnaps.push(await tx.get(doc(fs, 'slot_locks', lockId(cur.parkingId, cur.spotNumber, b))));
    for (const b of buckets) occSnaps.push(await tx.get(doc(fs, 'occupancy', occupancyId(cur.parkingId, b))));
    tx.update(bookingRef, { bookingStatus: 'CANCELLED', updatedAt: Timestamp.now() });
    lockSnaps.filter((s) => s.exists() && s.get('bookingId') === cur.id).forEach((s) => tx.delete(s.ref));
    const floorKey = String(cur.floorNumber);
    // Bookings made before occupancy existed have no counts to give back (skip empty slots).
    buckets.forEach((b, i) => {
      if (count(occSnaps[i], cur.vehicleType) > 0 && floorCount(occSnaps[i], floorKey) > 0)
        writeOccupancy(tx, occSnaps[i].ref, occSnaps[i], { parkingId: cur.parkingId, bucket: b, type: cur.vehicleType, floorKey, delta: -1, bookingId: cur.id });
    });
  }));
}

export async function extendBooking(fs, uid, booking, hours, capacity) {
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
    const occSnaps = [];
    for (const b of extra) occSnaps.push(await tx.get(doc(fs, 'occupancy', occupancyId(cur.parkingId, b))));
    if (capacity && occSnaps.some((s) => count(s, cur.vehicleType) >= capacity.type)) throw new Error('This parking is full for your vehicle type at that time.');
    const updated = { ...cur, exitTime: Timestamp.fromMillis(newExit), durationHours: cur.durationHours + hours, updatedAt: Timestamp.now() };
    tx.set(ref, updated);
    const floorKey = String(cur.floorNumber);
    extra.forEach((b, i) => {
      if (snaps[i].exists()) return; // already ours from a quick second extend
      tx.set(lockRefs[i], lockData(cur.parkingId, cur.spotNumber, b, cur.id, uid));
      writeOccupancy(tx, occSnaps[i].ref, occSnaps[i], { parkingId: cur.parkingId, bucket: b, type: cur.vehicleType, floorKey, delta: 1, bookingId: cur.id });
    });
    return updated;
  }));
}
