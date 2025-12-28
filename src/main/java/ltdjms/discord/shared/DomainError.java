package ltdjms.discord.shared;

import java.util.Objects;

/**
 * Represents domain-level errors that can occur during business operations. This is used as the
 * error type in Result<T, DomainError>.
 */
public record DomainError(Category category, String message, Throwable cause) {

  /** Error categories for classifying different types of domain errors. */
  public enum Category {
    /** Invalid user input or command arguments */
    INVALID_INPUT,
    /** Account balance is insufficient for the operation */
    INSUFFICIENT_BALANCE,
    /** Game token count is insufficient for the operation */
    INSUFFICIENT_TOKENS,
    /** Database or persistence layer failure */
    PERSISTENCE_FAILURE,
    /** Unexpected system failure (bugs, external system issues) */
    UNEXPECTED_FAILURE,
    /** Discord API interaction timeout (3 second limit) */
    DISCORD_INTERACTION_TIMEOUT,
    /** Discord InteractionHook expired (15 minute limit) */
    DISCORD_HOOK_EXPIRED,
    /** Discord message unknown or deleted */
    DISCORD_UNKNOWN_MESSAGE,
    /** Discord API rate limit exceeded */
    DISCORD_RATE_LIMITED,
    /** Discord bot missing required permissions */
    DISCORD_MISSING_PERMISSIONS,
    /** Discord invalid component ID */
    DISCORD_INVALID_COMPONENT_ID,
    /** AI service timeout */
    AI_SERVICE_TIMEOUT,
    /** AI service authentication failed */
    AI_SERVICE_AUTH_FAILED,
    /** AI service rate limited */
    AI_SERVICE_RATE_LIMITED,
    /** AI service unavailable */
    AI_SERVICE_UNAVAILABLE,
    /** AI response empty */
    AI_RESPONSE_EMPTY,
    /** AI response invalid */
    AI_RESPONSE_INVALID,
    /** Prompts directory not found */
    PROMPT_DIR_NOT_FOUND,
    /** Prompt file exceeds size limit */
    PROMPT_FILE_TOO_LARGE,
    /** Prompt file read failed */
    PROMPT_READ_FAILED,
    /** Prompt file has invalid encoding */
    PROMPT_INVALID_ENCODING,
    /** Prompt loading failed */
    PROMPT_LOAD_FAILED
  }

  public DomainError {
    Objects.requireNonNull(category, "category must not be null");
    Objects.requireNonNull(message, "message must not be null");
  }

  /** Creates an invalid input error. */
  public static DomainError invalidInput(String message) {
    return new DomainError(Category.INVALID_INPUT, message, null);
  }

  /** Creates an insufficient balance error. */
  public static DomainError insufficientBalance(String message) {
    return new DomainError(Category.INSUFFICIENT_BALANCE, message, null);
  }

  /** Creates an insufficient tokens error. */
  public static DomainError insufficientTokens(String message) {
    return new DomainError(Category.INSUFFICIENT_TOKENS, message, null);
  }

  /** Creates a persistence failure error. */
  public static DomainError persistenceFailure(String message, Throwable cause) {
    return new DomainError(Category.PERSISTENCE_FAILURE, message, cause);
  }

  /** Creates an unexpected failure error. */
  public static DomainError unexpectedFailure(String message, Throwable cause) {
    return new DomainError(Category.UNEXPECTED_FAILURE, message, cause);
  }
}
