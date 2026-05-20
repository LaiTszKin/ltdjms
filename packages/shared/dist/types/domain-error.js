/**
 * Error categories for classifying different types of domain errors.
 * Matches Java DomainError.Category exactly.
 */
export var DomainErrorCategory;
(function (DomainErrorCategory) {
    DomainErrorCategory["INVALID_INPUT"] = "INVALID_INPUT";
    DomainErrorCategory["INSUFFICIENT_BALANCE"] = "INSUFFICIENT_BALANCE";
    DomainErrorCategory["INSUFFICIENT_TOKENS"] = "INSUFFICIENT_TOKENS";
    DomainErrorCategory["PERSISTENCE_FAILURE"] = "PERSISTENCE_FAILURE";
    DomainErrorCategory["UNEXPECTED_FAILURE"] = "UNEXPECTED_FAILURE";
    DomainErrorCategory["DISCORD_INTERACTION_TIMEOUT"] = "DISCORD_INTERACTION_TIMEOUT";
    DomainErrorCategory["DISCORD_HOOK_EXPIRED"] = "DISCORD_HOOK_EXPIRED";
    DomainErrorCategory["DISCORD_UNKNOWN_MESSAGE"] = "DISCORD_UNKNOWN_MESSAGE";
    DomainErrorCategory["DISCORD_RATE_LIMITED"] = "DISCORD_RATE_LIMITED";
    DomainErrorCategory["DISCORD_MISSING_PERMISSIONS"] = "DISCORD_MISSING_PERMISSIONS";
    DomainErrorCategory["DISCORD_INVALID_COMPONENT_ID"] = "DISCORD_INVALID_COMPONENT_ID";
    DomainErrorCategory["AI_SERVICE_TIMEOUT"] = "AI_SERVICE_TIMEOUT";
    DomainErrorCategory["AI_SERVICE_AUTH_FAILED"] = "AI_SERVICE_AUTH_FAILED";
    DomainErrorCategory["AI_SERVICE_RATE_LIMITED"] = "AI_SERVICE_RATE_LIMITED";
    DomainErrorCategory["AI_SERVICE_UNAVAILABLE"] = "AI_SERVICE_UNAVAILABLE";
    DomainErrorCategory["AI_RESPONSE_EMPTY"] = "AI_RESPONSE_EMPTY";
    DomainErrorCategory["AI_RESPONSE_INVALID"] = "AI_RESPONSE_INVALID";
    DomainErrorCategory["PROMPT_DIR_NOT_FOUND"] = "PROMPT_DIR_NOT_FOUND";
    DomainErrorCategory["PROMPT_FILE_TOO_LARGE"] = "PROMPT_FILE_TOO_LARGE";
    DomainErrorCategory["PROMPT_READ_FAILED"] = "PROMPT_READ_FAILED";
    DomainErrorCategory["PROMPT_INVALID_ENCODING"] = "PROMPT_INVALID_ENCODING";
    DomainErrorCategory["PROMPT_LOAD_FAILED"] = "PROMPT_LOAD_FAILED";
    DomainErrorCategory["CHANNEL_NOT_ALLOWED"] = "CHANNEL_NOT_ALLOWED";
    DomainErrorCategory["DUPLICATE_CHANNEL"] = "DUPLICATE_CHANNEL";
    DomainErrorCategory["INSUFFICIENT_PERMISSIONS"] = "INSUFFICIENT_PERMISSIONS";
    DomainErrorCategory["CHANNEL_NOT_FOUND"] = "CHANNEL_NOT_FOUND";
    DomainErrorCategory["DUPLICATE_CATEGORY"] = "DUPLICATE_CATEGORY";
    DomainErrorCategory["CATEGORY_NOT_FOUND"] = "CATEGORY_NOT_FOUND";
})(DomainErrorCategory || (DomainErrorCategory = {}));
/**
 * Represents domain-level errors that can occur during business operations.
 * Used as the error type in Result<T, DomainError>.
 * Matches Java DomainError record.
 */
