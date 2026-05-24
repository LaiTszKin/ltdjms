package ltdjms.discord.discord.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

@DisplayName("SelectMenuUtil 測試")
class SelectMenuUtilTest {

  private static final String SELECT_ID = "test_select";
  private static final String PLACEHOLDER = "請選擇項目";

  @Test
  @DisplayName("空列表應回傳空列表")
  void emptyListShouldReturnEmpty() {
    assertThat(SelectMenuUtil.splitSelectMenu(SELECT_ID, PLACEHOLDER, List.of(), (b, i) -> {}))
        .isEmpty();
  }

  @Test
  @DisplayName("null 應回傳空列表")
  void nullShouldReturnEmpty() {
    assertThat(SelectMenuUtil.splitSelectMenu(SELECT_ID, PLACEHOLDER, null, (b, i) -> {}))
        .isEmpty();
  }

  @Test
  @DisplayName("1-25 個選項應回傳單一選單")
  void upTo25OptionsShouldReturnSingleMenu() {
    List<String> items = IntStream.range(0, 25).mapToObj(i -> "item" + i).toList();

    var menus =
        SelectMenuUtil.splitSelectMenu(
            SELECT_ID, PLACEHOLDER, items, (b, item) -> b.addOption(item, item));

    assertThat(menus).hasSize(1);
    assertThat(menus.get(0).getOptions()).hasSize(25);
    assertThat(menus.get(0).getId()).isEqualTo(SELECT_ID);
  }

  @Test
  @DisplayName("26 個選項應拆分為 2 個選單（25 + 1）")
  void moreThan25OptionsShouldSplit() {
    List<String> items = IntStream.range(0, 26).mapToObj(i -> "item" + i).toList();

    var menus =
        SelectMenuUtil.splitSelectMenu(
            SELECT_ID, PLACEHOLDER, items, (b, item) -> b.addOption(item, item));

    assertThat(menus).hasSize(2);
    assertThat(menus.get(0).getOptions()).hasSize(25);
    assertThat(menus.get(1).getOptions()).hasSize(1);
    assertThat(menus.get(0).getId()).isEqualTo(SELECT_ID);
    assertThat(menus.get(1).getId()).isEqualTo(SELECT_ID);
  }

  @Test
  @DisplayName("50 個選項應拆分為 2 個選單（25 + 25）")
  void exactly50OptionsShouldSplitEvenly() {
    List<String> items = IntStream.range(0, 50).mapToObj(i -> "item" + i).toList();

    var menus =
        SelectMenuUtil.splitSelectMenu(
            SELECT_ID, PLACEHOLDER, items, (b, item) -> b.addOption(item, item));

    assertThat(menus).hasSize(2);
    assertThat(menus.get(0).getOptions()).hasSize(25);
    assertThat(menus.get(1).getOptions()).hasSize(25);
  }

  @Test
  @DisplayName("51 個選項應拆分為 3 個選單（25 + 25 + 1）")
  void moreThan50OptionsShouldSplitIntoThree() {
    List<String> items = IntStream.range(0, 51).mapToObj(i -> "item" + i).toList();

    var menus =
        SelectMenuUtil.splitSelectMenu(
            SELECT_ID, PLACEHOLDER, items, (b, item) -> b.addOption(item, item));

    assertThat(menus).hasSize(3);
    assertThat(menus.get(0).getOptions()).hasSize(25);
    assertThat(menus.get(1).getOptions()).hasSize(25);
    assertThat(menus.get(2).getOptions()).hasSize(1);
  }

  @Test
  @DisplayName("每個選單的選項內容應正確（0-24, 25-49, ...）")
  void itemContentShouldBeCorrect() {
    List<String> items = List.of("zero", "one", "two");

    var menus =
        SelectMenuUtil.splitSelectMenu(
            SELECT_ID,
            PLACEHOLDER,
            items,
            (b, item) -> b.addOption("label:" + item, "value:" + item, "desc:" + item));

    assertThat(menus).hasSize(1);
    var opts = menus.get(0).getOptions();
    assertThat(opts.get(0).getLabel()).isEqualTo("label:zero");
    assertThat(opts.get(0).getValue()).isEqualTo("value:zero");
    assertThat(opts.get(0).getDescription()).isEqualTo("desc:zero");
    assertThat(opts.get(1).getLabel()).isEqualTo("label:one");
    assertThat(opts.get(2).getLabel()).isEqualTo("label:two");
  }

  @Test
  @DisplayName("buildSelectRows 應回傳 List<ActionRow>，每列一個選單")
  void buildSelectRowsShouldReturnActionRows() {
    List<String> items = IntStream.range(0, 30).mapToObj(i -> "item" + i).toList();

    var rows =
        SelectMenuUtil.buildSelectRows(
            SELECT_ID, PLACEHOLDER, items, (b, item) -> b.addOption(item, item));

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).getComponents()).hasSize(1);
    assertThat(rows.get(0).getComponents().get(0)).isInstanceOf(StringSelectMenu.class);
    assertThat(rows.get(1).getComponents().get(0)).isInstanceOf(StringSelectMenu.class);
    assertThat(((StringSelectMenu) rows.get(0).getComponents().get(0)).getOptions()).hasSize(25);
    assertThat(((StringSelectMenu) rows.get(1).getComponents().get(0)).getOptions()).hasSize(5);
  }

  @Test
  @DisplayName("自訂 maxOptionsPerMenu 應生效")
  void customMaxOptionsShouldWork() {
    List<String> items = IntStream.range(0, 10).mapToObj(i -> "item" + i).toList();

    var menus =
        SelectMenuUtil.splitSelectMenu(
            SELECT_ID, PLACEHOLDER, items, (b, item) -> b.addOption(item, item), 3);

    assertThat(menus).hasSize(4); // 3 + 3 + 3 + 1
    assertThat(menus.get(0).getOptions()).hasSize(3);
    assertThat(menus.get(3).getOptions()).hasSize(1);
  }
}
