/**
 * Thrown when database schema migration fails.
 * Matches Java SchemaMigrationException.
 */
export class SchemaMigrationException extends Error {
  constructor(message: string, cause?: Error) {
    super(message, { cause });
    this.name = 'SchemaMigrationException';
  }
}
