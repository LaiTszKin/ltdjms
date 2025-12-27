package ltdjms.discord.shared.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiceGameConfigChangedEventTest {

  @Test
  void shouldCreateEventWithDiceGame1Type() {
    // Given
    long guildId = 123456789L;
    DiceGameConfigChangedEvent.GameType gameType = DiceGameConfigChangedEvent.GameType.DICE_GAME_1;

    // When
    DiceGameConfigChangedEvent event = new DiceGameConfigChangedEvent(guildId, gameType);

    // Then
    assertThat(event.guildId()).isEqualTo(guildId);
    assertThat(event.gameType()).isEqualTo(gameType);
  }

  @Test
  void shouldCreateEventWithDiceGame2Type() {
    // Given
    long guildId = 987654321L;
    DiceGameConfigChangedEvent.GameType gameType = DiceGameConfigChangedEvent.GameType.DICE_GAME_2;

    // When
    DiceGameConfigChangedEvent event = new DiceGameConfigChangedEvent(guildId, gameType);

    // Then
    assertThat(event.guildId()).isEqualTo(guildId);
    assertThat(event.gameType()).isEqualTo(gameType);
  }

  @Test
  void shouldImplementDomainEvent() {
    // Given
    DiceGameConfigChangedEvent event =
        new DiceGameConfigChangedEvent(123L, DiceGameConfigChangedEvent.GameType.DICE_GAME_1);

    // Then
    assertThat(event).isInstanceOf(DomainEvent.class);
    assertThat(event.guildId()).isEqualTo(123L);
  }
}
