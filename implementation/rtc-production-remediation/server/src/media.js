import multer from 'multer';
import sharp from 'sharp';
import { randomUUID, createHash } from 'node:crypto';
import { mkdir, open, rename, unlink, readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { HttpError } from './errors.js';

const allowed = new Set(['image/jpeg', 'image/png', 'image/webp']);
export async function createMediaStore(root) {
  const staging = join(root, 'staging');
  const objects = join(root, 'objects');
  await mkdir(staging, { recursive: true, mode: 0o700 });
  await mkdir(objects, { recursive: true, mode: 0o700 });
  const upload = multer({
    storage: multer.diskStorage({ destination: staging, filename: (_req, _file, cb) => cb(null, `${randomUUID()}.upload`) }),
    limits: { fileSize: 10 * 1024 * 1024, files: 1, fields: 1, parts: 3, fieldSize: 16000, fieldNameSize: 32 },
    fileFilter(_req, file, cb) {
      cb(allowed.has(file.mimetype) ? null : new HttpError(415, 'UNSUPPORTED_IMAGE', 'Choose a JPEG, PNG or WebP image.'), allowed.has(file.mimetype));
    },
  }).single('image');
  let processing = 0;
  function capacity(_req, res, next) {
    if (processing >= 4) {
      res.set('Retry-After', '5');
      throw new HttpError(503, 'UPLOAD_BUSY', 'Image processing is busy. Retry with the same request key.');
    }
    processing++;
    let released = false;
    const release = () => { if (!released) { released = true; processing--; } };
    res.once('close', release);
    res.once('finish', release);
    next();
  }
  async function prepare(file) {
    if (!file) return null;
    try {
      const source = await readFile(file.path);
      const metadata = await sharp(source, { limitInputPixels: 25000000, failOn: 'warning' }).metadata();
      if (!['jpeg', 'png', 'webp'].includes(metadata.format) || (metadata.pages ?? 1) !== 1) {
        throw new Error('INVALID_FORMAT');
      }
      // Decode and re-encode actual pixels. Client MIME/filename are not proof of content.
      // Auto-orient EXIF, remove metadata, preserve aspect ratio, and cap dimensions.
      const { data, info } = await sharp(source, { limitInputPixels: 25000000, failOn: 'warning' })
        .rotate().resize({ width: 2048, height: 2048, fit: 'inside', withoutEnlargement: true })
        .webp({ quality: 85 }).toBuffer({ resolveWithObject: true });
      return { data, width: info.width, height: info.height, sourceHash: createHash('sha256').update(source).digest('hex') };
    } catch {
      throw new HttpError(415, 'INVALID_IMAGE', 'The image is corrupt, animated, too large in pixels, or unsupported.');
    } finally {
      await unlink(file.path).catch(() => {});
    }
  }
  async function write(id, buffer) {
    const filename = `${id}.webp`;
    const stagedPath = join(staging, `${id}.prepared`);
    const handle = await open(stagedPath, 'wx', 0o600);
    try {
      await handle.writeFile(buffer);
      await handle.sync();
    } finally { await handle.close(); }
    await rename(stagedPath, join(objects, filename));
    const directory = await open(objects, 'r');
    try { await directory.sync(); } finally { await directory.close(); }
    return filename;
  }
  return { upload, capacity, prepare, write, objects };
}
