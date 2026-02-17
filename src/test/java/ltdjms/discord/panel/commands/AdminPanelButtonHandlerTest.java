package ltdjms.discord.panel.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.panel.services.AdminPanelService;
import ltdjms.discord.panel.services.AdminPanelSessionManager;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

class AdminPanelButtonHandlerTest {

  private final AdminPanelButtonHandler handler =
      new AdminPanelButtonHandler(mock(AdminPanelService.class), new AdminPanelSessionManager());

  private final AdminPanelCommandHandler commandHandler =
      new AdminPanelCommandHandler(mock(AdminPanelService.class), new AdminPanelSessionManager());

  @Test
  @DisplayName("返回主選單時商品按鈕應維持「商品與兌換碼管理」文字")
  void mainPanelButtonsShouldKeepRedeemLabel() {
    List<ActionRow> rows = handler.buildMainPanelComponents("💰");

    ActionRow secondRow = rows.get(1);
    Button productButton = (Button) secondRow.getComponents().get(1);

    assertThat(productButton.getLabel()).isEqualTo("📦 商品與兌換碼管理");
  }

  @Test
  @DisplayName("返回主選單嵌入欄位應包含商品與兌換碼管理")
  void mainPanelEmbedShouldIncludeRedeemField() {
    MessageEmbed embed = handler.buildMainPanelEmbed("💰");

    assertThat(embed.getFields()).extracting(MessageEmbed.Field::getName).contains("📦 商品與兌換碼管理");
  }

  @Test
  @DisplayName("返回主選單應包含派單售後設定按鈕")
  void mainPanelShouldIncludeDispatchAfterSalesButton() {
    List<ActionRow> rows = handler.buildMainPanelComponents("💰");

    ActionRow lastRow = rows.get(rows.size() - 1);
    Button dispatchButton = (Button) lastRow.getComponents().get(0);

    assertThat(dispatchButton.getId())
        .isEqualTo(AdminPanelButtonHandler.BUTTON_DISPATCH_AFTER_SALES_CONFIG);
    assertThat(dispatchButton.getLabel()).isEqualTo("🧰 派單售後設定");
  }

  @Test
  @DisplayName("主選單初始載入與返回主選單應使用相同渲染路徑")
  void loadedAndReturnedMainPanelShouldUseSameRenderingPath() {
    MessageEmbed commandEmbed = commandHandler.buildMainPanelEmbed("💰");
    MessageEmbed buttonEmbed = handler.buildMainPanelEmbed("💰");

    assertThat(buttonEmbed.getFields().stream().map(MessageEmbed.Field::getName).toList())
        .isEqualTo(commandEmbed.getFields().stream().map(MessageEmbed.Field::getName).toList());

    List<String> commandButtons = flattenButtonIds(commandHandler.buildMainActionRows("💰"));
    List<String> buttonButtons = flattenButtonIds(handler.buildMainPanelComponents("💰"));

    assertThat(buttonButtons).isEqualTo(commandButtons);
  }

  private List<String> flattenButtonIds(List<ActionRow> rows) {
    return rows.stream()
        .flatMap(row -> row.getComponents().stream())
        .map(component -> (Button) component)
        .map(Button::getId)
        .collect(Collectors.toList());
  }
}
