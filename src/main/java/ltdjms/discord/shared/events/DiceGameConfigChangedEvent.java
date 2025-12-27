package ltdjms.discord.shared.events;

/**
 * Event fired when a guild's dice game configuration changes.
 *
 * @param guildId the Discord guild ID
 * @param gameType the type of dice game that was changed
 */
public record DiceGameConfigChangedEvent(long guildId, GameType gameType) implements DomainEvent {

  /** The type of dice game. */
  public enum GameType {
    DICE_GAME_1,
    DICE_GAME_2
  }
}
