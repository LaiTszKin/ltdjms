package ltdjms.discord.currency.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.bot.BotErrorHandler;
import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.currency.domain.BalanceView;
import ltdjms.discord.currency.services.BalanceService;
import ltdjms.discord.discord.adapter.SlashCommandAdapter;
import ltdjms.discord.discord.domain.DiscordContext;
import ltdjms.discord.discord.domain.DiscordInteraction;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/**
 * Handler for the /balance slash command. Shows the user's current currency balance in the guild.
 *
 * <p>All predictable errors are handled via Result&lt;T, DomainError&gt; pattern. This handler does
 * not catch domain exceptions directly; instead it relies on the Result-based service API for all
 * expected error conditions.
 *
 * <p>此類別使用 {@link DiscordInteraction} 和 {@link DiscordContext} 抽象介面 來處理 Discord 回應和提取上下文資訊，避免直接依賴
 * JDA API。 錯誤處理路徑仍使用原始 JDA event 以維持相容性。
 */
public class BalanceCommandHandler implements SlashCommandListener.CommandHandler {

  private static final Logger LOG = LoggerFactory.getLogger(BalanceCommandHandler.class);

  private final BalanceService balanceService;

  public BalanceCommandHandler(BalanceService balanceService) {
    this.balanceService = balanceService;
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    // 使用抽象介面取得 Discord 互動物件和上下文
    DiscordInteraction interaction = SlashCommandAdapter.fromSlashEvent(event);
    DiscordContext context = SlashCommandAdapter.toContext(event);

    // 透過抽象介面取得 Guild ID 和 User ID
    long guildId = context.getGuildId();
    long userId = context.getUserId();

    LOG.debug("Processing /balance for guildId={}, userId={}", guildId, userId);

    Result<BalanceView, DomainError> result = balanceService.tryGetBalance(guildId, userId);

    if (result.isErr()) {
      // 錯誤處理路徑仍使用原始 event（BotErrorHandler 尚未遷移）
      BotErrorHandler.handleDomainError(event, result.getError());
      return;
    }

    BalanceView balanceView = result.getValue();
    String message = balanceView.formatMessage();

    // 成功路徑使用抽象介面發送回應
    interaction.reply(message);

    BotErrorHandler.logSuccess(event, "balance=" + balanceView.balance());
  }
}
