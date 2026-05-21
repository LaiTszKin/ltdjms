/**
 * Error categories for classifying different types of domain errors.
 * Matches Java DomainError.Category exactly.
 */
export enum DomainErrorCategory {
  INVALID_INPUT = 'INVALID_INPUT',
  INSUFFICIENT_BALANCE = 'INSUFFICIENT_BALANCE',
  INSUFFICIENT_TOKENS = 'INSUFFICIENT_TOKENS',
  PERSISTENCE_FAILURE = 'PERSISTENCE_FAILURE',
  UNEXPECTED_FAILURE = 'UNEXPECTED_FAILURE',
  DISCORD_INTERACTION_TIMEOUT = 'DISCORD_INTERACTION_TIMEOUT',
  DISCORD_HOOK_EXPIRED = 'DISCORD_HOOK_EXPIRED',
  DISCORD_UNKNOWN_MESSAGE = 'DISCORD_UNKNOWN_MESSAGE',
  DISCORD_RATE_LIMITED = 'DISCORD_RATE_LIMITED',
  DISCORD_MISSING_PERMISSIONS = 'DISCORD_MISSING_PERMISSIONS',
  DISCORD_INVALID_COMPONENT_ID = 'DISCORD_INVALID_COMPONENT_ID',
  AI_SERVICE_TIMEOUT = 'AI_SERVICE_TIMEOUT',
  AI_SERVICE_AUTH_FAILED = 'AI_SERVICE_AUTH_FAILED',
  AI_SERVICE_RATE_LIMITED = 'AI_SERVICE_RATE_LIMITED',
  AI_SERVICE_UNAVAILABLE = 'AI_SERVICE_UNAVAILABLE',
  AI_RESPONSE_EMPTY = 'AI_RESPONSE_EMPTY',
  AI_RESPONSE_INVALID = 'AI_RESPONSE_INVALID',
  PROMPT_DIR_NOT_FOUND = 'PROMPT_DIR_NOT_FOUND',
  PROMPT_FILE_TOO_LARGE = 'PROMPT_FILE_TOO_LARGE',
  PROMPT_READ_FAILED = 'PROMPT_READ_FAILED',
  PROMPT_INVALID_ENCODING = 'PROMPT_INVALID_ENCODING',
  PROMPT_LOAD_FAILED = 'PROMPT_LOAD_FAILED',
  CHANNEL_NOT_ALLOWED = 'CHANNEL_NOT_ALLOWED',
  DUPLICATE_CHANNEL = 'DUPLICATE_CHANNEL',
  INSUFFICIENT_PERMISSIONS = 'INSUFFICIENT_PERMISSIONS',
  CHANNEL_NOT_FOUND = 'CHANNEL_NOT_FOUND',
  DUPLICATE_CATEGORY = 'DUPLICATE_CATEGORY',
  CATEGORY_NOT_FOUND = 'CATEGORY_NOT_FOUND',
  REDEEM_CODE_USED = 'REDEEM_CODE_USED',
  REDEEM_CODE_EXPIRED = 'REDEEM_CODE_EXPIRED',
  REDEEM_CODE_INVALID = 'REDEEM_CODE_INVALID',
}

/**
 * Represents domain-level errors that can occur during business operations.
 * Used as the error type in Result<T, DomainError>.
 * Matches Java DomainError record.
 */
export class DomainError extends Error {
  constructor(
    readonly category: DomainErrorCategory,
    message: string,
    readonly cause?: Error,
  ) {
    // super() MUST be called before any class-level 'this' access in TS strict mode;
    // null checks on constructor parameters are unnecessary because strict TS
    // compilation already prevents null/undefined assignment for typed parameters.
    super(message);
    this.name = 'DomainError';
  }

  // ---- Static factory methods ----

  static invalidInput(message: string): DomainError {
    return new DomainError(DomainErrorCategory.INVALID_INPUT, message);
  }

  static insufficientBalance(message: string): DomainError {
    return new DomainError(DomainErrorCategory.INSUFFICIENT_BALANCE, message);
  }

  static insufficientTokens(message: string): DomainError {
    return new DomainError(DomainErrorCategory.INSUFFICIENT_TOKENS, message);
  }

  static persistenceFailure(message: string, cause?: Error): DomainError {
    return new DomainError(DomainErrorCategory.PERSISTENCE_FAILURE, message, cause);
  }

