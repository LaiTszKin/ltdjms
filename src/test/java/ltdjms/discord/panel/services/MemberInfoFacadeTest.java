package ltdjms.discord.panel.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.services.MembershipQueryService;
import ltdjms.discord.product.domain.Product;
import ltdjms.discord.redemption.domain.RedemptionCode;
import ltdjms.discord.redemption.services.ProductRedemptionTransactionService;
import ltdjms.discord.redemption.services.RedemptionService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/** Unit tests for MemberInfoFacade. */
@ExtendWith(MockitoExtension.class)
class MemberInfoFacadeTest {

  private static final long TEST_GUILD_ID = 123456789012345678L;
  private static final long TEST_USER_ID = 987654321098765432L;

  @Mock private BalanceService balanceService;
  @Mock private GameTokenService gameTokenService;
  @Mock private GameTokenTransactionService gameTokenTransactionService;
  @Mock private CurrencyTransactionService currencyTransactionService;
  @Mock private RedemptionService redemptionService;
  @Mock private ProductRedemptionTransactionService productRedemptionTransactionService;
  @Mock private MembershipQueryService membershipQueryService;

  private MemberInfoFacade facade;

  @BeforeEach
  void setUp() {
    facade =
        new MemberInfoFacade(
            balanceService,
            gameTokenService,
            gameTokenTransactionService,
            currencyTransactionService,
            redemptionService,
            productRedemptionTransactionService,
            membershipQueryService);
  }

  @Nested
  @DisplayName("getUserPanelView")
  class GetUserPanelView {

    @Test
    @DisplayName("should return user panel view with balance and tokens")
    void shouldReturnUserPanelView() {
      BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 1000L, "Gold", "💰");
      Result<BalanceView, DomainError> balanceResult = Result.ok(balanceView);
      when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(balanceResult);
      when(gameTokenService.getBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(50L);
      when(membershipQueryService.getPanelSummary(TEST_USER_ID))
          .thenReturn(
              new MembershipQueryService.PanelSummary(
                  MembershipTier.NONE, 0L, 15_000L, null, BigDecimal.ZERO));

      Result<UserPanelView, DomainError> result =
          facade.getUserPanelView(TEST_GUILD_ID, TEST_USER_ID);

      assertThat(result.isOk()).isTrue();
      UserPanelView view = result.getValue();
      assertThat(view.guildId()).isEqualTo(TEST_GUILD_ID);
      assertThat(view.userId()).isEqualTo(TEST_USER_ID);
      assertThat(view.currencyBalance()).isEqualTo(1000L);
      assertThat(view.currencyName()).isEqualTo("Gold");
      assertThat(view.currencyIcon()).isEqualTo("💰");
      assertThat(view.gameTokens()).isEqualTo(50L);
      assertThat(view.membershipSummary().tier()).isEqualTo(MembershipTier.NONE);
      verify(balanceService).tryGetBalance(TEST_GUILD_ID, TEST_USER_ID);
      verify(gameTokenService).getBalance(TEST_GUILD_ID, TEST_USER_ID);
    }

    @Test
    @DisplayName("should return error when balance service fails")
    void shouldReturnErrorWhenBalanceServiceFails() {
      DomainError error = DomainError.persistenceFailure("Database error", null);
      Result<BalanceView, DomainError> balanceResult = Result.err(error);
      when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(balanceResult);

      Result<UserPanelView, DomainError> result =
          facade.getUserPanelView(TEST_GUILD_ID, TEST_USER_ID);

      assertThat(result.isErr()).isTrue();
      assertThat(result.getError().category()).isEqualTo(DomainError.Category.PERSISTENCE_FAILURE);
      verify(balanceService).tryGetBalance(TEST_GUILD_ID, TEST_USER_ID);
      verify(gameTokenService, never()).getBalance(anyLong(), anyLong());
    }

    @Test
    @DisplayName("should return zero tokens when account does not exist")
    void shouldReturnZeroTokensWhenAccountDoesNotExist() {
      BalanceView balanceView = new BalanceView(TEST_GUILD_ID, TEST_USER_ID, 500L, "Coins", "🪙");
      Result<BalanceView, DomainError> balanceResult = Result.ok(balanceView);
      when(balanceService.tryGetBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(balanceResult);
      when(gameTokenService.getBalance(TEST_GUILD_ID, TEST_USER_ID)).thenReturn(0L);
      when(membershipQueryService.getPanelSummary(TEST_USER_ID))
          .thenReturn(
              new MembershipQueryService.PanelSummary(
                  MembershipTier.NONE, 0L, 15_000L, null, BigDecimal.ZERO));

      Result<UserPanelView, DomainError> result =
          facade.getUserPanelView(TEST_GUILD_ID, TEST_USER_ID);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().gameTokens()).isEqualTo(0L);
    }
  }

  @Nested
  @DisplayName("getMembershipSummary")
  class GetMembershipSummary {

