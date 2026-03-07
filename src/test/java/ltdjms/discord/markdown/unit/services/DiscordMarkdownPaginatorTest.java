package ltdjms.discord.markdown.unit.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ltdjms.discord.markdown.services.DiscordMarkdownPaginator;

@DisplayName("DiscordMarkdownPaginator")
class DiscordMarkdownPaginatorTest {

  @Test
  @DisplayName("長程式碼區塊分頁後每頁都不應超過 Discord 長度上限")
  void longCodeBlock_shouldKeepEveryPageWithinLimit() {
    DiscordMarkdownPaginator paginator = new DiscordMarkdownPaginator();
    String content = "```java\n" + "a".repeat(6000) + "\n```";

    List<String> pages = paginator.paginate(content);

    assertThat(pages).isNotEmpty();
    assertThat(pages).allSatisfy(page -> assertThat(page.length()).isLessThanOrEqualTo(1900));
  }
}
