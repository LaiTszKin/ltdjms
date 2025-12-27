package ltdjms.discord.currency.persistence;

/** Exception thrown when a repository operation fails. */
public class RepositoryException extends RuntimeException {

  public RepositoryException(String message) {
    super(message);
  }

  public RepositoryException(String message, Throwable cause) {
    super(message, cause);
  }
}
