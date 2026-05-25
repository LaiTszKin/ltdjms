package ltdjms.discord.panel.services;

import java.time.Instant;

import ltdjms.discord.membership.domain.MembershipTier;
import ltdjms.discord.membership.domain.MembershipTierLabels;
import ltdjms.discord.membership.services.MembershipPanelSummary;

/**
 * View model for the user panel, containing all data needed to render the personal panel embed.
 *
 * @param guildId the guild ID
 * @param userId the user ID
 * @param currencyBalance the user's currency balance
 * @param currencyName the currency name
 * @param currencyIcon the currency icon/emoji
 * @param gameTokens the user's game token balance
 * @param membershipSummary membership tier and progress, or null when unavailable
 */
public record UserPanelView(
    long guildId,
    long userId,
    long currencyBalance,
    String currencyName,
    String currencyIcon,
    long gameTokens,
    MembershipPanelSummary membershipSummary) {

  private static final String EMBED_TITLE = "個人面板";
  private static final String GAME_TOKEN_ICON = "🎮";
  private static final String GAME_TOKEN_NAME = "遊戲代幣";
  private static final String NONE_TIER_HINT =
      "尚未達標（需完成 M≥500 護航法幣單）\n" + "完成一筆 NT$500 以上護航法幣訂單即可升級青銅";
  private static final String NONE_TIER_BENEFITS_HINT = "完成一筆 NT$500 以上護航法幣訂單即可升級青銅";

  /**
   * Gets the embed title for the user panel.
   *
   * @return the embed title in zh-TW
   */
  public String getEmbedTitle() {
    return EMBED_TITLE;
  }

  /**
   * Formats the currency balance field for display in an embed.
   *
   * @return formatted currency field with icon, amount, and name
   */
  public String formatCurrencyField() {
    return String.format("%s %,d %s", currencyIcon, currencyBalance, currencyName);
  }

  /**
   * Formats the game tokens field for display in an embed.
   *
   * @return formatted game tokens field with icon and amount
   */
  public String formatGameTokensField() {
    return String.format("%s %,d %s", GAME_TOKEN_ICON, gameTokens, GAME_TOKEN_NAME);
  }

  /**
   * Formats the membership section for display in an embed.
   *
   * @return formatted membership field in zh-TW
   */
  public String formatMembershipField() {
    if (membershipSummary == null) {
      return NONE_TIER_HINT;
    }
    if (membershipSummary.tier() == MembershipTier.NONE) {
      return formatNoneTierMembershipField();
    }
    return formatActiveTierMembershipField(membershipSummary);
  }

  private String formatNoneTierMembershipField() {
    StringBuilder builder = new StringBuilder();
    appendJoinDate(builder, membershipSummary.earliestGuildJoinAt());
    builder.append("**等級：**尚未達標\n");
    builder.append("**目前權益：**").append(NONE_TIER_BENEFITS_HINT).append('\n');
    appendPeriodSpend(builder, membershipSummary);
    appendRemainingToNextTier(builder, membershipSummary);
    appendNextTierProgress(builder, membershipSummary);
    appendNextSettlement(builder, membershipSummary.nextSettlementAt());
    return builder.toString();
  }

  private static String formatActiveTierMembershipField(MembershipPanelSummary membershipSummary) {
    MembershipTier tier = membershipSummary.tier();
    String tierName = MembershipTierLabels.displayName(tier);

    StringBuilder builder = new StringBuilder();
    appendJoinDate(builder, membershipSummary.earliestGuildJoinAt());
    builder.append("**等級：**").append(tierName).append('\n');
    appendCurrentBenefits(builder, membershipSummary);
    appendPeriodSpend(builder, membershipSummary);
    appendRemainingToNextTier(builder, membershipSummary);
    appendNextTierProgress(builder, membershipSummary);
    appendNextSettlement(builder, membershipSummary.nextSettlementAt());
    return builder.toString();
  }

  private static void appendJoinDate(StringBuilder builder, Instant joinAt) {
    builder.append("**加入日期：**");
    if (joinAt != null) {
      builder.append(String.format("<t:%d:D>", joinAt.getEpochSecond()));
    } else {
      builder.append("尚未記錄");
    }
    builder.append('\n');
  }

  private static void appendCurrentBenefits(StringBuilder builder, MembershipPanelSummary summary) {
    String discount = MembershipTierLabels.discountLabel(summary.tier());
    String tokenPart =
        summary.monthlyTokenGrant() > 0
            ? "每月贈幣 " + summary.monthlyTokenGrant()
            : "每月贈幣：無";
    builder.append("**目前權益：**").append(discount).append("、").append(tokenPart).append('\n');
  }

  private static void appendPeriodSpend(StringBuilder builder, MembershipPanelSummary summary) {
    builder
        .append("**本週期累計 M：**")
        .append(String.format("%,d", summary.periodSpendListPriceM()))
        .append('\n');
  }

  private static void appendRemainingToNextTier(StringBuilder builder, MembershipPanelSummary summary) {
    builder.append("**距下一等級：**");
    if (summary.hasNextTierThreshold()) {
      builder
          .append("還需 ")
          .append(String.format("%,d", summary.remainingToNextTierM()))
          .append(" M");
    } else {
      builder.append("已達最高等級");
    }
    builder.append('\n');
  }

  private static void appendNextTierProgress(StringBuilder builder, MembershipPanelSummary summary) {
    if (summary.hasNextTierThreshold()) {
      int progressPercent = (int) Math.round(summary.nextTierProgressRatio() * 100);
      builder
          .append("**下一門檻進度：**")
          .append(progressPercent)
          .append("% (")
          .append(String.format("%,d", summary.periodSpendListPriceM()))
          .append(" / ")
          .append(String.format("%,d", summary.nextTierThresholdM()))
          .append(" M)\n");
    } else {
      builder.append("**下一門檻進度：**已達最高等級\n");
    }
  }

  private static void appendNextSettlement(StringBuilder builder, Instant nextSettlementAt) {
    builder.append("**下次結算日：**");
    if (nextSettlementAt != null) {
      builder.append(String.format("<t:%d:D>", nextSettlementAt.getEpochSecond()));
    } else {
      builder.append("尚未設定");
    }
  }

  /** Field name for the membership embed section. */
  public String getMembershipFieldName() {
    return "會員等級";
  }

  /**
   * Gets the currency balance field name for the embed. Uses the guild's custom currency name for
   * consistency.
   *
   * @return the field name in zh-TW, e.g., "星幣餘額"
   */
  public String getCurrencyFieldName() {
    return currencyName + "餘額";
  }

  /**
   * Gets the button label for viewing currency transaction history. Uses the guild's custom
   * currency icon for consistency.
   *
   * @return the button label in zh-TW, e.g., "✨ 查看貨幣流水"
   */
  public String getCurrencyHistoryButtonLabel() {
    return currencyIcon + " 查看貨幣流水";
  }

  /**
   * Gets the game tokens field name for the embed.
   *
   * @return the field name in zh-TW
   */
  public String getGameTokensFieldName() {
    return "遊戲代幣餘額";
  }
}
