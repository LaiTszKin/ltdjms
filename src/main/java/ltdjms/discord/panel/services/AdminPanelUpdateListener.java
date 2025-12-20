package ltdjms.discord.panel.services;

import ltdjms.discord.panel.commands.AdminProductPanelHandler;
import ltdjms.discord.shared.events.CurrencyConfigChangedEvent;
import ltdjms.discord.shared.events.DiceGameConfigChangedEvent;
import ltdjms.discord.shared.events.DomainEvent;
import ltdjms.discord.shared.events.ProductChangedEvent;
import ltdjms.discord.shared.events.RedemptionCodesGeneratedEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Listener for domain events that triggers real-time updates for active admin panels.
 * Handles currency configuration, game configuration, and product changes.
 */
public class AdminPanelUpdateListener implements Consumer<DomainEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(AdminPanelUpdateListener.class);

    private final AdminPanelSessionManager sessionManager;
    private final AdminProductPanelHandler adminProductPanelHandler;

    public AdminPanelUpdateListener(AdminPanelSessionManager sessionManager,
                                    AdminProductPanelHandler adminProductPanelHandler) {
        this.sessionManager = sessionManager;
        this.adminProductPanelHandler = adminProductPanelHandler;
    }

    @Override
    public void accept(DomainEvent event) {
        if (event instanceof CurrencyConfigChangedEvent e) {
            handleCurrencyConfigChanged(e);
        } else if (event instanceof DiceGameConfigChangedEvent e) {
            handleDiceGameConfigChanged(e);
        } else if (event instanceof ProductChangedEvent e) {
            handleProductChanged(e);
        } else if (event instanceof RedemptionCodesGeneratedEvent e) {
            handleRedemptionCodesGenerated(e);
        }
    }

    private void handleCurrencyConfigChanged(CurrencyConfigChangedEvent event) {
        LOG.debug("Admin panel update triggered by currency config change for guildId={}", event.guildId());
        // Admin panels showing currency information need to be refreshed
        // This is a notification-only update - the actual panel rebuild will happen
        // when the admin interacts with the panel next
        notifyAdminPanels(event.guildId(), "貨幣設定已更新");
    }

    private void handleDiceGameConfigChanged(DiceGameConfigChangedEvent event) {
        LOG.debug("Admin panel update triggered by {} config change for guildId={}",
                event.gameType(), event.guildId());
        // Game config panels should refresh to show updated values
        notifyAdminPanels(event.guildId(), "遊戲設定已更新");
    }

    private void handleProductChanged(ProductChangedEvent event) {
        LOG.debug("Admin panel update triggered by product {} for guildId={}, productId={}",
                event.operationType(), event.guildId(), event.productId());
        adminProductPanelHandler.refreshProductPanels(event.guildId());
        notifyAdminPanels(event.guildId(), "商品資料已更新");
    }

    private void handleRedemptionCodesGenerated(RedemptionCodesGeneratedEvent event) {
        LOG.debug("Admin panel update triggered by redemption codes generated for guildId={}, productId={}, count={}",
                event.guildId(), event.productId(), event.count());
        adminProductPanelHandler.refreshProductPanels(event.guildId());
        notifyAdminPanels(event.guildId(), "兌換碼已更新");
    }

    /**
     * Notifies all admin panels in a guild that a change has occurred.
     * Since admin panels have complex state, we log the notification
     * and let the panels refresh on next interaction.
     */
    private void notifyAdminPanels(long guildId, String changeDescription) {
        sessionManager.updatePanelsByGuild(guildId, hook -> {
            LOG.trace("Admin panel session notified of change: {}", changeDescription);
            // Admin panels are stateful with multiple views (main menu, game config, product list, etc.)
            // A full rebuild requires knowing the current view state, which isn't stored in the session.
            // Instead, we log that a change occurred. The admin will see updated data
            // on their next navigation action.
        });
    }
}
