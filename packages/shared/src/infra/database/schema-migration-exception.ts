/**
 * Thrown when database schema migration fails.
 * Matches Java SchemaMigrationException.
 */
export class SchemaMigrationException extends Error {
  constructor(message: string, cause?: Error) {
    super(message);
    this.name = 'SchemaMigrationException';
    if (cause) {
      this.cause = cause;
    }
  }
}
