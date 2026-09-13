import pg from 'pg';
import { createHash } from 'node:crypto';
import { HttpError } from './errors.js';

export function createDatabase(connectionString, ssl) {
  const pool = new pg.Pool({
    connectionString, ssl, max: 10, connectionTimeoutMillis: 5000,
    idleTimeoutMillis: 30000, statement_timeout: 15000,
    application_name: 'rtc-remediation-api',
  });
  pool.on('error', () => console.error('DATABASE_IDLE_CONNECTION_ERROR'));
  return {
    query: (sql, params) => pool.query(sql, params),
    async transaction(work) {
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        await client.query("SET LOCAL lock_timeout = '5s'");
        const value = await work(client);
        await client.query('COMMIT');
        return value;
      } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        throw error;
      } finally {
        client.release();
      }
    },
    close: () => pool.end(),
  };
}

export function requestHash(operation, normalizedPayload) {
  return createHash('sha256').update(JSON.stringify([operation, normalizedPayload])).digest('hex');
}

export async function idempotent(db, userId, key, hash, operation) {
  return db.transaction(async (tx) => {
    const inserted = await tx.query(`
      INSERT INTO rtc_api.idempotency(user_id, key, request_hash)
      VALUES ($1, $2, $3) ON CONFLICT DO NOTHING RETURNING key`, [userId, key, hash]);
    if (inserted.rows.length === 0) {
      // A competing transaction must finish before ON CONFLICT returns. READ COMMITTED
      // obtains a fresh snapshot here, including that transaction's committed response.
      const { rows: [prior] } = await tx.query(`
        SELECT request_hash, response_status, response_body
        FROM rtc_api.idempotency WHERE user_id=$1 AND key=$2`, [userId, key]);
      if (!prior || prior.request_hash !== hash) {
        throw new HttpError(409, 'IDEMPOTENCY_CONFLICT', 'This request key was already used for different content.');
      }
      if (!prior.response_status) throw new HttpError(503, 'RETRY_LATER', 'The request is not ready. Retry with the same key.');
      return { status: prior.response_status, body: prior.response_body, replayed: true };
    }
    const result = await operation(tx);
    await tx.query(`UPDATE rtc_api.idempotency SET response_status=$3, response_body=$4::jsonb
      WHERE user_id=$1 AND key=$2`, [userId, key, result.status, JSON.stringify(result.body)]);
    return { ...result, replayed: false };
  });
}

export function rateLimit(db, scope, maxPerMinute) {
  return async (req, res, next) => {
    const bucket = Math.floor(Date.now() / 60000);
    const { rows: [row] } = await db.query(`
      INSERT INTO rtc_api.rate_limits(user_id, scope, bucket, hits) VALUES ($1,$2,$3,1)
      ON CONFLICT(user_id,scope) DO UPDATE SET bucket=excluded.bucket,
      hits=CASE WHEN rtc_api.rate_limits.bucket=excluded.bucket THEN rtc_api.rate_limits.hits+1 ELSE 1 END
      RETURNING hits`, [req.userId, scope, bucket]);
    if (row.hits > maxPerMinute) {
      res.set('Retry-After', String(60 - Math.floor(Date.now() / 1000) % 60));
      throw new HttpError(429, 'RATE_LIMITED', 'Too many requests. Please retry shortly.');
    }
    next();
  };
}
