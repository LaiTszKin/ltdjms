package ltdjms.discord.panel.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ltdjms.discord.aichat.services.AIChannelRestrictionService;
import ltdjms.discord.currency.services.BalanceAdjustmentService;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyConfigService;
import ltdjms.discord.gametoken.domain.DiceGame1Config;
import ltdjms.discord.gametoken.domain.DiceGame2Config;
import ltdjms.discord.gametoken.persistence.DiceGame1ConfigRepository;
import ltdjms.discord.gametoken.persistence.DiceGame2ConfigRepository;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.events.DiceGameConfigChangedEvent;
import ltdjms.discord.shared.events.DomainEvent;
import ltdjms.discord.shared.events.DomainEventPublisher;

class AdminPanelServiceEventTest {

  private BalanceService balanceService;
  private BalanceAdjustmentService balanceAdjustmentService;
  private GameTokenService gameTokenService;
  private GameTokenTransactionService transactionService;
  private DiceGame1ConfigRepository diceGame1ConfigRepository;
  private DiceGame2ConfigRepository diceGame2ConfigRepository;
  private CurrencyConfigService currencyConfigService;
  private DomainEventPublisher eventPublisher;
  private AIChannelRestrictionService aiChannelRestrictionService;
  private AdminPanelService service;

  @BeforeEach
  void setUp() {
    balanceService = mock(BalanceService.class);
    balanceAdjustmentService = mock(BalanceAdjustmentService.class);
    gameTokenService = mock(GameTokenService.class);
    transactionService = mock(GameTokenTransactionService.class);
    diceGame1ConfigRepository = mock(DiceGame1ConfigRepository.class);
    diceGame2ConfigRepository = mock(DiceGame2ConfigRepository.class);
    currencyConfigService = mock(CurrencyConfigService.class);
    eventPublisher = mock(DomainEventPublisher.class);
    aiChannelRestrictionService = mock(AIChannelRestrictionService.class);

    service =
        new AdminPanelService(
            balanceService,
            balanceAdjustmentService,
            gameTokenService,
            transactionService,
            diceGame1ConfigRepository,
            diceGame2ConfigRepository,
            currencyConfigService,
            eventPublisher,
            aiChannelRestrictionService);
  }

  @Test
  void updateDiceGame1Config_shouldPublishEventOnSuccess() {
    // Given
    long guildId = 123L;
    Instant now = Instant.now();
    DiceGame1Config defaultConfig = new DiceGame1Config(guildId, 1L, 10L, 100L, now, now);
    DiceGame1Config updatedConfig = new DiceGame1Config(guildId, 5L, 20L, 150L, now, now);

    when(diceGame1ConfigRepository.findOrCreateDefault(guildId)).thenReturn(defaultConfig);
    when(diceGame1ConfigRepository.updateTokensPerPlayRange(anyLong(), anyLong(), anyLong()))
        .thenReturn(updatedConfig);
    when(diceGame1ConfigRepository.updateRewardPerDiceValue(anyLong(), anyLong()))
        .thenReturn(updatedConfig);

    // When
    Result<DiceGame1Config, ?> result = service.updateDiceGame1Config(guildId, 5L, 20L, 150L);

    // Then
    assertThat(result.isOk()).isTrue();

    ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisher).publish(eventCaptor.capture());

    DomainEvent event = eventCaptor.getValue();
    assertThat(event).isInstanceOf(DiceGameConfigChangedEvent.class);
    DiceGameConfigChangedEvent gameEvent = (DiceGameConfigChangedEvent) event;
    assertThat(gameEvent.guildId()).isEqualTo(guildId);
    assertThat(gameEvent.gameType()).isEqualTo(DiceGameConfigChangedEvent.GameType.DICE_GAME_1);
  }

  @Test
  void updateDiceGame2Config_shouldPublishEventOnSuccess() {
    // Given
    long guildId = 123L;
    Instant now = Instant.now();
    DiceGame2Config defaultConfig =
        new DiceGame2Config(guildId, 1L, 10L, 5L, 2L, 50L, 100L, now, now);
    DiceGame2Config updatedConfig =
        new DiceGame2Config(guildId, 5L, 20L, 10L, 3L, 75L, 150L, now, now);

    when(diceGame2ConfigRepository.findOrCreateDefault(guildId)).thenReturn(defaultConfig);
    when(diceGame2ConfigRepository.updateTokensPerPlayRange(anyLong(), anyLong(), anyLong()))
        .thenReturn(updatedConfig);
    when(diceGame2ConfigRepository.updateMultipliers(anyLong(), anyLong(), anyLong()))
        .thenReturn(updatedConfig);
    when(diceGame2ConfigRepository.updateTripleBonuses(anyLong(), anyLong(), anyLong()))
        .thenReturn(updatedConfig);

    // When
    Result<DiceGame2Config, ?> result =
        service.updateDiceGame2Config(guildId, 5L, 20L, 10L, 3L, 75L, 150L);

    // Then
    assertThat(result.isOk()).isTrue();

    ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisher).publish(eventCaptor.capture());

    DomainEvent event = eventCaptor.getValue();
    assertThat(event).isInstanceOf(DiceGameConfigChangedEvent.class);
    DiceGameConfigChangedEvent gameEvent = (DiceGameConfigChangedEvent) event;
    assertThat(gameEvent.guildId()).isEqualTo(guildId);
    assertThat(gameEvent.gameType()).isEqualTo(DiceGameConfigChangedEvent.GameType.DICE_GAME_2);
  }

  @Test
  void updateDiceGame1Config_shouldNotPublishEventOnFailure() {
    // Given
    long guildId = 123L;
    Instant now = Instant.now();
    DiceGame1Config defaultConfig = new DiceGame1Config(guildId, 1L, 10L, 100L, now, now);

    when(diceGame1ConfigRepository.findOrCreateDefault(guildId)).thenReturn(defaultConfig);
    when(diceGame1ConfigRepository.updateTokensPerPlayRange(anyLong(), anyLong(), anyLong()))
        .thenThrow(new IllegalArgumentException("Invalid range"));

    // When
    Result<DiceGame1Config, ?> result = service.updateDiceGame1Config(guildId, 20L, 5L, null);

    // Then
    assertThat(result.isErr()).isTrue();
    verify(eventPublisher, never()).publish(any());
  }
}
