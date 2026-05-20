/**
 * Thrown when database schema migration fails.
 * Matches Java SchemaMigrationException.
 */
export class SchemaMigrationException extends Error {
    constructor(message, cause) {
        super(message);
        this.name = 'SchemaMigrationException';
        if (cause) {
            this.cause = cause;
        }
    }
}
//# sourceMappingURL=schema-migration-exception.js.map