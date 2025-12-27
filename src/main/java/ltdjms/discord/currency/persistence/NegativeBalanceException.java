package ltdjms.discord.currency.persistence;

/** Exception thrown when an operation would result in a negative balance. */
public class NegativeBalanceException extends RuntimeException {

  public NegativeBalanceException(String message) {
    super(message);
  }

  public NegativeBalanceException(String message, Throwable cause) {
    super(message, cause);
  }
}
