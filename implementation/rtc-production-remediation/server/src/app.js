import express from 'express';
import helmet from 'helmet';
import multer from 'multer';
import { randomUUID } from 'node:crypto';
import { unlink } from 'node:fs/promises';
import { createAuth } from './auth.js';
import { idempotent, requestHash, rateLimit } from './database.js';
import { HttpError } from './errors.js';
import { createMediaStore } from './media.js';
import { createTomTom } from './tomtom.js';
import { parse, uuid, postInput, reportInput, bookingInput, voteInput, routeQuery, searchQuery, pageQuery, idempotencyKey, decodeCursor, encodeCursor } from './validation.js';

const iso = (date) => new Date(date).toISOString();
const feedColumns = `p.id, p.author_id, p.body, p.created_at,
  to_char(p.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS cursor_at, m.id AS media_id, m.width, m.height,
  (SELECT count(*)::int FROM rtc_api.post_votes WHERE post_id=p.id) AS vote_count,
  EXISTS(SELECT 1 FROM rtc_api.post_votes WHERE post_id=p.id AND user_id=$1) AS voted_by_me`;
function postDto(row, base) {
  return { id: row.id, authorId: row.author_id, body: row.body,
    imageUrl: row.media_id ? `${base}/media/${row.media_id}` : null,
    imageWidth: row.width ?? null, imageHeight: row.height ?? null,
    createdAt: iso(row.created_at), voteCount: row.vote_count, votedByMe: row.voted_by_me };
}
function reportDto(row) {
  return { id: row.id, body: row.body, category: row.category, priority: row.priority,
    status: row.status, createdAt: iso(row.created_at), latitude: row.latitude, longitude: row.longitude };
}
function bookingDto(row) {
  return { id: row.id, providerId: row.provider_id, startsAt: iso(row.starts_at), notes: row.notes,
    status: row.status, createdAt: iso(row.created_at) };
}
function respond(res, result) {
  res.set('Idempotency-Replayed', String(result.replayed)).status(result.status).json(result.body);
}

