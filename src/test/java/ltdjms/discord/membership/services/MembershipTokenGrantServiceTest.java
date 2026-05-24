package ltdjms.discord.membership.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.gametoken.domain.GameTokenTransaction;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.membership.persistence.MembershipTokenGrantRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

@ExtendWith(MockitoExtension.class)
class MembershipTokenGrantServiceTest {

  private static final long TEST_USER_ID = 123456789012345678L;
  private static final long TEST_GUILD_ID = 987654321098765432L;
  private static final Instant PERIOD_END = Instant.parse("2026-04-15T00:00:00+08:00");

  @Mock private MembershipTokenGrantRepository grantRepository;
  @Mock private MembershipSpendRepository spendRepository;
  @Mock private GameTokenService gameTokenService;
  @Mock private GameTokenTransactionService gameTokenTransactionService;

  private MembershipTokenGrantService service;

  @BeforeEach
  void setUp() {
    service =
        new MembershipTokenGrantService(
            grantRepository, spendRepository, gameTokenService, gameTokenTransactionService);
  }

  @Test
  @DisplayName("should skip grant for NONE tier")
  void shouldSkipNoneTier() {
    service.grantForSettlement(TEST_USER_ID, PERIOD_END, MembershipTier.NONE);

    verify(grantRepository, never()).hasGrantForPeriod(anyLong(), any());
    verify(gameTokenService, never()).tryAdjustTokens(anyLong(), anyLong(), anyLong());
  }

  @Test
  @DisplayName("should grant GOLD tokens once per settlement period")
  void shouldGrantGoldTokensIdempotently() {
    when(grantRepository.hasGrantForPeriod(TEST_USER_ID, PERIOD_END)).thenReturn(false, true);
    when(spendRepository.findMostRecentGuildId(TEST_USER_ID)).thenReturn(Optional.of(TEST_GUILD_ID));
    when(gameTokenService.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 200))
        .thenReturn(
            Result.ok(new GameTokenService.TokenAdjustmentResult(TEST_GUILD_ID, TEST_USER_ID, 0, 200, 200)));

    service.grantForSettlement(TEST_USER_ID, PERIOD_END, MembershipTier.GOLD);
    service.grantForSettlement(TEST_USER_ID, PERIOD_END, MembershipTier.GOLD);

    verify(gameTokenService, times(1)).tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 200);
    verify(gameTokenTransactionService)
        .recordTransaction(
            eq(TEST_GUILD_ID),
            eq(TEST_USER_ID),
            eq(200L),
            eq(200L),
            eq(GameTokenTransaction.Source.MEMBERSHIP_GRANT),
            eq("會員結算贈幣 (GOLD)"));
    verify(grantRepository).insertGrantLog(TEST_USER_ID, PERIOD_END, MembershipTier.GOLD, 200);
  }

  @Test
  @DisplayName("should log and skip when token adjustment fails")
  void shouldSkipWhenAdjustmentFails() {
    when(grantRepository.hasGrantForPeriod(TEST_USER_ID, PERIOD_END)).thenReturn(false);
    when(spendRepository.findMostRecentGuildId(TEST_USER_ID)).thenReturn(Optional.of(TEST_GUILD_ID));
    when(gameTokenService.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 200))
        .thenReturn(Result.err(DomainError.persistenceFailure("db down", null)));

    service.grantForSettlement(TEST_USER_ID, PERIOD_END, MembershipTier.GOLD);

    verify(gameTokenTransactionService, never())
        .recordTransaction(anyLong(), anyLong(), anyLong(), anyLong(), any(), any());
    verify(grantRepository, never()).insertGrantLog(anyLong(), any(), any(), anyInt());
  }
}
