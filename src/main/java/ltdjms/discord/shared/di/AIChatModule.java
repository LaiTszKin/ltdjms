package ltdjms.discord.shared.di;

import javax.inject.Singleton;
import javax.sql.DataSource;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.aiagent.services.AIAgentChannelConfigService;
import ltdjms.discord.aichat.commands.AIChatMentionListener;
import ltdjms.discord.aichat.domain.AIServiceConfig;
import ltdjms.discord.aichat.persistence.AIChannelRestrictionRepository;
import ltdjms.discord.aichat.persistence.JdbcAIChannelRestrictionRepository;
import ltdjms.discord.aichat.services.AIChannelRestrictionService;
import ltdjms.discord.aichat.services.AIChatService;
import ltdjms.discord.aichat.services.DefaultAIChannelRestrictionService;
import ltdjms.discord.shared.EnvironmentConfig;

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
  public AIChannelRestrictionRepository provideAIChannelRestrictionRepository(
      DataSource dataSource) {
    return new JdbcAIChannelRestrictionRepository(dataSource);
  }

  @Provides
  @Singleton
  public AIChannelRestrictionService provideAIChannelRestrictionService(
      AIChannelRestrictionRepository repository) {
    return new DefaultAIChannelRestrictionService(repository);
  }

  @Provides
  @Singleton
  public AIChatMentionListener provideAIChatMentionListener(
      AIChatService aiChatService,
      AIChannelRestrictionService channelRestrictionService,
      AIAgentChannelConfigService agentConfigService,
      AIServiceConfig config) {
    return new AIChatMentionListener(
        aiChatService, channelRestrictionService, agentConfigService, config.showReasoning());
  }
}
