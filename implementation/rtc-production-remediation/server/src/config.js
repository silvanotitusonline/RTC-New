import { resolve } from 'node:path';

export function readConfig(env = process.env) {
  const required = (name) => {
    const value = env[name]?.trim();
    if (!value) throw new Error(`Missing ${name}`);
    return value;
  };
  const secureUrl = (name) => {
    const value = required(name);
    const url = new URL(value);
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) {
      throw new Error(`${name} must be a credential-free HTTPS URL`);
    }
    return value;
  };
  const port = Number(env.PORT ?? '3000');
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid PORT');
  return {
    port,
    databaseUrl: required('DATABASE_URL'),
    publicBaseUrl: secureUrl('PUBLIC_BASE_URL').replace(/\/$/, ''),
    issuer: secureUrl('OIDC_ISSUER'),
    audience: required('OIDC_AUDIENCE'),
    jwksUrl: secureUrl('OIDC_JWKS_URL'),
    mediaDir: resolve(required('MEDIA_DIR')),
    tomtomKey: env.TOMTOM_API_KEY?.trim() ?? '',
    pgCaFile: env.PG_CA_FILE?.trim(),
    pgTls: env.PG_TLS === 'true',
  };
}
