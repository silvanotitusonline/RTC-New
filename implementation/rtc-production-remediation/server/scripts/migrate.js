import { readFile } from 'node:fs/promises';
import pg from 'pg';

if (!process.env.DATABASE_URL) throw new Error('Missing DATABASE_URL');
const ssl = process.env.PG_TLS === 'true' ? {
  rejectUnauthorized: true,
  ...(process.env.PG_CA_FILE ? { ca: await readFile(process.env.PG_CA_FILE, 'utf8') } : {}),
} : undefined;
const client = new pg.Client({ connectionString: process.env.DATABASE_URL, ssl, connectionTimeoutMillis: 5000 });
try {
  await client.connect();
  await client.query(await readFile(new URL('../migrations/001_initial.sql', import.meta.url), 'utf8'));
  console.log('RTC API schema is ready.');
} finally { await client.end(); }
