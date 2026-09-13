import { createRemoteJWKSet, jwtVerify } from 'jose';
import { HttpError } from './errors.js';

export function createAuth({ issuer, audience, jwksUrl }, keyResolver) {
  // The optional resolver is dependency injection for a real signed local JWKS in tests,
  // never an alternate authorization path. Every request still verifies the signature.
  const keys = keyResolver ?? createRemoteJWKSet(new URL(jwksUrl), {
    timeoutDuration: 5000, cooldownDuration: 30000, cacheMaxAge: 600000,
  });
  return async (req, _res, next) => {
    const header = req.get('Authorization');
    if (!header || !/^Bearer [^\s]+$/i.test(header) || header.length > 16384) {
      throw new HttpError(401, 'UNAUTHENTICATED', 'Sign in to continue.');
    }
    try {
      const { payload } = await jwtVerify(header.slice(7), keys, {
        issuer, audience, algorithms: ['RS256', 'ES256'],
        requiredClaims: ['sub', 'iat', 'exp'], clockTolerance: 5,
      });
      if (typeof payload.sub !== 'string' || !payload.sub.trim() || payload.sub.length > 256) {
        throw new Error('INVALID_SUBJECT');
      }
      req.userId = payload.sub;
    } catch {
      throw new HttpError(401, 'UNAUTHENTICATED', 'Your session has expired. Sign in again.');
    }
    next();
  };
}
