export class DatabaseConnectionException extends Error {
    cause;
    constructor(message, cause) {
        super(message);
        this.cause = cause;
        this.name = 'DatabaseConnectionException';
    }
}
//# sourceMappingURL=database-connection-exception.js.map