package ltdjms.discord.panel.services;

import ltdjms.discord.panel.commands.AdminProductPanelHandler;
import ltdjms.discord.shared.events.CurrencyConfigChangedEvent;
import ltdjms.discord.shared.events.DiceGameConfigChangedEvent;
import ltdjms.discord.shared.events.ProductChangedEvent;
import ltdjms.discord.shared.events.RedemptionCodesGeneratedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminPanelUpdateListenerTest {

    private AdminPanelSessionManager sessionManager;
    private AdminProductPanelHandler adminProductPanelHandler;
    private AdminPanelUpdateListener listener;

    @BeforeEach
    void setUp() {
        sessionManager = mock(AdminPanelSessionManager.class);
        adminProductPanelHandler = mock(AdminProductPanelHandler.class);
        listener = new AdminPanelUpdateListener(sessionManager, adminProductPanelHandler);
    }

    @Test
    void accept_shouldHandleCurrencyConfigChangedEvent() {
        // Given
        CurrencyConfigChangedEvent event = new CurrencyConfigChangedEvent(123L, "金幣", "💰");

        // When
        listener.accept(event);

        // Then
        verify(sessionManager).updatePanelsByGuild(eq(123L), any());
    }

    @Test
    void accept_shouldHandleDiceGame1ConfigChangedEvent() {
        // Given
        DiceGameConfigChangedEvent event = new DiceGameConfigChangedEvent(
                456L, DiceGameConfigChangedEvent.GameType.DICE_GAME_1);

        // When
        listener.accept(event);

        // Then
        verify(sessionManager).updatePanelsByGuild(eq(456L), any());
    }

    @Test
    void accept_shouldHandleDiceGame2ConfigChangedEvent() {
        // Given
        DiceGameConfigChangedEvent event = new DiceGameConfigChangedEvent(
                789L, DiceGameConfigChangedEvent.GameType.DICE_GAME_2);

        // When
        listener.accept(event);

        // Then
        verify(sessionManager).updatePanelsByGuild(eq(789L), any());
    }

    @Test
    void accept_shouldHandleProductCreatedEvent() {
        // Given
        ProductChangedEvent event = new ProductChangedEvent(
                100L, 1L, ProductChangedEvent.OperationType.CREATED);

        // When
        listener.accept(event);

        // Then
        verify(sessionManager).updatePanelsByGuild(eq(100L), any());
        verify(adminProductPanelHandler).refreshProductPanels(100L);
    }

    @Test
    void accept_shouldHandleProductUpdatedEvent() {
        // Given
        ProductChangedEvent event = new ProductChangedEvent(
                100L, 2L, ProductChangedEvent.OperationType.UPDATED);

        // When
        listener.accept(event);

        // Then
        verify(sessionManager).updatePanelsByGuild(eq(100L), any());
        verify(adminProductPanelHandler).refreshProductPanels(100L);
    }

    @Test
    void accept_shouldHandleProductDeletedEvent() {
        // Given
        ProductChangedEvent event = new ProductChangedEvent(
                100L, 3L, ProductChangedEvent.OperationType.DELETED);

        // When
        listener.accept(event);

        // Then
        verify(sessionManager).updatePanelsByGuild(eq(100L), any());
        verify(adminProductPanelHandler).refreshProductPanels(100L);
    }

    @Test
    void accept_shouldHandleRedemptionCodesGeneratedEvent() {
        RedemptionCodesGeneratedEvent event = new RedemptionCodesGeneratedEvent(321L, 9L, 5);

        listener.accept(event);

        verify(sessionManager).updatePanelsByGuild(eq(321L), any());
        verify(adminProductPanelHandler).refreshProductPanels(321L);
    }
}
