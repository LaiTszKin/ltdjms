package ltdjms.discord.discord.adapter;

import ltdjms.discord.discord.domain.DiscordContext;
import ltdjms.discord.discord.domain.DiscordInteraction;
import ltdjms.discord.discord.services.JdaDiscordContext;
import ltdjms.discord.discord.services.JdaDiscordInteraction;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/**
 * Slash 指令事件適配器
 *
 * <p>此類別提供靜態方法將 JDA 的 {@link SlashCommandInteractionEvent} 轉換為統一的 {@link DiscordInteraction} 和
 * {@link DiscordContext} 抽象介面。
 *
 * <p>主要功能：
 *
 * <ul>
 *   <li>從 Slash 指令事件建立 DiscordInteraction
 *   <li>從 Slash 指令事件建立 DiscordContext
 *   <li>封裝 JDA 特定的轉換邏輯
 *   <li>提供簡潔的 API 給命令處理器使用
 * </ul>
 *
 * <p>使用範例：
 *
 * <pre>{@code
 * public class BalanceCommandHandler {
 *     public void handle(SlashCommandInteractionEvent event) {
 *         DiscordInteraction interaction = SlashCommandAdapter.fromSlashEvent(event);
 *         DiscordContext context = SlashCommandAdapter.toContext(event);
 *
 *         interaction.reply("餘額查詢結果...");
 *     }
 * }
 * }</pre>
 */
public class SlashCommandAdapter {

  /**
   * 從 Slash 指令事件建立 DiscordInteraction
   *
   * @param event JDA Slash 指令事件
   * @return DiscordInteraction 抽象介面實例
   * @throws IllegalArgumentException 如果 event 為 null
   */
  public static DiscordInteraction fromSlashEvent(SlashCommandInteractionEvent event) {
    if (event == null) {
      throw new IllegalArgumentException("SlashCommandInteractionEvent 不能為 null");
    }
    return new JdaDiscordInteraction(event);
  }

  /**
   * 從通用互動事件建立 DiscordInteraction
   *
   * <p>此方法支援所有類型的互動事件（包括 Slash 指令、按鈕、選擇選單等）。
   *
   * @param event JDA 通用互動事件
   * @return DiscordInteraction 抽象介面實例
   * @throws IllegalArgumentException 如果 event 為 null
   */
  public static DiscordInteraction fromGenericEvent(GenericInteractionCreateEvent event) {
    if (event == null) {
      throw new IllegalArgumentException("GenericInteractionCreateEvent 不能為 null");
    }
    return new JdaDiscordInteraction(event);
  }

  /**
   * 從 Slash 指令事件建立 DiscordContext
   *
   * <p>此方法從 Slash 指令事件中提取上下文資訊，包括 Guild ID、使用者 ID、頻道 ID 和命令選項。
   *
   * @param event JDA Slash 指令事件
   * @return DiscordContext 抽象介面實例
   * @throws IllegalArgumentException 如果 event 為 null
   */
  public static DiscordContext toContext(SlashCommandInteractionEvent event) {
    if (event == null) {
      throw new IllegalArgumentException("SlashCommandInteractionEvent 不能為 null");
    }
    return new JdaDiscordContext(event);
  }

  /**
   * 從通用互動事件建立 DiscordContext
   *
   * <p>此方法支援所有類型的互動事件（包括 Slash 指令、按鈕、選擇選單等）。
   *
   * @param event JDA 通用互動事件
   * @return DiscordContext 抽象介面實例
   * @throws IllegalArgumentException 如果 event 為 null
   */
  public static DiscordContext toContext(GenericInteractionCreateEvent event) {
    if (event == null) {
      throw new IllegalArgumentException("GenericInteractionCreateEvent 不能為 null");
    }
    return new JdaDiscordContext(event);
  }

  // 私有建構函式，防止實例化
  private SlashCommandAdapter() {
    throw new UnsupportedOperationException("SlashCommandAdapter 是工具類別，無法實例化");
  }
}
