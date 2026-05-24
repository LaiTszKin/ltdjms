package ltdjms.discord.dispatch.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.dispatch.domain.EscortDispatchOrder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

@DisplayName("DispatchPanelView 測試")
class DispatchPanelViewTest {

  @Test
  @DisplayName("buildPanelEmbed 應在未選取成員時顯示預設文字")
  void buildPanelEmbedShouldShowFallbackWhenUsersAreNotSelected() {
    MessageEmbed embed = DispatchPanelView.buildPanelEmbed(null, "", null);

    assertThat(embed.getTitle()).isEqualTo("🛡️ 護航派單面板");
    assertThat(embed.getDescription()).contains("請選擇護航者與客戶");
    assertThat(embed.getFields()).hasSize(3);
    assertThat(embed.getFields().get(0).getValue()).isEqualTo("尚未選擇");
    assertThat(embed.getFields().get(1).getValue()).isEqualTo("尚未選擇");
    assertThat(embed.getFields().get(2).getValue()).contains("目前沒有待派單");
    assertThat(embed.getFooter()).isNotNull();
    assertThat(embed.getFooter().getText()).isEqualTo("限制：護航者與客戶不可為同一人");
  }

  @Test
  @DisplayName("buildPanelEmbed 應加入狀態欄位")
  void buildPanelEmbedShouldIncludeStatusField() {
    MessageEmbed embed = DispatchPanelView.buildPanelEmbed("<@1>", "<@2>", "可建立派單");

    assertThat(embed.getFields()).hasSize(4);
    assertThat(embed.getFields().get(3).getName()).isEqualTo("狀態");
    assertThat(embed.getFields().get(3).getValue()).isEqualTo("可建立派單");
  }

  @Test
  @DisplayName("模式面板應提供開單與派單選擇")
  void buildModeComponentsShouldProvideCreateAndAssignModes() {
    List<ActionRow> rows = DispatchPanelView.buildModeComponents();

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).getComponents().get(0)).isInstanceOf(StringSelectMenu.class);
    StringSelectMenu select = (StringSelectMenu) rows.get(0).getComponents().get(0);
    assertThat(select.getOptions())
        .extracting(option -> option.getValue())
        .containsExactly(DispatchPanelView.MODE_CREATE, DispatchPanelView.MODE_ASSIGN);
  }

  @Test
  @DisplayName("開單面板應提供客戶選擇與護航品類")
  void buildCreateOrderComponentsShouldProvideCustomerAndOptionSelectors() {
    List<ActionRow> rows = DispatchPanelView.buildCreateOrderComponents(false, null);

    assertThat(rows.get(0).getComponents().get(0)).isInstanceOf(EntitySelectMenu.class);
    assertThat(rows.get(1).getComponents().get(0)).isInstanceOf(StringSelectMenu.class);
    assertThat(rows.get(rows.size() - 1).getButtons().get(0).getId())
        .isEqualTo(DispatchPanelView.BUTTON_CREATE_ORDER);
    assertThat(rows.get(rows.size() - 1).getButtons().get(0).isDisabled()).isTrue();
  }

  @Test
  @DisplayName("派單面板應顯示待派單訂單並可選擇護航者")
  void buildAssignOrderEmbedShouldRenderPendingOrders() {
    EscortDispatchOrder order =
        EscortDispatchOrder.createManualOpenOrder(
            "ESC-20260427-OPEN01", 1L, 10L, 30L, "CONF_HOURLY_1H");

    MessageEmbed embed = DispatchPanelView.buildAssignOrderEmbed(List.of(order), null, null, null);
    List<ActionRow> rows =
        DispatchPanelView.buildAssignOrderComponents(List.of(order), order.orderNumber(), true);

    assertThat(embed.getFields().get(0).getValue()).contains("ESC-20260427-OPEN01");
    assertThat(embed.getFields().get(0).getValue()).contains("CONF_HOURLY_1H");
    assertThat(rows.get(0).getComponents().get(0)).isInstanceOf(StringSelectMenu.class);
    assertThat(rows.get(1).getComponents().get(0)).isInstanceOf(EntitySelectMenu.class);
    assertThat(rows.get(2).getButtons().get(0).isDisabled()).isFalse();
  }

  @Test
  @DisplayName("超過 25 筆待派單時應拆分為多個 StringSelectMenu")
  void moreThan25PendingOrdersShouldSplitMenu() {
    List<EscortDispatchOrder> orders =
        java.util.stream.IntStream.range(0, 30)
            .mapToObj(
                i ->
                    EscortDispatchOrder.createManualOpenOrder(
                        "ESC-" + String.format("ORDER%04d", i), 1L, 10L, 30L, "CONF_HOURLY_1H"))
            .toList();

    List<ActionRow> rows = DispatchPanelView.buildAssignOrderComponents(orders, null, true);

    // 2 select menu rows (25 + 5) + 1 entity select row + 1 button row
    assertThat(rows).hasSize(4);
    assertThat(rows.get(0).getComponents().get(0)).isInstanceOf(StringSelectMenu.class);
    assertThat(rows.get(1).getComponents().get(0)).isInstanceOf(StringSelectMenu.class);
    assertThat(rows.get(2).getComponents().get(0)).isInstanceOf(EntitySelectMenu.class);
    assertThat(rows.get(3).getButtons()).hasSize(3);

    StringSelectMenu firstMenu = (StringSelectMenu) rows.get(0).getComponents().get(0);
    StringSelectMenu secondMenu = (StringSelectMenu) rows.get(1).getComponents().get(0);
    assertThat(firstMenu.getOptions()).hasSize(25);
    assertThat(secondMenu.getOptions()).hasSize(5);
  }

  @Test
  @DisplayName("buildPanelComponents 應建立兩個選單列與一個按鈕列")
  void buildPanelComponentsShouldBuildRowsInExpectedOrder() {
    List<ActionRow> rows = DispatchPanelView.buildPanelComponents(false);

    assertThat(rows).hasSize(3);
    assertThat(rows.get(0).getComponents().get(0)).isInstanceOf(EntitySelectMenu.class);
    assertThat(rows.get(1).getComponents().get(0)).isInstanceOf(EntitySelectMenu.class);
    assertThat(rows.get(2).getButtons()).hasSize(2);
    assertThat(rows.get(2).getButtons().get(0).getId())
        .isEqualTo(DispatchPanelView.BUTTON_CREATE_ORDER);
    assertThat(rows.get(2).getButtons().get(1).getId()).isEqualTo(DispatchPanelView.BUTTON_HISTORY);
  }

  @Test
  @DisplayName("buildPanelComponents 應依可建立狀態切換建立派單按鈕")
  void buildPanelComponentsShouldToggleCreateButtonState() {
    List<ActionRow> disabledRows = DispatchPanelView.buildPanelComponents(false);
    List<ActionRow> enabledRows = DispatchPanelView.buildPanelComponents(true);

    assertThat(disabledRows.get(2).getButtons().get(0).isDisabled()).isTrue();
    assertThat(enabledRows.get(2).getButtons().get(0).isDisabled()).isFalse();
    assertThat(enabledRows.get(2).getButtons().get(1).isDisabled()).isFalse();
  }
}
