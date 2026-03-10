package ltdjms.discord.dispatch.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;

@DisplayName("DispatchPanelView 測試")
class DispatchPanelViewTest {

  @Test
  @DisplayName("buildPanelEmbed 應在未選取成員時顯示預設文字")
  void buildPanelEmbedShouldShowFallbackWhenUsersAreNotSelected() {
    MessageEmbed embed = DispatchPanelView.buildPanelEmbed(null, "", null);

    assertThat(embed.getTitle()).isEqualTo("🛡️ 護航派單面板");
    assertThat(embed.getDescription()).contains("請選擇護航者與客戶");
    assertThat(embed.getFields()).hasSize(2);
    assertThat(embed.getFields().get(0).getValue()).isEqualTo("尚未選擇");
    assertThat(embed.getFields().get(1).getValue()).isEqualTo("尚未選擇");
    assertThat(embed.getFooter()).isNotNull();
    assertThat(embed.getFooter().getText()).isEqualTo("限制：護航者與客戶不可為同一人");
  }

  @Test
  @DisplayName("buildPanelEmbed 應加入狀態欄位")
  void buildPanelEmbedShouldIncludeStatusField() {
    MessageEmbed embed = DispatchPanelView.buildPanelEmbed("<@1>", "<@2>", "可建立派單");

    assertThat(embed.getFields()).hasSize(3);
    assertThat(embed.getFields().get(2).getName()).isEqualTo("狀態");
    assertThat(embed.getFields().get(2).getValue()).isEqualTo("可建立派單");
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
