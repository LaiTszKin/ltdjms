package ltdjms.discord.shared;

/**
 * Thrown when automatic schema migration detects a destructive change that cannot be applied
 * safely.
 */
public class SchemaMigrationException extends RuntimeException {

  public SchemaMigrationException(String message) {
    super(message);
  }

  public SchemaMigrationException(String message, Throwable cause) {
    super(message, cause);
  }
}
