export class DatabaseConnectionException extends Error {
  constructor(message: string, cause?: Error) {
    super(message);
    this.name = 'DatabaseConnectionException';
    if (cause) {
      this.cause = cause;
    }
  }
}
