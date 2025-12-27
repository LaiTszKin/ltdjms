package ltdjms.discord.shared.di;

import dagger.Module;
import dagger.Provides;
import ltdjms.discord.discord.domain.DiscordEmbedBuilder;
import ltdjms.discord.discord.services.JdaDiscordEmbedBuilder;

import javax.inject.Singleton;

/**
 * Dagger module providing Discord API abstraction layer dependencies.
 *
 * <p>This module provides:
 * <ul>
 *   <li>DiscordInteraction - unified Discord interaction interface</li>
 *   <li>DiscordContext - Discord event context extraction interface</li>
 *   <li>DiscordEmbedBuilder - Discord view component builder interface</li>
 *   <li>DiscordSessionManager - Discord session management interface</li>
 * </ul>
 */
@Module
public class DiscordModule {

    /**
     * Provides JDA implementation of DiscordEmbedBuilder.
     *
     * <p>此提供者返回一個新的 JdaDiscordEmbedBuilder 實例，
     * 用於建構符合 Discord API 規範的 Embed 訊息。
     *
     * @return a new DiscordEmbedBuilder instance
     */
    @Provides
    @Singleton
    public DiscordEmbedBuilder provideDiscordEmbedBuilder() {
        return new JdaDiscordEmbedBuilder();
    }

    // 註冊的抽象層元件：
    //
    // User Story 1: DiscordInteraction
    //   - 介面: ltdjms.discord.discord.domain.DiscordInteraction
    //   - JDA 實作: ltdjms.discord.discord.services.JdaDiscordInteraction
    //   - Mock 實作: ltdjms.discord.discord.mock.MockDiscordInteraction
    //   - Adapter: ltdjms.discord.discord.adapter.SlashCommandAdapter
    //
    // User Story 4: DiscordContext
    //   - 介面: ltdjms.discord.discord.domain.DiscordContext
    //   - JDA 實作: ltdjms.discord.discord.services.JdaDiscordContext
    //   - Mock 實作: ltdjms.discord.discord.mock.MockDiscordContext
    //
    // User Story 2: DiscordEmbedBuilder (已註冊)
    //   - 介面: ltdjms.discord.discord.domain.DiscordEmbedBuilder
    //   - JDA 實作: ltdjms.discord.discord.services.JdaDiscordEmbedBuilder
    //   - Mock 實作: ltdjms.discord.discord.mock.MockDiscordEmbedBuilder
    //
    // User Story 3: DiscordSessionManager
    //   - 介面: ltdjms.discord.discord.domain.DiscordSessionManager
    //   - 泛型實作: ltdjms.discord.discord.services.InteractionSessionManager
    //   - SessionType: ltdjms.discord.discord.domain.SessionType
    //   - Button Adapter: ltdjms.discord.discord.adapter.ButtonInteractionAdapter
    //
    // 注意：DiscordInteraction 和 DiscordContext 已在 User Stories 1 和 4 中實作，
    // 但尚未在此模組中提供 @Provides 方法，因為它們目前是透過 Adapter 直接建立的。
    // 未來可以考慮將它們也納入 DI 容器管理。
}
