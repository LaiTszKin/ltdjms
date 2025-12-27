package ltdjms.discord.gametoken.persistence;

/** Exception thrown when an operation would result in negative tokens. */
public class InsufficientTokensException extends RuntimeException {

  public InsufficientTokensException(String message) {
    super(message);
  }

  public InsufficientTokensException(String message, Throwable cause) {
    super(message, cause);
  }
}
