/**
 * Error categories for classifying different types of domain errors.
 * Matches Java DomainError.Category exactly.
 */
export declare enum DomainErrorCategory {
    INVALID_INPUT = "INVALID_INPUT",
    INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE",
    INSUFFICIENT_TOKENS = "INSUFFICIENT_TOKENS",
    PERSISTENCE_FAILURE = "PERSISTENCE_FAILURE",
    UNEXPECTED_FAILURE = "UNEXPECTED_FAILURE",
    DISCORD_INTERACTION_TIMEOUT = "DISCORD_INTERACTION_TIMEOUT",
    DISCORD_HOOK_EXPIRED = "DISCORD_HOOK_EXPIRED",
    DISCORD_UNKNOWN_MESSAGE = "DISCORD_UNKNOWN_MESSAGE",
    DISCORD_RATE_LIMITED = "DISCORD_RATE_LIMITED",
    DISCORD_MISSING_PERMISSIONS = "DISCORD_MISSING_PERMISSIONS",
    DISCORD_INVALID_COMPONENT_ID = "DISCORD_INVALID_COMPONENT_ID",
    AI_SERVICE_TIMEOUT = "AI_SERVICE_TIMEOUT",
    AI_SERVICE_AUTH_FAILED = "AI_SERVICE_AUTH_FAILED",
    AI_SERVICE_RATE_LIMITED = "AI_SERVICE_RATE_LIMITED",
    AI_SERVICE_UNAVAILABLE = "AI_SERVICE_UNAVAILABLE",
    AI_RESPONSE_EMPTY = "AI_RESPONSE_EMPTY",
    AI_RESPONSE_INVALID = "AI_RESPONSE_INVALID",
    PROMPT_DIR_NOT_FOUND = "PROMPT_DIR_NOT_FOUND",
    PROMPT_FILE_TOO_LARGE = "PROMPT_FILE_TOO_LARGE",
    PROMPT_READ_FAILED = "PROMPT_READ_FAILED",
    PROMPT_INVALID_ENCODING = "PROMPT_INVALID_ENCODING",
    PROMPT_LOAD_FAILED = "PROMPT_LOAD_FAILED",
    CHANNEL_NOT_ALLOWED = "CHANNEL_NOT_ALLOWED",
    DUPLICATE_CHANNEL = "DUPLICATE_CHANNEL",
    INSUFFICIENT_PERMISSIONS = "INSUFFICIENT_PERMISSIONS",
    CHANNEL_NOT_FOUND = "CHANNEL_NOT_FOUND",
    DUPLICATE_CATEGORY = "DUPLICATE_CATEGORY",
    CATEGORY_NOT_FOUND = "CATEGORY_NOT_FOUND"
}
/**
 * Represents domain-level errors that can occur during business operations.
 * Used as the error type in Result<T, DomainError>.
 * Matches Java DomainError record.
 */
export declare class DomainError {
    readonly category: DomainErrorCategory;
    readonly message: string;
    readonly cause?: Error | undefined;
    constructor(category: DomainErrorCategory, message: string, cause?: Error | undefined);
    static invalidInput(message: string): DomainError;
    static insufficientBalance(message: string): DomainError;
    static insufficientTokens(message: string): DomainError;
    static persistenceFailure(message: string, cause?: Error): DomainError;
    static unexpectedFailure(message: string, cause?: Error): DomainError;
    static discordInteractionTimeout(message: string): DomainError;
    static discordHookExpired(message: string): DomainError;
    static discordUnknownMessage(message: string): DomainError;
    static discordRateLimited(message: string): DomainError;
    static discordMissingPermissions(message: string): DomainError;
    static discordInvalidComponentId(message: string): DomainError;
    static aiServiceTimeout(message: string): DomainError;
    static aiServiceAuthFailed(message: string): DomainError;
    static aiServiceRateLimited(message: string): DomainError;
    static aiServiceUnavailable(message: string): DomainError;
    static aiResponseEmpty(message: string): DomainError;
    static aiResponseInvalid(message: string): DomainError;
    static promptDirNotFound(message: string): DomainError;
    static promptFileTooLarge(message: string): DomainError;
    static promptReadFailed(message: string, cause?: Error): DomainError;
    static promptInvalidEncoding(message: string): DomainError;
    static promptLoadFailed(message: string, cause?: Error): DomainError;
    static channelNotAllowed(message: string): DomainError;
    static duplicateChannel(message: string): DomainError;
    static insufficientPermissions(message: string): DomainError;
    static channelNotFound(message: string): DomainError;
    static duplicateCategory(message: string): DomainError;
    static categoryNotFound(message: string): DomainError;
}
