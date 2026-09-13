import { readdir, stat, unlink, readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { readConfig } from '../src/config.js';
import { createDatabase } from '../src/database.js';

// Run at most daily. Only files older than 24 hours are candidates. The application
// commits within 15 seconds, leaving ample margin for upload/transaction completion.
const config = readConfig();
const ssl = config.pgTls ? { rejectUnauthorized: true, ...(config.pgCaFile ? { ca: await readFile(config.pgCaFile, 'utf8') } : {}) } : undefined;
const db = createDatabase(config.databaseUrl, ssl);
const olderThan = Date.now() - 24 * 60 * 60 * 1000;
let removed = 0;
try {
  for (const directory of ['staging', 'objects']) {
    const root = join(config.mediaDir, directory);
    for (const name of await readdir(root)) {
      if (!/^[0-9a-f-]{36}\.(upload|prepared|webp)$/.test(name)) continue;
      const path = join(root, name);
      const info = await stat(path);
      if (!info.isFile() || info.mtimeMs >= olderThan) continue;
      if (directory === 'objects') {
        const { rows } = await db.query('SELECT 1 FROM rtc_api.media WHERE filename=$1', [name]);
        if (rows.length) continue;
      }
      await unlink(path);
      removed++;
    }
  }
  console.log(`Removed ${removed} expired unreferenced media files.`);
} finally { await db.close(); }
