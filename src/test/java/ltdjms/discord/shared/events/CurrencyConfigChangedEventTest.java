package ltdjms.discord.shared.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyConfigChangedEventTest {

    @Test
    void shouldCreateEventWithAllFields() {
        // Given
        long guildId = 123456789L;
        String currencyName = "金幣";
        String currencyIcon = "💰";

        // When
        CurrencyConfigChangedEvent event = new CurrencyConfigChangedEvent(guildId, currencyName, currencyIcon);

        // Then
        assertThat(event.guildId()).isEqualTo(guildId);
        assertThat(event.currencyName()).isEqualTo(currencyName);
        assertThat(event.currencyIcon()).isEqualTo(currencyIcon);
    }

    @Test
    void shouldImplementDomainEvent() {
        // Given
        CurrencyConfigChangedEvent event = new CurrencyConfigChangedEvent(123L, "金幣", "💰");

        // Then
        assertThat(event).isInstanceOf(DomainEvent.class);
        assertThat(event.guildId()).isEqualTo(123L);
    }
}
