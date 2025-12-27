package ltdjms.discord.currency.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.panel.commands.AdminPanelButtonHandler;
import ltdjms.discord.panel.commands.AdminProductPanelHandler;
import ltdjms.discord.panel.commands.UserPanelButtonHandler;
import ltdjms.discord.shop.commands.ShopButtonHandler;
import ltdjms.discord.shop.commands.ShopSelectMenuHandler;

/** 驗證事件監聽器組合是否包含商品管理處理器。 */
class DiscordCurrencyBotTest {

  @Test
  @DisplayName("事件監聽器列表應包含商品面板處理器")
  void eventListenersShouldIncludeProductPanelHandler() {
    SlashCommandListener slashCommandListener = mock(SlashCommandListener.class);
    UserPanelButtonHandler userPanelButtonHandler = mock(UserPanelButtonHandler.class);
    AdminPanelButtonHandler adminPanelButtonHandler = mock(AdminPanelButtonHandler.class);
    AdminProductPanelHandler adminProductPanelHandler = mock(AdminProductPanelHandler.class);
    ShopButtonHandler shopButtonHandler = mock(ShopButtonHandler.class);
    ShopSelectMenuHandler shopSelectMenuHandler = mock(ShopSelectMenuHandler.class);

    List<Object> listeners =
        DiscordCurrencyBot.buildEventListeners(
            slashCommandListener,
            userPanelButtonHandler,
            adminPanelButtonHandler,
            adminProductPanelHandler,
            shopButtonHandler,
            shopSelectMenuHandler);

    assertThat(listeners)
        .containsExactly(
            slashCommandListener,
            userPanelButtonHandler,
            adminPanelButtonHandler,
            adminProductPanelHandler,
            shopButtonHandler,
            shopSelectMenuHandler);
  }
}
