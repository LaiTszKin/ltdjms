package ltdjms.discord.discord.domain;

import java.util.Optional;

import net.dv8tion.jda.api.entities.User;

/**
 * Discord 事件上下文提取的統一抽象介面
 *
 * <p>此介面提供從 Discord 事件中提取上下文資訊的所有必要操作，包括：
 *
 * <ul>
 *   <li>取得 Guild、使用者、頻道 ID
 *   <li>取得使用者 Mention 格式
 *   <li>取得命令參數（options）
 * </ul>
 *
 * <p>實作類別應將 JDA 特定的細節封裝，提供統一的介面給業務邏輯使用。
 *
 * <p>使用範例：
 *
 * <pre>{@code
 * public class BalanceCommandHandler {
 *     public void handle(SlashCommandInteractionEvent event) {
 *         DiscordContext context = SlashCommandAdapter.toContext(event);
 *
 *         long guildId = context.getGuildId();
 *         long userId = context.getUserId();
 *         String mention = context.getUserMention();
 *
 *         Result<BalanceView, DomainError> result =
 *             balanceService.getBalance(guildId, userId);
 *
 *         if (result.isOk()) {
 *             interaction.replyEmbed(buildEmbed(mention, result.getValue()));
 *         }
 *     }
 * }
 * }</pre>
 */
public interface DiscordContext {

  /**
   * 取得 Guild ID
   *
   * @return Guild ID（必須為正數）
   */
  long getGuildId();

  /**
   * 取得使用者 ID
   *
   * @return 使用者 ID（必須為正數）
   */
  long getUserId();

  /**
   * 取得頻道 ID
   *
   * @return 頻道 ID（必須為正數）
   */
  long getChannelId();

  /**
   * 取得使用者的 Mention 格式（如 {@code <@123456789>}）
   *
   * @return 使用者 Mention 字串（非空）
   */
  String getUserMention();

  /**
   * 取得命令參數的原始值
   *
   * @param name 參數名稱
   * @return Optional 包含參數值，如果參數不存在則為空
   */
  Optional<String> getOption(String name);

  /**
   * 取得命令參數並轉換為字串
   *
   * @param name 參數名稱
   * @return Optional 包含字串值，如果參數不存在或無法轉換則為空
   */
  Optional<String> getOptionAsString(String name);

  /**
   * 取得命令參數並轉換為 Long
   *
   * @param name 參數名稱
   * @return Optional 包含 Long 值，如果參數不存在、無法轉換或超出範圍則為空
   */
  Optional<Long> getOptionAsLong(String name);

  /**
   * 取得命令參數並轉換為 User 物件
   *
   * @param name 參數名稱
   * @return Optional 包含 User 物件，如果參數不存在或無法轉換則為空
   */
  Optional<User> getOptionAsUser(String name);
}
