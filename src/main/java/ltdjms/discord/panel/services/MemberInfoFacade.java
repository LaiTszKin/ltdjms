package ltdjms.discord.panel.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.gametoken.services.GameTokenService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.membership.services.MembershipPanelSummary;
import ltdjms.discord.membership.services.MembershipQueryService;
import ltdjms.discord.redemption.services.ProductRedemptionTransactionService;
import ltdjms.discord.redemption.services.RedemptionService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/**
 * Facade for member information aggregation. Combines currency and game token information for
 * display in user panels.
 */
public class MemberInfoFacade {

  private static final Logger LOG = LoggerFactory.getLogger(MemberInfoFacade.class);

  private final BalanceService balanceService;
  private final GameTokenService gameTokenService;
  private final GameTokenTransactionService gameTokenTransactionService;
  private final CurrencyTransactionService currencyTransactionService;
  private final RedemptionService redemptionService;
  private final ProductRedemptionTransactionService productRedemptionTransactionService;
  private final MembershipQueryService membershipQueryService;

  public MemberInfoFacade(
      BalanceService balanceService,
      GameTokenService gameTokenService,
      GameTokenTransactionService gameTokenTransactionService,
      CurrencyTransactionService currencyTransactionService,
      RedemptionService redemptionService,
      ProductRedemptionTransactionService productRedemptionTransactionService,
      MembershipQueryService membershipQueryService) {
    this.balanceService = balanceService;
    this.gameTokenService = gameTokenService;
    this.gameTokenTransactionService = gameTokenTransactionService;
    this.currencyTransactionService = currencyTransactionService;
    this.redemptionService = redemptionService;
    this.productRedemptionTransactionService = productRedemptionTransactionService;
    this.membershipQueryService = membershipQueryService;
  }

  /**
   * Gets the user panel view for a member in a guild.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @return Result containing UserPanelView on success, or DomainError on failure
   */
  public Result<UserPanelView, DomainError> getUserPanelView(long guildId, long userId) {
    LOG.debug("Getting user panel view for guildId={}, userId={}", guildId, userId);

    Result<BalanceView, DomainError> balanceResult = balanceService.tryGetBalance(guildId, userId);
    if (balanceResult.isErr()) {
      LOG.warn(
          "Failed to get balance for guildId={}, userId={}: {}",
          guildId,
          userId,
          balanceResult.getError().message());
      return Result.err(balanceResult.getError());
    }

    MembershipPanelSummary membershipSummary = getMembershipSummary(userId);
    return buildUserPanelView(guildId, userId, balanceResult.getValue(), membershipSummary);
  }

  /**
   * Gets the user panel view using a precomputed membership summary.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param membershipSummary membership summary shared across guild panels
   * @return Result containing UserPanelView on success, or DomainError on failure
   */
  public Result<UserPanelView, DomainError> getUserPanelView(
      long guildId, long userId, MembershipPanelSummary membershipSummary) {
    LOG.debug(
        "Getting user panel view for guildId={}, userId={} with cached membership summary",
        guildId,
        userId);

    Result<BalanceView, DomainError> balanceResult = balanceService.tryGetBalance(guildId, userId);
    if (balanceResult.isErr()) {
      LOG.warn(
          "Failed to get balance for guildId={}, userId={}: {}",
          guildId,
          userId,
          balanceResult.getError().message());
      return Result.err(balanceResult.getError());
    }

    return buildUserPanelView(guildId, userId, balanceResult.getValue(), membershipSummary);
  }

  private Result<UserPanelView, DomainError> buildUserPanelView(
      long guildId,
      long userId,
      BalanceView balanceView,
      MembershipPanelSummary membershipSummary) {
    long gameTokens = gameTokenService.getBalance(guildId, userId);

    UserPanelView panelView =
        new UserPanelView(
            guildId,
            userId,
            balanceView.balance(),
            balanceView.currencyName(),
            balanceView.currencyIcon(),
            gameTokens,
            membershipSummary);

    LOG.debug(
        "User panel view created: guildId={}, userId={}, currency={}, tokens={}",
        guildId,
        userId,
        balanceView.balance(),
        gameTokens);

    return Result.ok(panelView);
  }

  /**
   * Builds membership tier and period progress for the user panel.
   *
   * @param userId Discord user snowflake
   * @return summary for panel rendering; tier {@link MembershipTier#NONE} when no membership row
   */
  public MembershipPanelSummary getMembershipSummary(long userId) {
    return membershipQueryService.getPanelSummary(userId);
  }

  /**
   * Gets a page of token transaction history for a user.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param page the page number (1-based)
   * @return the transaction page
   */
  public GameTokenTransactionService.TransactionPage getTokenTransactionPage(
      long guildId, long userId, int page) {
    LOG.debug(
        "Getting token transaction page for guildId={}, userId={}, page={}", guildId, userId, page);
    return gameTokenTransactionService.getTransactionPage(
        guildId, userId, page, GameTokenTransactionService.DEFAULT_PAGE_SIZE);
  }

  /**
   * Gets a page of currency transaction history for a user.
   *
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @param page the page number (1-based)
   * @return the currency transaction page
   */
  public CurrencyTransactionService.TransactionPage getCurrencyTransactionPage(
      long guildId, long userId, int page) {
    LOG.debug(
        "Getting currency transaction page for guildId={}, userId={}, page={}",
        guildId,
        userId,
        page);
    return currencyTransactionService.getTransactionPage(
        guildId, userId, page, CurrencyTransactionService.DEFAULT_PAGE_SIZE);
  }

  /**
   * Redeems a code for a user.
   *
   * @param code the redemption code string
   * @param guildId the Discord guild ID
   * @param userId the Discord user ID
   * @return Result containing the redemption result or an error
   */
  public Result<RedemptionService.RedemptionResult, DomainError> redeemCode(
      String code, long guildId, long userId) {
    LOG.debug("Redeeming code for guildId={}, userId={}", guildId, userId);
    return redemptionService.redeemCode(code, guildId, userId);
  }

  /**
   * 取得使用者的商品兌換交易分頁紀錄。
   *
   * @param guildId Discord 伺服器 ID
   * @param userId Discord 使用者 ID
   * @param page 頁碼（從 1 開始）
   * @return 商品兌換交易分頁紀錄
   */
  public ProductRedemptionTransactionService.TransactionPage getProductRedemptionTransactionPage(
      long guildId, long userId, int page) {
    LOG.debug(
        "Getting product redemption transaction page for guildId={}, userId={}, page={}",
        guildId,
        userId,
        page);
    return productRedemptionTransactionService.getTransactionPage(
        guildId, userId, page, ProductRedemptionTransactionService.DEFAULT_PAGE_SIZE);
  }
}
