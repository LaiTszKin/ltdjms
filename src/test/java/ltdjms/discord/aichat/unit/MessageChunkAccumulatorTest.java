package ltdjms.discord.aichat.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ltdjms.discord.aichat.services.MessageChunkAccumulator;

/** MessageChunkAccumulator 單元測試。 */
class MessageChunkAccumulatorTest {

  @Test
  void testAccumulate_paragraphSplit_shouldReturnChunk() {
    MessageChunkAccumulator accumulator = new MessageChunkAccumulator();

    List<String> chunks = accumulator.accumulate("第一段");
    assertThat(chunks).isEmpty();

    chunks = accumulator.accumulate("內容\n\n");
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).isEqualTo("第一段內容\n\n");

    // 緩衝區應該被清空
    chunks = accumulator.accumulate("第二段內容");
    assertThat(chunks).isEmpty();
  }

  @Test
  void testAccumulate_singleNewline_shouldNotSplit() {
    MessageChunkAccumulator accumulator = new MessageChunkAccumulator();

    List<String> chunks = accumulator.accumulate("第一行\n");
    assertThat(chunks).isEmpty();

    chunks = accumulator.accumulate("第二行\n第三行");
    assertThat(chunks).isEmpty();

    // 只有段落分割（\n\n）才會分割
    chunks = accumulator.accumulate("\n\n");
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).isEqualTo("第一行\n第二行\n第三行\n\n");
  }

  @Test
  void testAccumulate_forceSplit_whenExceedsMaxLength() {
    MessageChunkAccumulator accumulator = new MessageChunkAccumulator();

    // 累積超過 1980 字元
    String longText = "A".repeat(2000);
    List<String> chunks = accumulator.accumulate(longText);

    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).hasSize(1980);

    // 剩餘內容應該少於 20 字元
    String remaining = accumulator.drain();
    assertThat(remaining).hasSize(20);
  }

  @Test
  void testDrain_shouldReturnRemainingContent() {
    MessageChunkAccumulator accumulator = new MessageChunkAccumulator();

    accumulator.accumulate("未完成的");
    accumulator.accumulate("內容");

    String remaining = accumulator.drain();
    assertThat(remaining).isEqualTo("未完成的內容");

    // drain 後緩衝區應該被清空
    remaining = accumulator.drain();
    assertThat(remaining).isEmpty();
  }

  @Test
  void testAccumulate_emptyDelta_shouldNotThrow() {
    MessageChunkAccumulator accumulator = new MessageChunkAccumulator();

    List<String> chunks = accumulator.accumulate("");
    assertThat(chunks).isEmpty();

    chunks = accumulator.accumulate(null);
    assertThat(chunks).isEmpty();

    accumulator.accumulate("內容");
    chunks = accumulator.accumulate("");
    assertThat(chunks).isEmpty();
  }

  @Test
  void testAccumulate_priority_paragraphOverForceSplit() {
    MessageChunkAccumulator accumulator = new MessageChunkAccumulator();

    // 段落分割優先級高於強制分割
    List<String> chunks = accumulator.accumulate("A".repeat(1900));
    assertThat(chunks).isEmpty();

    // 加入段落分割，應該在段落處分割而不是強制分割
    chunks = accumulator.accumulate("\n\n");
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).hasSize(1902); // 1900 + \n\n

    // 繼續累積
    chunks = accumulator.accumulate("更多內容");
    assertThat(chunks).isEmpty();

    // 檢查剩餘內容
    String remaining = accumulator.drain();
    assertThat(remaining).isEqualTo("更多內容");
  }

  @Test
  void testAccumulate_mixedContent_shouldHandleCorrectly() {
    MessageChunkAccumulator accumulator = new MessageChunkAccumulator();

    // 混合內容測試
    List<String> chunks = accumulator.accumulate("這是第一段。\n\n");
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).isEqualTo("這是第一段。\n\n");

    chunks = accumulator.accumulate("這是第二段內容！還有更多內容");
    assertThat(chunks).isEmpty();

    // 段落分割
    chunks = accumulator.accumulate("\n\n");
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).isEqualTo("這是第二段內容！還有更多內容\n\n");

    // 繼續累積
    chunks = accumulator.accumulate("第三段");
    assertThat(chunks).isEmpty();

    // 緩衝區應該有剩餘內容
    String remaining = accumulator.drain();
    assertThat(remaining).isEqualTo("第三段");
  }

  @Test
  void testDrain_trimsWhitespace() {
    MessageChunkAccumulator accumulator = new MessageChunkAccumulator();

    accumulator.accumulate("  內容  ");
    String remaining = accumulator.drain();
    assertThat(remaining).isEqualTo("內容");
  }

  @Test
  void testAccumulate_multipleParagraphs_shouldSplitCorrectly() {
    MessageChunkAccumulator accumulator = new MessageChunkAccumulator();

    // 多個段落
    List<String> chunks = accumulator.accumulate("第一段\n\n第二段\n\n第三段");
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).isEqualTo("第一段\n\n");

    // 繼續調用獲取第二段
    chunks = accumulator.accumulate("");
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).isEqualTo("第二段\n\n");

    // 第三段沒有 \n\n，所以不會自動分割，需要使用 drain() 獲取
    chunks = accumulator.accumulate("");
    assertThat(chunks).isEmpty();

    // 使用 drain 獲取剩餘內容
    String remaining = accumulator.drain();
    assertThat(remaining).isEqualTo("第三段");
  }

  @Test
  void testAccumulate_longParagraph_shouldForceSplit() {
    MessageChunkAccumulator accumulator = new MessageChunkAccumulator();

    // 單一段落超過 1980 字元
    String longParagraph = "A".repeat(2100);
    List<String> chunks = accumulator.accumulate(longParagraph);

    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).hasSize(1980);

    // 剩餘內容
    String remaining = accumulator.drain();
    assertThat(remaining).hasSize(120);
  }
}
