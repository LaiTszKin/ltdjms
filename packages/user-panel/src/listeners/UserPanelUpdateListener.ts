import type { DomainEvent } from '@ltdjms/shared';
import { type DiscordRuntimeGateway } from '@ltdjms/shared';
import type {
  BalanceChangedEvent,
  CurrencyConfigChangedEvent,
} from '@ltdjms/economy';
import type { GameTokenChangedEvent } from '@ltdjms/games';
import { type Client, type TextChannel, EmbedBuilder } from 'discord.js';
import { PanelSessionManager } from '../session/PanelSessionManager.js';
import { UserPanelService } from '../services/UserPanelService.js';
import { UserPanelEmbedBuilder } from '../services/UserPanelEmbedBuilder.js';
import { USER_PANEL_FOOTER_PUSH_UPDATE } from '../constants/UserPanelConstants.js';

const EVENT_TYPES = {
  BALANCE_CHANGED: 'balance_changed',
  GAME_TOKEN_CHANGED: 'game_token_changed',
  CURRENCY_CONFIG_CHANGED: 'currency_config_changed',
} as const;

/**
 * Listens to domain events and updates active user panel sessions.
 * Mirrors Java UserPanelUpdateListener (embed-only updates).
 */
export class UserPanelUpdateListener {
  constructor(
    private readonly sessionManager: PanelSessionManager,
    private readonly userPanelService: UserPanelService,
    private readonly discordGateway: DiscordRuntimeGateway,
    private readonly embedBuilder: UserPanelEmbedBuilder = new UserPanelEmbedBuilder(),
  ) {}

  async onEvent(event: DomainEvent): Promise<void> {
    if (!this.isRelevantEvent(event)) return;

    const guildId = String(event.guildId);

    if (event.eventType === EVENT_TYPES.CURRENCY_CONFIG_CHANGED) {
      await this.updateAllGuildPanels(guildId);
      return;
    }

    if (event.eventType === EVENT_TYPES.BALANCE_CHANGED) {
      const userId = String((event as BalanceChangedEvent).userId);
      await this.updateUserPanel(guildId, userId);
      return;
    }

    if (event.eventType === EVENT_TYPES.GAME_TOKEN_CHANGED) {
      const userId = String((event as GameTokenChangedEvent).userId);
      await this.updateUserPanel(guildId, userId);
    }
  }

  private async updateAllGuildPanels(guildId: string): Promise<void> {
    const sessions = this.sessionManager.getAllForGuild(guildId);
    for (const session of sessions) {
      await this.updateUserPanel(guildId, session.userId);
    }
  }

  private async updateUserPanel(guildId: string, userId: string): Promise<void> {
    const session = this.sessionManager.getSession(guildId, userId);
    if (!session?.channelId || !session.messageId) return;

    const result = await this.userPanelService.getUserPanelView(guildId, userId);
    if (result.isErr()) return;

    const view = result.getValue();
    const userMention = `<@${userId}>`;
    const embedData = this.embedBuilder.buildPanelEmbed(
      view,
      userMention,
      USER_PANEL_FOOTER_PUSH_UPDATE,
    );

    const embed = new EmbedBuilder()
      .setTitle(embedData.title)
      .setDescription(embedData.description)
      .addFields(embedData.fields)
      .setColor(embedData.color)
      .setFooter({ text: embedData.footer ?? '' });

    try {
      const client = this.discordGateway.requireReadyClient() as Client;
      const channel = await client.channels.fetch(session.channelId);
      if (!channel?.isTextBased()) return;

      const message = await (channel as TextChannel).messages.fetch(session.messageId);
      await message.edit({ embeds: [embed] });
    } catch {
      this.sessionManager.removeSession(guildId, userId);
    }
  }

  private isRelevantEvent(event: DomainEvent): boolean {
    return (
      event.eventType === EVENT_TYPES.BALANCE_CHANGED ||
      event.eventType === EVENT_TYPES.GAME_TOKEN_CHANGED ||
      event.eventType === EVENT_TYPES.CURRENCY_CONFIG_CHANGED
    );
  }
}