  static unexpectedFailure(message: string, cause?: Error): DomainError {
    return new DomainError(DomainErrorCategory.UNEXPECTED_FAILURE, message, cause);
  }

  static discordInteractionTimeout(message: string): DomainError {
    return new DomainError(DomainErrorCategory.DISCORD_INTERACTION_TIMEOUT, message);
  }

  static discordHookExpired(message: string): DomainError {
    return new DomainError(DomainErrorCategory.DISCORD_HOOK_EXPIRED, message);
  }

  static discordUnknownMessage(message: string): DomainError {
    return new DomainError(DomainErrorCategory.DISCORD_UNKNOWN_MESSAGE, message);
  }

  static discordRateLimited(message: string): DomainError {
    return new DomainError(DomainErrorCategory.DISCORD_RATE_LIMITED, message);
  }

  static discordMissingPermissions(message: string): DomainError {
    return new DomainError(DomainErrorCategory.DISCORD_MISSING_PERMISSIONS, message);
  }

  static discordInvalidComponentId(message: string): DomainError {
    return new DomainError(DomainErrorCategory.DISCORD_INVALID_COMPONENT_ID, message);
  }

  static aiServiceTimeout(message: string): DomainError {
    return new DomainError(DomainErrorCategory.AI_SERVICE_TIMEOUT, message);
  }

  static aiServiceAuthFailed(message: string): DomainError {
    return new DomainError(DomainErrorCategory.AI_SERVICE_AUTH_FAILED, message);
  }

  static aiServiceRateLimited(message: string): DomainError {
    return new DomainError(DomainErrorCategory.AI_SERVICE_RATE_LIMITED, message);
  }

  static aiServiceUnavailable(message: string): DomainError {
    return new DomainError(DomainErrorCategory.AI_SERVICE_UNAVAILABLE, message);
  }

  static aiResponseEmpty(message: string): DomainError {
    return new DomainError(DomainErrorCategory.AI_RESPONSE_EMPTY, message);
  }

  static aiResponseInvalid(message: string): DomainError {
    return new DomainError(DomainErrorCategory.AI_RESPONSE_INVALID, message);
  }

  static promptDirNotFound(message: string): DomainError {
    return new DomainError(DomainErrorCategory.PROMPT_DIR_NOT_FOUND, message);
  }

  static promptFileTooLarge(message: string): DomainError {
    return new DomainError(DomainErrorCategory.PROMPT_FILE_TOO_LARGE, message);
  }

  static promptReadFailed(message: string, cause?: Error): DomainError {
    return new DomainError(DomainErrorCategory.PROMPT_READ_FAILED, message, cause);
  }

  static promptInvalidEncoding(message: string): DomainError {
    return new DomainError(DomainErrorCategory.PROMPT_INVALID_ENCODING, message);
  }

  static promptLoadFailed(message: string, cause?: Error): DomainError {
    return new DomainError(DomainErrorCategory.PROMPT_LOAD_FAILED, message, cause);
  }

  static channelNotAllowed(message: string): DomainError {
    return new DomainError(DomainErrorCategory.CHANNEL_NOT_ALLOWED, message);
  }

  static duplicateChannel(message: string): DomainError {
    return new DomainError(DomainErrorCategory.DUPLICATE_CHANNEL, message);
  }

  static insufficientPermissions(message: string): DomainError {
    return new DomainError(DomainErrorCategory.INSUFFICIENT_PERMISSIONS, message);
  }

  static channelNotFound(message: string): DomainError {
    return new DomainError(DomainErrorCategory.CHANNEL_NOT_FOUND, message);
  }

  static duplicateCategory(message: string): DomainError {
    return new DomainError(DomainErrorCategory.DUPLICATE_CATEGORY, message);
  }

  static categoryNotFound(message: string): DomainError {
    return new DomainError(DomainErrorCategory.CATEGORY_NOT_FOUND, message);
  }

  static redeemCodeUsed(message: string): DomainError {
    return new DomainError(DomainErrorCategory.REDEEM_CODE_USED, message);
  }

  static redeemCodeExpired(message: string): DomainError {
    return new DomainError(DomainErrorCategory.REDEEM_CODE_EXPIRED, message);
  }

  static redeemCodeInvalid(message: string): DomainError {
    return new DomainError(DomainErrorCategory.REDEEM_CODE_INVALID, message);
  }
}
