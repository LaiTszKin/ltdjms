package ltdjms.discord.currency.services;

import ltdjms.discord.currency.domain.GuildCurrencyConfig;
import ltdjms.discord.currency.persistence.GuildCurrencyConfigRepository;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.CurrencyConfigChangedEvent;
import ltdjms.discord.shared.events.DomainEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CurrencyConfigServiceEventTest {

    private GuildCurrencyConfigRepository configRepository;
    private EmojiValidator emojiValidator;
    private DomainEventPublisher eventPublisher;
    private CurrencyConfigService service;

    @BeforeEach
    void setUp() {
        configRepository = mock(GuildCurrencyConfigRepository.class);
        emojiValidator = mock(EmojiValidator.class);
        eventPublisher = mock(DomainEventPublisher.class);
        service = new CurrencyConfigService(configRepository, emojiValidator, eventPublisher);
    }

    @Test
    void tryUpdateConfig_shouldPublishEventOnSuccess() {
        // Given
        long guildId = 123L;
        String newName = "金幣";
        String newIcon = "💰";

        GuildCurrencyConfig existingConfig = GuildCurrencyConfig.createDefault(guildId);
        GuildCurrencyConfig updatedConfig = existingConfig.withUpdates(newName, newIcon);

        when(configRepository.findByGuildId(guildId)).thenReturn(Optional.of(existingConfig));
        when(configRepository.saveOrUpdate(any())).thenReturn(updatedConfig);

        // When
        Result<GuildCurrencyConfig, ?> result = service.tryUpdateConfig(guildId, newName, newIcon);

        // Then
        assertThat(result.isOk()).isTrue();

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        DomainEvent event = eventCaptor.getValue();
        assertThat(event).isInstanceOf(CurrencyConfigChangedEvent.class);
        CurrencyConfigChangedEvent configEvent = (CurrencyConfigChangedEvent) event;
        assertThat(configEvent.guildId()).isEqualTo(guildId);
        assertThat(configEvent.currencyName()).isEqualTo(newName);
        assertThat(configEvent.currencyIcon()).isEqualTo(newIcon);
    }

    @Test
    void tryUpdateConfig_shouldNotPublishEventOnValidationFailure() {
        // Given
        long guildId = 123L;
        String invalidName = "";
        String icon = "💰";

        // When
        Result<GuildCurrencyConfig, ?> result = service.tryUpdateConfig(guildId, invalidName, icon);

        // Then
        assertThat(result.isErr()).isTrue();
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void tryUpdateConfig_shouldUpdatePartialFields() {
        // Given
        long guildId = 123L;
        String newName = "新貨幣";

        GuildCurrencyConfig existingConfig = GuildCurrencyConfig.createDefault(guildId);
        GuildCurrencyConfig updatedConfig = existingConfig.withUpdates(newName, null);

        when(configRepository.findByGuildId(guildId)).thenReturn(Optional.of(existingConfig));
        when(configRepository.saveOrUpdate(any())).thenReturn(updatedConfig);

        // When
        Result<GuildCurrencyConfig, ?> result = service.tryUpdateConfig(guildId, newName, null);

        // Then
        assertThat(result.isOk()).isTrue();

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        CurrencyConfigChangedEvent event = (CurrencyConfigChangedEvent) eventCaptor.getValue();
        assertThat(event.guildId()).isEqualTo(guildId);
        assertThat(event.currencyName()).isEqualTo(newName);
        // Icon should be the default value since we only updated name
        assertThat(event.currencyIcon()).isEqualTo(existingConfig.currencyIcon());
    }
}
