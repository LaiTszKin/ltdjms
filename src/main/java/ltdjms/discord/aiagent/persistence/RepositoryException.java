package ltdjms.discord.aiagent.persistence;

/** Repository 操作失敗時拋出的例外。 */
public class RepositoryException extends RuntimeException {

  public RepositoryException(String message) {
    super(message);
  }

  public RepositoryException(String message, Throwable cause) {
    super(message, cause);
  }
}
