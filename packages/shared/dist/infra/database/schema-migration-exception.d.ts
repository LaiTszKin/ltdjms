/**
 * Thrown when database schema migration fails.
 * Matches Java SchemaMigrationException.
 */
export declare class SchemaMigrationException extends Error {
    constructor(message: string, cause?: Error);
}
