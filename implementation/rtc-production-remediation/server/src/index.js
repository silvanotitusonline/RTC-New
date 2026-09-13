import { readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { readConfig } from './config.js';
import { createDatabase } from './database.js';
import { createApp } from './app.js';

const config = readConfig();
const ssl = config.pgTls ? { rejectUnauthorized: true, ...(config.pgCaFile ? { ca: await readFile(config.pgCaFile, 'utf8') } : {}) } : undefined;
const db = createDatabase(config.databaseUrl, ssl);
await db.query('SELECT 1 FROM rtc_api.posts LIMIT 0');
const app = await createApp({ db, config });
const server = createServer({
  maxHeaderSize: 16384, requestTimeout: 30000, headersTimeout: 15000, keepAliveTimeout: 5000,
}, app);
server.maxHeadersCount = 64;
server.listen(config.port, '0.0.0.0', () => console.log(`RTC API listening on port ${config.port}`));
let closing = false;
async function shutdown() {
  if (closing) return;
  closing = true;
  const timeout = setTimeout(() => { server.closeAllConnections(); process.exitCode = 1; }, 15000);
  timeout.unref();
  server.close(async () => { await db.close(); clearTimeout(timeout); });
}
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