    @Test
    @DisplayName("should compute period spend and next tier threshold")
    void shouldComputeMembershipSummary() {
      Instant nextSettlement = Instant.parse("2026-05-15T00:00:00+08:00");
      when(membershipQueryService.getPanelSummary(TEST_USER_ID))
          .thenReturn(
              new MembershipQueryService.PanelSummary(
                  MembershipTier.SILVER,
                  20_000L,
                  MembershipTier.GOLD.thresholdListPriceTwd(),
                  nextSettlement,
                  MembershipTier.SILVER.discountRate()));

      MembershipPanelSummary summary = facade.getMembershipSummary(TEST_USER_ID);

      assertThat(summary.tier()).isEqualTo(MembershipTier.SILVER);
      assertThat(summary.periodSpendListPriceM()).isEqualTo(20_000L);
      assertThat(summary.nextTierThresholdM())
          .isEqualTo(MembershipTier.GOLD.thresholdListPriceTwd());
      assertThat(summary.nextSettlementAt()).isEqualTo(nextSettlement);
    }
  }

  @Nested
  @DisplayName("getTokenTransactionPage")
  class GetTokenTransactionPage {

    @Test
    @DisplayName("should return token transaction page from service")
    void shouldReturnTokenTransactionPage() {
      GameTokenTransactionService.TransactionPage expectedPage =
          new GameTokenTransactionService.TransactionPage(Collections.emptyList(), 1, 1, 0, 10);
      when(gameTokenTransactionService.getTransactionPage(
              TEST_GUILD_ID, TEST_USER_ID, 1, GameTokenTransactionService.DEFAULT_PAGE_SIZE))
          .thenReturn(expectedPage);

      GameTokenTransactionService.TransactionPage result =
          facade.getTokenTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 1);

      assertThat(result).isEqualTo(expectedPage);
      verify(gameTokenTransactionService)
          .getTransactionPage(
              TEST_GUILD_ID, TEST_USER_ID, 1, GameTokenTransactionService.DEFAULT_PAGE_SIZE);
    }
  }

  @Nested
  @DisplayName("getCurrencyTransactionPage")
  class GetCurrencyTransactionPage {

    @Test
    @DisplayName("should return currency transaction page from service")
    void shouldReturnCurrencyTransactionPage() {
      CurrencyTransactionService.TransactionPage expectedPage =
          new CurrencyTransactionService.TransactionPage(Collections.emptyList(), 1, 1, 0, 10);
      when(currencyTransactionService.getTransactionPage(
              TEST_GUILD_ID, TEST_USER_ID, 2, CurrencyTransactionService.DEFAULT_PAGE_SIZE))
          .thenReturn(expectedPage);

      CurrencyTransactionService.TransactionPage result =
          facade.getCurrencyTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 2);

      assertThat(result).isEqualTo(expectedPage);
      verify(currencyTransactionService)
          .getTransactionPage(
              TEST_GUILD_ID, TEST_USER_ID, 2, CurrencyTransactionService.DEFAULT_PAGE_SIZE);
    }
  }

  @Nested
  @DisplayName("redeemCode")
  class RedeemCode {

    @Test
    @DisplayName("should redeem code through redemption service")
    void shouldRedeemCode() {
      String codeStr = "TEST1234";
      RedemptionCode code = RedemptionCode.create(codeStr, 1L, TEST_GUILD_ID, null);
      Product product =
          new Product(
              1L,
              TEST_GUILD_ID,
              "VIP 會員",
              "Premium membership",
              Product.RewardType.CURRENCY,
              1000L,
              null,
              java.time.Instant.now(),
              java.time.Instant.now());
      RedemptionService.RedemptionResult expectedResult =
          new RedemptionService.RedemptionResult(code, product, 1000L);
      Result<RedemptionService.RedemptionResult, DomainError> redeemResult =
          Result.ok(expectedResult);
      when(redemptionService.redeemCode(codeStr, TEST_GUILD_ID, TEST_USER_ID))
          .thenReturn(redeemResult);

      Result<RedemptionService.RedemptionResult, DomainError> result =
          facade.redeemCode(codeStr, TEST_GUILD_ID, TEST_USER_ID);

      assertThat(result.isOk()).isTrue();
      assertThat(result.getValue().product().name()).isEqualTo("VIP 會員");
      assertThat(result.getValue().rewardedAmount()).isEqualTo(1000L);
      verify(redemptionService).redeemCode(codeStr, TEST_GUILD_ID, TEST_USER_ID);
    }
  }

  @Nested
  @DisplayName("getProductRedemptionTransactionPage")
  class GetProductRedemptionTransactionPage {

    @Test
    @DisplayName("should return product redemption transaction page from service")
    void shouldReturnProductRedemptionTransactionPage() {
      ProductRedemptionTransactionService.TransactionPage expectedPage =
          new ProductRedemptionTransactionService.TransactionPage(
              Collections.emptyList(), 1, 1, 0, 10);
      when(productRedemptionTransactionService.getTransactionPage(
              TEST_GUILD_ID,
              TEST_USER_ID,
              3,
              ProductRedemptionTransactionService.DEFAULT_PAGE_SIZE))
          .thenReturn(expectedPage);

      ProductRedemptionTransactionService.TransactionPage result =
          facade.getProductRedemptionTransactionPage(TEST_GUILD_ID, TEST_USER_ID, 3);

      assertThat(result).isEqualTo(expectedPage);
      verify(productRedemptionTransactionService)
          .getTransactionPage(
              TEST_GUILD_ID,
              TEST_USER_ID,
              3,
              ProductRedemptionTransactionService.DEFAULT_PAGE_SIZE);
    }
  }
}