export class DomainError {
    category;
    message;
    cause;
    constructor(category, message, cause) {
        this.category = category;
        this.message = message;
        this.cause = cause;
        if (!category) {
            throw new Error('category must not be null');
        }
        if (!message) {
            throw new Error('message must not be null');
        }
    }
    // ---- Static factory methods ----
    static invalidInput(message) {
        return new DomainError(DomainErrorCategory.INVALID_INPUT, message);
    }
    static insufficientBalance(message) {
        return new DomainError(DomainErrorCategory.INSUFFICIENT_BALANCE, message);
    }
    static insufficientTokens(message) {
        return new DomainError(DomainErrorCategory.INSUFFICIENT_TOKENS, message);
    }
    static persistenceFailure(message, cause) {
        return new DomainError(DomainErrorCategory.PERSISTENCE_FAILURE, message, cause);
    }
    static unexpectedFailure(message, cause) {
        return new DomainError(DomainErrorCategory.UNEXPECTED_FAILURE, message, cause);
    }
    static discordInteractionTimeout(message) {
        return new DomainError(DomainErrorCategory.DISCORD_INTERACTION_TIMEOUT, message);
    }
    static discordHookExpired(message) {
        return new DomainError(DomainErrorCategory.DISCORD_HOOK_EXPIRED, message);
    }
    static discordUnknownMessage(message) {
        return new DomainError(DomainErrorCategory.DISCORD_UNKNOWN_MESSAGE, message);
    }
    static discordRateLimited(message) {
        return new DomainError(DomainErrorCategory.DISCORD_RATE_LIMITED, message);
    }
    static discordMissingPermissions(message) {
        return new DomainError(DomainErrorCategory.DISCORD_MISSING_PERMISSIONS, message);
    }
    static discordInvalidComponentId(message) {
        return new DomainError(DomainErrorCategory.DISCORD_INVALID_COMPONENT_ID, message);
    }
    static aiServiceTimeout(message) {
        return new DomainError(DomainErrorCategory.AI_SERVICE_TIMEOUT, message);
    }
    static aiServiceAuthFailed(message) {
        return new DomainError(DomainErrorCategory.AI_SERVICE_AUTH_FAILED, message);
    }
    static aiServiceRateLimited(message) {
        return new DomainError(DomainErrorCategory.AI_SERVICE_RATE_LIMITED, message);
    }
    static aiServiceUnavailable(message) {
        return new DomainError(DomainErrorCategory.AI_SERVICE_UNAVAILABLE, message);
    }
    static aiResponseEmpty(message) {
        return new DomainError(DomainErrorCategory.AI_RESPONSE_EMPTY, message);
    }
    static aiResponseInvalid(message) {
        return new DomainError(DomainErrorCategory.AI_RESPONSE_INVALID, message);
    }
    static promptDirNotFound(message) {
        return new DomainError(DomainErrorCategory.PROMPT_DIR_NOT_FOUND, message);
    }
    static promptFileTooLarge(message) {
        return new DomainError(DomainErrorCategory.PROMPT_FILE_TOO_LARGE, message);
    }
    static promptReadFailed(message, cause) {
        return new DomainError(DomainErrorCategory.PROMPT_READ_FAILED, message, cause);
    }
    static promptInvalidEncoding(message) {
        return new DomainError(DomainErrorCategory.PROMPT_INVALID_ENCODING, message);
    }
    static promptLoadFailed(message, cause) {
        return new DomainError(DomainErrorCategory.PROMPT_LOAD_FAILED, message, cause);
    }
    static channelNotAllowed(message) {
        return new DomainError(DomainErrorCategory.CHANNEL_NOT_ALLOWED, message);
    }
    static duplicateChannel(message) {
        return new DomainError(DomainErrorCategory.DUPLICATE_CHANNEL, message);
    }
    static insufficientPermissions(message) {
        return new DomainError(DomainErrorCategory.INSUFFICIENT_PERMISSIONS, message);
    }
    static channelNotFound(message) {
        return new DomainError(DomainErrorCategory.CHANNEL_NOT_FOUND, message);
    }
    static duplicateCategory(message) {
        return new DomainError(DomainErrorCategory.DUPLICATE_CATEGORY, message);
    }
    static categoryNotFound(message) {
        return new DomainError(DomainErrorCategory.CATEGORY_NOT_FOUND, message);
    }
}
//# sourceMappingURL=domain-error.js.map