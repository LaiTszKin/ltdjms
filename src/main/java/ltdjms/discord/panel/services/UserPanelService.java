package ltdjms.discord.panel.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.gametoken.services.GameTokenTransactionService;
import ltdjms.discord.redemption.services.ProductRedemptionTransactionService;
import ltdjms.discord.redemption.services.RedemptionService;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;

/**
 * Service for generating user panel data. Aggregates currency balance and game token information
 * for display in the personal panel embed through a facade.
 *
 * <p>This service has been refactored to use MemberInfoFacade, reducing dependencies from 6 to 1.
 */
public class UserPanelService {

  private static final Logger LOG = LoggerFactory.getLogger(UserPanelService.class);

  private final MemberInfoFacade memberInfoFacade;

  public UserPanelService(MemberInfoFacade memberInfoFacade) {
    this.memberInfoFacade = memberInfoFacade;
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
    return memberInfoFacade.getUserPanelView(guildId, userId);
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
    return memberInfoFacade.getUserPanelView(guildId, userId, membershipSummary);
  }

  /** Returns membership tier and period progress for panel rendering. */
  public MembershipPanelSummary getMembershipSummary(long userId) {
    return memberInfoFacade.getMembershipSummary(userId);
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
    return memberInfoFacade.getTokenTransactionPage(guildId, userId, page);
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
    return memberInfoFacade.getCurrencyTransactionPage(guildId, userId, page);
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
    return memberInfoFacade.redeemCode(code, guildId, userId);
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
    return memberInfoFacade.getProductRedemptionTransactionPage(guildId, userId, page);
  }
}
