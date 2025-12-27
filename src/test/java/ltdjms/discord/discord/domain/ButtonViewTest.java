package ltdjms.discord.discord.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

/**
 * ButtonView 單元測試
 *
 * <p>測試 ButtonView record 的驗證邏輯和 toJdaButton 方法。
 */
@DisplayName("ButtonView 測試")
class ButtonViewTest {

  private static final String TEST_ID = "test_button";
  private static final String TEST_LABEL = "測試按鈕";

  @Test
  @DisplayName("建構子應該正確建立 ButtonView")
  void constructorShouldCreateButtonView() {
    ButtonView view = new ButtonView(TEST_ID, TEST_LABEL, ButtonStyle.PRIMARY, false);

    assertThat(view.id()).isEqualTo(TEST_ID);
    assertThat(view.label()).isEqualTo(TEST_LABEL);
    assertThat(view.style()).isEqualTo(ButtonStyle.PRIMARY);
    assertThat(view.disabled()).isFalse();
  }

  @Test
  @DisplayName("建構子應該接受 null id")
  void constructorShouldAcceptNullId() {
    ButtonView view = new ButtonView(null, TEST_LABEL, ButtonStyle.PRIMARY, false);

    assertThat(view.id()).isNull();
  }

  @Test
  @DisplayName("建構子應該接受 null label")
  void constructorShouldAcceptNullLabel() {
    ButtonView view = new ButtonView(TEST_ID, null, ButtonStyle.PRIMARY, false);

    assertThat(view.label()).isNull();
  }

  @Test
  @DisplayName("建構子應該拋出異常當 id 超過 100 字元")
  void constructorShouldThrowWhenIdExceeds100Characters() {
    String longId = "a".repeat(101);

    assertThatThrownBy(() -> new ButtonView(longId, TEST_LABEL, ButtonStyle.PRIMARY, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不可超過 100 字元");
  }

  @Test
  @DisplayName("建構子應該拋出異常當 label 超過 80 字元")
  void constructorShouldThrowWhenLabelExceeds80Characters() {
    String longLabel = "a".repeat(81);

    assertThatThrownBy(() -> new ButtonView(TEST_ID, longLabel, ButtonStyle.PRIMARY, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不可超過 80 字元");
  }

  @Test
  @DisplayName("建構子應該接受剛好 100 字元的 id")
  void constructorShouldAcceptExactly100CharacterId() {
    String id = "a".repeat(100);

    ButtonView view = new ButtonView(id, TEST_LABEL, ButtonStyle.PRIMARY, false);

    assertThat(view.id()).hasSize(100);
  }

  @Test
  @DisplayName("建構子應該接受剛好 80 字元的 label")
  void constructorShouldAcceptExactly80CharacterLabel() {
    String label = "a".repeat(80);

    ButtonView view = new ButtonView(TEST_ID, label, ButtonStyle.PRIMARY, false);

    assertThat(view.label()).hasSize(80);
  }

  @Test
  @DisplayName("toJdaButton 應該正確轉換為 JDA Button")
  void toJdaButtonShouldConvertToJdaButton() {
    ButtonView view = new ButtonView(TEST_ID, TEST_LABEL, ButtonStyle.PRIMARY, false);

    Button button = view.toJdaButton();

    assertThat(button).isNotNull();
    assertThat(button.getId()).isEqualTo(TEST_ID);
    assertThat(button.getLabel()).isEqualTo(TEST_LABEL);
    assertThat(button.getStyle()).isEqualTo(ButtonStyle.PRIMARY);
    assertThat(button.isDisabled()).isFalse();
  }

  @Test
  @DisplayName("toJdaButton 應該正確處理 disabled 狀態")
  void toJdaButtonShouldHandleDisabledState() {
    ButtonView view = new ButtonView(TEST_ID, TEST_LABEL, ButtonStyle.PRIMARY, true);

    Button button = view.toJdaButton();

    assertThat(button.isDisabled()).isTrue();
  }

  @Test
  @DisplayName("toJdaButton 應該正確處理 null label（link 樣式）")
  void toJdaButtonShouldHandleNullLabel() {
    // Link 樣式不要求 label，但可以為 null
    ButtonView view = new ButtonView(TEST_ID, null, ButtonStyle.LINK, false);

    // 這會拋出異常，因為 JDA Button 要求至少有 label 或 emoji
    // 所以我們測試這個行為
    assertThatThrownBy(() -> view.toJdaButton())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("emoji or label");
  }

  @Test
  @DisplayName("toJdaButton 應該正確處理不同樣式")
  void toJdaButtonShouldHandleDifferentStyles() {
    ButtonView primaryView = new ButtonView(TEST_ID, TEST_LABEL, ButtonStyle.PRIMARY, false);
    ButtonView secondaryView = new ButtonView(TEST_ID, TEST_LABEL, ButtonStyle.SECONDARY, false);
    ButtonView successView = new ButtonView(TEST_ID, TEST_LABEL, ButtonStyle.SUCCESS, false);
    ButtonView dangerView = new ButtonView(TEST_ID, TEST_LABEL, ButtonStyle.DANGER, false);
    ButtonView linkView = new ButtonView(TEST_ID, TEST_LABEL, ButtonStyle.LINK, false);

    assertThat(primaryView.toJdaButton().getStyle()).isEqualTo(ButtonStyle.PRIMARY);
    assertThat(secondaryView.toJdaButton().getStyle()).isEqualTo(ButtonStyle.SECONDARY);
    assertThat(successView.toJdaButton().getStyle()).isEqualTo(ButtonStyle.SUCCESS);
    assertThat(dangerView.toJdaButton().getStyle()).isEqualTo(ButtonStyle.DANGER);
    assertThat(linkView.toJdaButton().getStyle()).isEqualTo(ButtonStyle.LINK);
  }

  @Test
  @DisplayName("toJdaButton 應該建立新的 Button 實例每次呼叫")
  void toJdaButtonShouldCreateNewButtonInstanceEachCall() {
    ButtonView view = new ButtonView(TEST_ID, TEST_LABEL, ButtonStyle.PRIMARY, false);

    Button button1 = view.toJdaButton();
    Button button2 = view.toJdaButton();

    // 每次呼叫應該建立新的實例
    assertThat(button1).isNotSameAs(button2);
    // 但內容應該相同
    assertThat(button1.getId()).isEqualTo(button2.getId());
    assertThat(button1.getLabel()).isEqualTo(button2.getLabel());
    assertThat(button1.getStyle()).isEqualTo(button2.getStyle());
    assertThat(button1.isDisabled()).isEqualTo(button2.isDisabled());
  }

  @Test
  @DisplayName("disabled 應該正確反映設定的值")
  void disabledShouldReflectSetValue() {
    ButtonView enabledView = new ButtonView(TEST_ID, TEST_LABEL, ButtonStyle.PRIMARY, false);
    ButtonView disabledView = new ButtonView(TEST_ID, TEST_LABEL, ButtonStyle.PRIMARY, true);

    assertThat(enabledView.disabled()).isFalse();
    assertThat(disabledView.disabled()).isTrue();
  }
}
