package ltdjms.discord.panel.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.panel.services.AdminPanelService;
import ltdjms.discord.panel.services.AdminPanelSessionManager;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

/** 驗證管理員面板主選單的內容與按鈕配置。 */
class AdminPanelCommandHandlerTest {

  private final AdminPanelCommandHandler handler =
      new AdminPanelCommandHandler(mock(AdminPanelService.class), new AdminPanelSessionManager());

  @Test
  @DisplayName("主選單應包含商品與兌換碼管理入口")
  void mainPanelShouldIncludeProductManagementEntry() {
    MessageEmbed embed = handler.buildMainPanelEmbed("💰");

    List<String> fieldNames = embed.getFields().stream().map(MessageEmbed.Field::getName).toList();

    assertThat(fieldNames).contains("📦 商品與兌換碼管理");
  }

  @Test
  @DisplayName("主選單按鈕應包含商品管理按鈕")
  void mainPanelButtonsShouldContainProductButton() {
    List<Button> buttons = handler.buildMainActionButtons("💰");

    assertThat(buttons)
        .extracting(Button::getId)
        .contains(AdminProductPanelHandler.BUTTON_PRODUCTS);
  }
}
