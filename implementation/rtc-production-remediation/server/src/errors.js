export class HttpError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}
export const badRequest = (message) => new HttpError(400, 'VALIDATION_ERROR', message);
