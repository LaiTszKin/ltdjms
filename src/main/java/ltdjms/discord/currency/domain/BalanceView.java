package ltdjms.discord.currency.domain;

/**
 * Data transfer object representing a member's balance with currency formatting information. Used
 * to format balance responses in Discord messages.
 */
public record BalanceView(
    long guildId, long userId, long balance, String currencyName, String currencyIcon) {
  /**
   * Formats the balance as a display string with icon and name. Example: "💰 100 Gold"
   *
   * @return the formatted balance string
   */
  public String formatDisplay() {
    return String.format("%s %,d %s", currencyIcon, balance, currencyName);
  }

  /**
   * Formats the balance as a Discord message. Example: "Your balance: 💰 100 Gold"
   *
   * @return the formatted message
   */
  public String formatMessage() {
    return String.format("Your balance: %s", formatDisplay());
  }
}
