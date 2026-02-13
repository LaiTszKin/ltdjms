package ltdjms.discord.dispatch.persistence;

/** 派單模組的資料存取例外。 */
public class RepositoryException extends RuntimeException {

  public RepositoryException(String message) {
    super(message);
  }

  public RepositoryException(String message, Throwable cause) {
    super(message, cause);
  }
}
