package ltdjms.discord.panel.commands;

import ltdjms.discord.currency.domain.CurrencyTransaction;
import ltdjms.discord.currency.services.CurrencyTransactionService;
import ltdjms.discord.gametoken.domain.GameTokenTransaction;
import ltdjms.discord.gametoken.services.GameTokenTransactionService.TransactionPage;
import ltdjms.discord.panel.services.UserPanelService;
import ltdjms.discord.panel.services.UserPanelView;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles button interactions for the user panel.
 * Processes token history viewing, pagination, and navigation back to main panel.
 */
public class UserPanelButtonHandler extends ListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(UserPanelButtonHandler.class);

    private static final Color EMBED_COLOR = new Color(0x5865F2);

    // Button ID prefix for token history pagination
    public static final String BUTTON_PREFIX_HISTORY = "user_panel_token_history";
    public static final String BUTTON_PREFIX_TOKEN_PAGE = "user_panel_token_page_";

    // Button ID prefix for currency history pagination
    public static final String BUTTON_PREFIX_CURRENCY_HISTORY = "user_panel_currency_history";
    public static final String BUTTON_PREFIX_CURRENCY_PAGE = "user_panel_currency_page_";

    public static final String BUTTON_BACK_TO_PANEL = "user_panel_back";

    private final UserPanelService userPanelService;

    public UserPanelButtonHandler(UserPanelService userPanelService) {
        this.userPanelService = userPanelService;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        // Only handle our button interactions
        if (!buttonId.startsWith("user_panel_")) {
            return;
        }

        // Only process in guilds
        if (!event.isFromGuild() || event.getGuild() == null) {
            event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();
        long userId = event.getUser().getIdLong();

        LOG.debug("Processing button interaction: buttonId={}, guildId={}, userId={}",
                buttonId, guildId, userId);

        try {
            if (buttonId.equals(BUTTON_PREFIX_HISTORY)) {
                // Show first page of token history
                showTokenHistoryPage(event, guildId, userId, 1);
            } else if (buttonId.startsWith(BUTTON_PREFIX_TOKEN_PAGE)) {
                // Parse page number from button ID for token history
                String pageStr = buttonId.substring(BUTTON_PREFIX_TOKEN_PAGE.length());
                int page = Integer.parseInt(pageStr);
                showTokenHistoryPage(event, guildId, userId, page);
            } else if (buttonId.equals(BUTTON_PREFIX_CURRENCY_HISTORY)) {
                // Show first page of currency history
                showCurrencyHistoryPage(event, guildId, userId, 1);
            } else if (buttonId.startsWith(BUTTON_PREFIX_CURRENCY_PAGE)) {
                // Parse page number from button ID for currency history
                String pageStr = buttonId.substring(BUTTON_PREFIX_CURRENCY_PAGE.length());
                int page = Integer.parseInt(pageStr);
                showCurrencyHistoryPage(event, guildId, userId, page);
            } else if (buttonId.equals(BUTTON_BACK_TO_PANEL)) {
                // Navigate back to main panel
                showMainPanel(event, guildId, userId);
            } else {
                LOG.warn("Unknown button ID: {}", buttonId);
            }
        } catch (NumberFormatException e) {
            LOG.error("Failed to parse page number from button ID: {}", buttonId, e);
            event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
        } catch (Exception e) {
            LOG.error("Error handling button interaction: {}", buttonId, e);
            event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
        }
    }

    private void showMainPanel(ButtonInteractionEvent event, long guildId, long userId) {
        Result<UserPanelView, DomainError> result = userPanelService.getUserPanelView(guildId, userId);

        if (result.isErr()) {
            LOG.error("Failed to load user panel view: guildId={}, userId={}, error={}",
                    guildId, userId, result.getError().message());
            event.reply("無法載入面板資料，請稍後再試").setEphemeral(true).queue();
            return;
        }

        UserPanelView panelView = result.getValue();
        MessageEmbed embed = buildPanelEmbed(panelView, event.getUser().getAsMention());

        event.editMessageEmbeds(embed)
                .setActionRow(
                        Button.secondary(BUTTON_PREFIX_CURRENCY_HISTORY, "💰 查看貨幣流水"),
                        Button.secondary(BUTTON_PREFIX_HISTORY, "📜 查看遊戲代幣流水")
                )
                .queue();

        LOG.debug("Navigated back to main panel for guildId={}, userId={}", guildId, userId);
    }

    private MessageEmbed buildPanelEmbed(UserPanelView view, String userMention) {
        return new EmbedBuilder()
                .setTitle(view.getEmbedTitle())
                .setDescription(userMention + " 的帳戶資訊")
                .setColor(EMBED_COLOR)
                .addField(view.getCurrencyFieldName(), view.formatCurrencyField(), true)
                .addField(view.getGameTokensFieldName(), view.formatGameTokensField(), true)
                .setFooter("點擊下方按鈕查看流水紀錄")
                .build();
    }

    private void showTokenHistoryPage(ButtonInteractionEvent event, long guildId, long userId, int page) {
        TransactionPage transactionPage = userPanelService.getTokenTransactionPage(guildId, userId, page);

        MessageEmbed embed = buildTokenHistoryEmbed(transactionPage);
        List<Button> buttons = buildTokenPaginationButtons(transactionPage);

        // Always show buttons since we always have at least the back button
        event.editMessageEmbeds(embed)
                .setActionRow(buttons)
                .queue();

        LOG.debug("Showed token history page {} for guildId={}, userId={}",
                page, guildId, userId);
    }

    private void showCurrencyHistoryPage(ButtonInteractionEvent event, long guildId, long userId, int page) {
        CurrencyTransactionService.TransactionPage transactionPage =
                userPanelService.getCurrencyTransactionPage(guildId, userId, page);

        MessageEmbed embed = buildCurrencyHistoryEmbed(transactionPage);
        List<Button> buttons = buildCurrencyPaginationButtons(transactionPage);

        // Always show buttons since we always have at least the back button
        event.editMessageEmbeds(embed)
                .setActionRow(buttons)
                .queue();

        LOG.debug("Showed currency history page {} for guildId={}, userId={}",
                page, guildId, userId);
    }

    private MessageEmbed buildTokenHistoryEmbed(TransactionPage page) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("📜 遊戲代幣流水")
                .setColor(EMBED_COLOR);

        if (page.isEmpty()) {
            builder.setDescription("目前沒有任何遊戲代幣流水紀錄");
        } else {
            StringBuilder sb = new StringBuilder();
            for (GameTokenTransaction tx : page.transactions()) {
                sb.append(tx.getShortTimestamp())
                        .append(" ")
                        .append(tx.formatForDisplay())
                        .append("\n");
            }
            builder.setDescription(sb.toString());
        }

        builder.setFooter(page.formatPageIndicator());

        return builder.build();
    }

    private MessageEmbed buildCurrencyHistoryEmbed(CurrencyTransactionService.TransactionPage page) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("💰 貨幣流水")
                .setColor(EMBED_COLOR);

        if (page.isEmpty()) {
            builder.setDescription("目前沒有任何貨幣流水紀錄");
        } else {
            StringBuilder sb = new StringBuilder();
            for (CurrencyTransaction tx : page.transactions()) {
                sb.append(tx.getShortTimestamp())
                        .append(" ")
                        .append(tx.formatForDisplay())
                        .append("\n");
            }
            builder.setDescription(sb.toString());
        }

        builder.setFooter(page.formatPageIndicator());

        return builder.build();
    }

    private List<Button> buildTokenPaginationButtons(TransactionPage page) {
        List<Button> buttons = new ArrayList<>();

        // Add back to panel button
        buttons.add(Button.secondary(BUTTON_BACK_TO_PANEL, "🔙 返回主頁"));

        if (page.hasPreviousPage()) {
            buttons.add(Button.secondary(
                    BUTTON_PREFIX_TOKEN_PAGE + (page.currentPage() - 1),
                    "⬅️ 上一頁"
            ));
        }

        if (page.hasNextPage()) {
            buttons.add(Button.secondary(
                    BUTTON_PREFIX_TOKEN_PAGE + (page.currentPage() + 1),
                    "下一頁 ➡️"
            ));
        }

        return buttons;
    }

    private List<Button> buildCurrencyPaginationButtons(CurrencyTransactionService.TransactionPage page) {
        List<Button> buttons = new ArrayList<>();

        // Add back to panel button
        buttons.add(Button.secondary(BUTTON_BACK_TO_PANEL, "🔙 返回主頁"));

        if (page.hasPreviousPage()) {
            buttons.add(Button.secondary(
                    BUTTON_PREFIX_CURRENCY_PAGE + (page.currentPage() - 1),
                    "⬅️ 上一頁"
            ));
        }

        if (page.hasNextPage()) {
            buttons.add(Button.secondary(
                    BUTTON_PREFIX_CURRENCY_PAGE + (page.currentPage() + 1),
                    "下一頁 ➡️"
            ));
        }

        return buttons;
    }
}
