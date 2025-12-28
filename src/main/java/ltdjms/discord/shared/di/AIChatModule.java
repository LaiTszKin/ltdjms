package ltdjms.discord.shared.di;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.aichat.commands.AIChatMentionListener;
import ltdjms.discord.aichat.domain.AIServiceConfig;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.aichat.services.AIClient;
import ltdjms.discord.aichat.services.DefaultAIChatService;
import ltdjms.discord.aichat.services.DefaultPromptLoader;
import ltdjms.discord.aichat.services.PromptLoader;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.events.DomainEventPublisher;

/** Dagger module providing AI chat service dependencies. */
@Module
public class AIChatModule {

  @Provides
  @Singleton
  public AIServiceConfig provideAIServiceConfig(EnvironmentConfig envConfig) {
    AIServiceConfig config = AIServiceConfig.from(envConfig);
    var validation = config.validate();
    if (validation.isErr()) {
      throw new IllegalStateException(
          "Invalid AI service config: " + validation.getError().message());
    }
    return config;
  }

  @Provides
  @Singleton
  public AIClient provideAIClient(AIServiceConfig config) {
    return new AIClient(config);
  }

  @Provides
  @Singleton
  public PromptLoader providePromptLoader(EnvironmentConfig envConfig) {
    return new DefaultPromptLoader(envConfig);
  }

  @Provides
  @Singleton
  public AIChatService provideAIChatService(
      AIServiceConfig config,
      AIClient aiClient,
      DomainEventPublisher eventPublisher,
      PromptLoader promptLoader) {
    return new DefaultAIChatService(config, aiClient, eventPublisher, promptLoader);
  }

  @Provides
  @Singleton
  public AIChatMentionListener provideAIChatMentionListener(AIChatService aiChatService) {
    return new AIChatMentionListener(aiChatService);
  }
}
