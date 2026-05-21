import type { DomainEvent } from '@ltdjms/shared';

export interface AIAgentChannelConfigChangedEvent extends DomainEvent {
  readonly eventType: 'ai_agent_channel_config_changed';
  readonly guildId: string;
  readonly channelId: string;
  readonly agentEnabled: boolean;
  readonly changedAt: Date;
}

export interface AgentFailedEvent extends DomainEvent {
  readonly eventType: 'agent_failed';
  readonly guildId: string;
  readonly channelId: string;
  readonly userId: string;
  readonly conversationId: string;
  readonly reason: string;
  readonly timestamp: Date;
}

export interface AIChannelConfigChangedEvent extends DomainEvent {
  readonly eventType: 'ai_channel_config_changed';
  readonly guildId: string;
  readonly changeType: 'channel_added' | 'channel_removed' | 'category_added' | 'category_removed';
  readonly targetId: string;
}