export async function createApp({ db, config, keyResolver, fetchImpl }) {
  const app = express();
  const media = await createMediaStore(config.mediaDir);
  const maps = createTomTom(config.tomtomKey, fetchImpl);
  app.disable('x-powered-by');
  app.set('query parser', 'simple');
  app.use(helmet());
  app.use((_req, res, next) => {
    res.set('Cache-Control', 'private, no-store');
    next();
  });
  app.get('/healthz', (_req, res) => res.json({ status: 'ok' }));
  app.use(createAuth(config, keyResolver));
  app.use(rateLimit(db, 'all', 180));
  app.use(express.json({ limit: '32kb', strict: true }));
  const writeLimit = rateLimit(db, 'write', 30);

  app.post('/posts', writeLimit, media.capacity, media.upload, async (req, res) => {
    try {
      const key = idempotencyKey(req);
      const body = parse(postInput, req.body).body;
      const image = await media.prepare(req.file);
      const hash = requestHash('POST /posts', { body, imageHash: image?.sourceHash ?? null });
      const result = await idempotent(db, req.userId, key, hash, async (tx) => {
        let mediaId = null;
        if (image) {
          mediaId = randomUUID();
          const filename = await media.write(mediaId, image.data);
          // Write file before committing its reference. A failed/unknown DB commit may
          // leave an orphan, but never delete a potentially committed image here.
          await tx.query(`INSERT INTO rtc_api.media(id, owner_id, filename, width, height)
            VALUES ($1,$2,$3,$4,$5)`, [mediaId, req.userId, filename, image.width, image.height]);
        }
        const id = randomUUID();
        await tx.query(`INSERT INTO rtc_api.posts(id, author_id, body, media_id) VALUES ($1,$2,$3,$4)`, [id, req.userId, body, mediaId]);
        const { rows: [row] } = await tx.query(`SELECT ${feedColumns} FROM rtc_api.posts p
          LEFT JOIN rtc_api.media m ON m.id=p.media_id WHERE p.id=$2`, [req.userId, id]);
        return { status: 201, body: postDto(row, config.publicBaseUrl) };
      });
      respond(res, result);
    } finally {
      if (req.file?.path) await unlink(req.file.path).catch(() => {});
    }
  });

  async function feed(req, res, own) {
    const { limit, before } = parse(pageQuery, req.query);
    const cursor = decodeCursor(before);
    const values = [req.userId];
    let where = 'p.deleted_at IS NULL';
    if (own) where += ' AND p.author_id=$1';
    if (cursor) {
      values.push(cursor.at, cursor.id);
      where += ` AND (p.created_at,p.id)<($2::timestamptz,$3::uuid)`;
    }
    values.push(limit + 1);
    const { rows } = await db.query(`SELECT ${feedColumns} FROM rtc_api.posts p
      LEFT JOIN rtc_api.media m ON m.id=p.media_id WHERE ${where}
      ORDER BY p.created_at DESC,p.id DESC LIMIT $${values.length}`, values);
    if (rows.length > limit) res.set('X-Next-Cursor', encodeCursor(rows[limit - 1]));
    res.json(rows.slice(0, limit).map((row) => postDto(row, config.publicBaseUrl)));
  }
  app.get('/feed', (req, res) => feed(req, res, false));
  app.get('/timeline', (req, res) => feed(req, res, true));

  app.get('/media/:id', async (req, res, next) => {
    const id = parse(uuid, req.params.id);
    const { rows: [row] } = await db.query(`SELECT m.filename FROM rtc_api.media m
      JOIN rtc_api.posts p ON p.media_id=m.id WHERE m.id=$1 AND p.deleted_at IS NULL`, [id]);
    if (!row) throw new HttpError(404, 'MEDIA_NOT_FOUND', 'This image is unavailable.');
    res.type('image/webp');
    // Authorization always precedes file serving; the storage directory is not public.
    res.sendFile(row.filename, { root: media.objects, dotfiles: 'deny', cacheControl: false }, (error) => {
      if (error) next(error.code === 'ENOENT' ? new HttpError(404, 'MEDIA_NOT_FOUND', 'This image is unavailable.') : error);
    });
  });

  app.put('/posts/:id/vote', writeLimit, async (req, res) => {
    const id = parse(uuid, req.params.id);
    const { voted } = parse(voteInput, req.body);
    const result = await db.transaction(async (tx) => {
      // Serialize votes with deletion and with each other for a coherent response count.
      const { rows } = await tx.query('SELECT id FROM rtc_api.posts WHERE id=$1 AND deleted_at IS NULL FOR UPDATE', [id]);
      if (!rows.length) throw new HttpError(404, 'POST_NOT_FOUND', 'This post is unavailable.');
      if (voted) await tx.query('INSERT INTO rtc_api.post_votes(post_id,user_id) VALUES ($1,$2) ON CONFLICT DO NOTHING', [id, req.userId]);
      else await tx.query('DELETE FROM rtc_api.post_votes WHERE post_id=$1 AND user_id=$2', [id, req.userId]);
      const { rows: [count] } = await tx.query('SELECT count(*)::int AS count FROM rtc_api.post_votes WHERE post_id=$1', [id]);
      return { postId: id, voteCount: count.count, votedByMe: voted };
    });
    res.json(result);
  });

  app.post('/reports', writeLimit, async (req, res) => {
    const key = idempotencyKey(req);
    const input = parse(reportInput, req.body);
    const result = await idempotent(db, req.userId, key, requestHash('POST /reports', input), async (tx) => {
      const { rows: [row] } = await tx.query(`INSERT INTO rtc_api.reports
        (id,reporter_id,body,category,priority,latitude,longitude) VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING *`,
      [randomUUID(), req.userId, input.body, input.category, input.priority, input.latitude, input.longitude]);
      // The table name comes only from the validated enum, never from client SQL text.
      const queue = input.category === 'APP_SUPPORT' ? 'it_queue' : 'municipal_queue';
      await tx.query(`INSERT INTO rtc_api.${queue}(report_id) VALUES ($1)`, [row.id]);
      return { status: 201, body: reportDto(row) };
    });
    respond(res, result);
  });
  app.get('/reports', async (req, res) => {
    const { rows } = await db.query('SELECT * FROM rtc_api.reports WHERE reporter_id=$1 ORDER BY created_at DESC,id DESC', [req.userId]);
    res.json(rows.map(reportDto));
  });
  app.get('/dashboard', async (req, res) => {
    const { rows: [row] } = await db.query(`SELECT
      count(*) FILTER (WHERE status='PENDING')::int AS pending,
      count(*) FILTER (WHERE status='WORKING')::int AS working,
      count(*) FILTER (WHERE status='RESOLVED')::int AS resolved,
      count(*)::int AS total FROM rtc_api.reports WHERE reporter_id=$1`, [req.userId]);
    res.json(row);
  });
  app.get('/providers', async (_req, res) => {
    const { rows } = await db.query('SELECT * FROM rtc_api.providers WHERE active=true ORDER BY name,id');
    res.json(rows.map((row) => ({ id: row.id, name: row.name, imageUrl: row.image_url,
      rateCents: Number(row.rate_cents), latitude: row.latitude, longitude: row.longitude })));
  });

  app.post('/bookings', writeLimit, async (req, res) => {
    const key = idempotencyKey(req);
    const parsed = parse(bookingInput, req.body);
    const input = { ...parsed, startsAt: new Date(parsed.startsAt).toISOString() };
    const result = await idempotent(db, req.userId, key, requestHash('POST /bookings', input), async (tx) => {
      // Check future time only on first execution. A delayed retry must still return
      // the original successful response after its booked start time has passed.
      if (Date.parse(input.startsAt) <= Date.now()) throw new HttpError(400, 'PAST_BOOKING', 'Choose a future booking time.');
      const { rows: providers } = await tx.query('SELECT id FROM rtc_api.providers WHERE id=$1 AND active=true FOR SHARE', [input.providerId]);
      if (!providers.length) throw new HttpError(404, 'PROVIDER_NOT_FOUND', 'This provider is unavailable.');
      const { rows: [row] } = await tx.query(`INSERT INTO rtc_api.bookings(id,user_id,provider_id,starts_at,notes)
        VALUES ($1,$2,$3,$4,$5) RETURNING *`, [randomUUID(), req.userId, input.providerId, input.startsAt, input.notes]);
      return { status: 201, body: bookingDto(row) };
    });
    respond(res, result);
  });

  app.get('/locations/route', rateLimit(db, 'maps', 30), async (req, res) => {
    res.json(await maps.route(parse(routeQuery, req.query)));
  });
  app.get('/locations/search', rateLimit(db, 'maps', 30), async (req, res) => {
    res.json(await maps.search(parse(searchQuery, req.query)));
  });
  app.use((_req, _res, next) => next(new HttpError(404, 'NOT_FOUND', 'This endpoint does not exist.')));
  app.use((error, _req, res, next) => {
    if (res.headersSent) return next(error);
    let failure = error;
    if (error instanceof multer.MulterError) {
      failure = new HttpError(error.code === 'LIMIT_FILE_SIZE' ? 413 : 400, 'UPLOAD_REJECTED',
        error.code === 'LIMIT_FILE_SIZE' ? 'Images must be no larger than 10 MiB.' : 'Upload one image and one body field.');
    } else if (error.code === '23505' && error.constraint === 'bookings_provider_slot_key') {
      failure = new HttpError(409, 'SLOT_UNAVAILABLE', 'This provider is already booked at that time.');
    } else if (['55P03', '57014', '53300', '57P01', '08006', 'ECONNREFUSED', 'ETIMEDOUT'].includes(error.code)) {
      failure = new HttpError(503, 'RETRY_LATER', 'The service is busy. Retry with the same request key.');
    } else if (error.type === 'entity.too.large') {
      failure = new HttpError(413, 'PAYLOAD_TOO_LARGE', 'The request is too large.');
    } else if (error instanceof SyntaxError && error.status === 400) {
      failure = new HttpError(400, 'INVALID_JSON', 'Send valid JSON.');
    }
    if (!(failure instanceof HttpError)) {
      console.error('REQUEST_FAILED', { code: typeof error.code === 'string' ? error.code : 'INTERNAL_ERROR' });
      failure = new HttpError(500, 'INTERNAL_ERROR', 'The request could not be completed.');
    }
    if (failure.status === 503) res.set('Retry-After', '5');
    if (failure.status === 401) res.set('WWW-Authenticate', 'Bearer realm="RTC"');
    res.status(failure.status).json({ error: { code: failure.code, message: failure.message } });
  });
  return app;
}
