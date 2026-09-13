import { z } from 'zod';
import { badRequest } from './errors.js';

export const uuid = z.string().uuid();
const boundedText = (min, max) => z.string().trim().refine((value) => {
  const length = Array.from(value).length;
  return length >= min && length <= max;
}, `Enter ${min} to ${max} characters.`);
export const postInput = z.object({ body: boundedText(1, 4000) }).strict();
export const reportInput = z.object({
  body: boundedText(20, 4000),
  category: z.preprocess((value) => ({ 'App Support': 'APP_SUPPORT', Infrastructure: 'INFRASTRUCTURE' })[value] ?? value,
    z.enum(['APP_SUPPORT', 'INFRASTRUCTURE'])),
  priority: z.enum(['LOW', 'NORMAL', 'HIGH', 'URGENT']),
  latitude: z.number().finite().min(-90).max(90).nullable().optional().default(null),
  longitude: z.number().finite().min(-180).max(180).nullable().optional().default(null),
}).strict().refine((value) => (value.latitude === null) === (value.longitude === null), 'Supply both coordinates or neither.');
export const bookingInput = z.object({
  providerId: uuid,
  startsAt: z.iso.datetime({ precision: -1 }).or(z.iso.datetime()),
  notes: boundedText(0, 2000),
}).strict();
export const voteInput = z.object({ voted: z.boolean() }).strict();
export const routeQuery = z.object({
  fromLat: z.string().trim().min(1).pipe(z.coerce.number().finite().min(-90).max(90)),
  fromLon: z.string().trim().min(1).pipe(z.coerce.number().finite().min(-180).max(180)),
  toLat: z.string().trim().min(1).pipe(z.coerce.number().finite().min(-90).max(90)),
  toLon: z.string().trim().min(1).pipe(z.coerce.number().finite().min(-180).max(180)),
}).strict();
export const searchQuery = z.object({
  q: boundedText(2, 120),
  lat: z.string().trim().min(1).pipe(z.coerce.number().finite().min(-90).max(90)),
  lon: z.string().trim().min(1).pipe(z.coerce.number().finite().min(-180).max(180)),
}).strict();
export const pageQuery = z.object({
  limit: z.coerce.number().int().min(1).max(100).default(30),
  before: z.string().max(400).optional(),
}).strict();
export function parse(schema, value) {
  const result = schema.safeParse(value);
  if (!result.success) throw badRequest(result.error.issues[0]?.message ?? 'Invalid request.');
  return result.data;
}
export function idempotencyKey(req) { return parse(uuid, req.get('Idempotency-Key')); }
export function decodeCursor(value) {
  if (!value) return null;
  try {
    if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new Error();
    const parsed = JSON.parse(Buffer.from(value, 'base64url').toString());
    return parse(z.object({ at: z.iso.datetime(), id: uuid }).strict(), parsed);
  } catch { throw badRequest('Invalid feed cursor.'); }
}
export function encodeCursor(row) {
  return Buffer.from(JSON.stringify({ at: row.cursor_at ?? new Date(row.created_at).toISOString(), id: row.id })).toString('base64url');
}
