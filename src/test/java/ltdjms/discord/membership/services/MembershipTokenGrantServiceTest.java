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
import ltdjms.discord.membership.persistence.GrantClaimState;
import ltdjms.discord.membership.persistence.MembershipRepository;
import ltdjms.discord.membership.persistence.MembershipSpendRepository;
import ltdjms.discord.membership.persistence.MembershipTokenGrantRepository;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

@ExtendWith(MockitoExtension.class)
class MembershipTokenGrantServiceTest {

  private static final long TEST_USER_ID = 123456789012345678L;
  private static final long TEST_GUILD_ID = 987654321098765432L;
  private static final Instant PERIOD_START = Instant.parse("2026-03-15T00:00:00+08:00");
  private static final Instant PERIOD_END = Instant.parse("2026-04-15T00:00:00+08:00");

  @Mock private MembershipTokenGrantRepository grantRepository;
  @Mock private MembershipSpendRepository spendRepository;
  @Mock private MembershipRepository membershipRepository;
  @Mock private GameTokenService gameTokenService;
  @Mock private GameTokenTransactionService gameTokenTransactionService;

  private MembershipTokenGrantService service;

  @BeforeEach
  void setUp() {
    service =
        new MembershipTokenGrantService(
            grantRepository,
            spendRepository,
            membershipRepository,
            gameTokenService,
            gameTokenTransactionService);
  }

  @Test
  @DisplayName("should skip grant for NONE tier")
  void shouldSkipNoneTier() {
    assertThat(
            service.grantForSettlement(
                TEST_USER_ID, PERIOD_START, PERIOD_END, MembershipTier.NONE))
        .isTrue();

    verify(grantRepository, never()).tryClaimGrantLog(anyLong(), any(), any(), anyInt());
    verify(gameTokenService, never()).tryAdjustTokens(anyLong(), anyLong(), anyLong());
  }

  @Test
  @DisplayName("should grant GOLD tokens once per settlement period")
  void shouldGrantGoldTokensIdempotently() {
    when(grantRepository.findClaimState(TEST_USER_ID, PERIOD_END))
        .thenReturn(
            Optional.empty(),
            Optional.of(new GrantClaimState("CLAIMED", false, false)),
            Optional.of(new GrantClaimState("COMPLETED", true, true)));
    when(grantRepository.tryClaimGrantLog(TEST_USER_ID, PERIOD_END, MembershipTier.GOLD, 200))
        .thenReturn(true);
    when(spendRepository.findPrimaryGuildInPeriod(TEST_USER_ID, PERIOD_START, PERIOD_END))
        .thenReturn(Optional.of(TEST_GUILD_ID));
    when(gameTokenService.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 200))
        .thenReturn(
            Result.ok(
                new GameTokenService.TokenAdjustmentResult(
                    TEST_GUILD_ID, TEST_USER_ID, 0, 200, 200)));

    assertThat(
            service.grantForSettlement(
                TEST_USER_ID, PERIOD_START, PERIOD_END, MembershipTier.GOLD))
        .isTrue();
    assertThat(
            service.grantForSettlement(
                TEST_USER_ID, PERIOD_START, PERIOD_END, MembershipTier.GOLD))
        .isTrue();

    verify(gameTokenService, times(1)).tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 200);
    verify(gameTokenTransactionService)
        .recordTransaction(
            eq(TEST_GUILD_ID),
            eq(TEST_USER_ID),
            eq(200L),
            eq(200L),
            eq(GameTokenTransaction.Source.MEMBERSHIP_GRANT),
            eq("會員結算贈幣 (GOLD)"));
    verify(grantRepository).markAuditRecorded(TEST_USER_ID, PERIOD_END);
    verify(grantRepository).completeGrantClaim(TEST_USER_ID, PERIOD_END);
  }

  @Test
  @DisplayName("should release claim and allow retry when token adjustment fails")
  void shouldReleaseClaimWhenAdjustmentFails() {
    when(grantRepository.findClaimState(TEST_USER_ID, PERIOD_END)).thenReturn(Optional.empty());
    when(grantRepository.tryClaimGrantLog(TEST_USER_ID, PERIOD_END, MembershipTier.GOLD, 200))
        .thenReturn(true);
    when(grantRepository.findClaimState(TEST_USER_ID, PERIOD_END))
        .thenReturn(Optional.empty(), Optional.of(new GrantClaimState("CLAIMED", false, false)));
    when(spendRepository.findPrimaryGuildInPeriod(TEST_USER_ID, PERIOD_START, PERIOD_END))
        .thenReturn(Optional.of(TEST_GUILD_ID));
    when(gameTokenService.tryAdjustTokens(TEST_GUILD_ID, TEST_USER_ID, 200))
        .thenReturn(Result.err(DomainError.persistenceFailure("db down", null)));

    assertThat(
            service.grantForSettlement(
                TEST_USER_ID, PERIOD_START, PERIOD_END, MembershipTier.GOLD))
        .isFalse();

    verify(gameTokenTransactionService, never())
        .recordTransaction(anyLong(), anyLong(), anyLong(), anyLong(), any(), any());
    verify(grantRepository).releaseGrantClaim(TEST_USER_ID, PERIOD_END);
  }

  @Test
  @DisplayName("should complete grant without duplicate audit when tokens already adjusted")
  void shouldSkipDuplicateAuditOnRetry() {
    when(grantRepository.findClaimState(TEST_USER_ID, PERIOD_END))
        .thenReturn(Optional.of(new GrantClaimState("FAILED", true, true)));

    assertThat(
            service.grantForSettlement(
                TEST_USER_ID, PERIOD_START, PERIOD_END, MembershipTier.GOLD))
        .isTrue();

    verify(gameTokenService, never()).tryAdjustTokens(anyLong(), anyLong(), anyLong());
    verify(gameTokenTransactionService, never())
        .recordTransaction(anyLong(), anyLong(), anyLong(), anyLong(), any(), any());
    verify(grantRepository).completeGrantClaim(TEST_USER_ID, PERIOD_END);
  }

  @Test
  @DisplayName("should mark skipped when no spend guild exists")
  void shouldMarkSkippedWhenNoGuild() {
    when(grantRepository.findClaimState(TEST_USER_ID, PERIOD_END))
        .thenReturn(Optional.empty(), Optional.of(new GrantClaimState("CLAIMED", false, false)));
    when(grantRepository.tryClaimGrantLog(TEST_USER_ID, PERIOD_END, MembershipTier.GOLD, 200))
        .thenReturn(true);
    when(spendRepository.findPrimaryGuildInPeriod(TEST_USER_ID, PERIOD_START, PERIOD_END))
        .thenReturn(Optional.empty());

    assertThat(
            service.grantForSettlement(
                TEST_USER_ID, PERIOD_START, PERIOD_END, MembershipTier.GOLD))
        .isTrue();

    verify(grantRepository).markSkippedNoGuild(TEST_USER_ID, PERIOD_END);
    verify(gameTokenService, never()).tryAdjustTokens(anyLong(), anyLong(), anyLong());
  }
}
