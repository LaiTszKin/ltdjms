package ltdjms.discord.shared.di;

import java.util.List;
import javax.inject.Singleton;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.aichat.domain.AIServiceConfig;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.aichat.services.LangChain4jAIChatService;
import ltdjms.discord.markdown.services.MarkdownValidatingAIChatService;
import ltdjms.discord.markdown.validation.CommonMarkValidator;
import ltdjms.discord.markdown.validation.MarkdownErrorFormatter;
import ltdjms.discord.markdown.validation.MarkdownValidator;

/** Markdown 驗證功能的 Dagger 模組 提供驗證器和裝飾後的 AIChatService */
@Module
public interface MarkdownValidationModule {

  @Provides
  @Singleton
  static org.commonmark.parser.Parser provideCommonMarkParser() {
    return org.commonmark.parser.Parser.builder()
        .extensions(List.of(TablesExtension.create(), TaskListItemsExtension.create()))
        .build();
  }

  @Provides
  @Singleton
  static org.commonmark.renderer.html.HtmlRenderer provideCommonMarkHtmlRenderer() {
    return org.commonmark.renderer.html.HtmlRenderer.builder()
        .extensions(List.of(TablesExtension.create(), TaskListItemsExtension.create()))
        .build();
  }

  @Provides
  @Singleton
  static MarkdownValidator provideMarkdownValidator(
      org.commonmark.parser.Parser parser, org.commonmark.renderer.html.HtmlRenderer renderer) {
    return new CommonMarkValidator(parser, renderer);
  }

  @Provides
  @Singleton
  static MarkdownErrorFormatter provideMarkdownErrorFormatter() {
    return new MarkdownErrorFormatter();
  }

  @Provides
  @Singleton
  static AIChatService provideValidatingAIChatService(
      AIServiceConfig config,
      LangChain4jAIChatService delegateService,
      MarkdownValidator validator,
      MarkdownErrorFormatter formatter) {

    if (!config.enableMarkdownValidation()) {
      return delegateService;
    }

    return new MarkdownValidatingAIChatService(
        delegateService,
        validator,
        true,
        formatter,
        config.maxMarkdownValidationRetries(),
        config.streamingBypassValidation());
  }
}
