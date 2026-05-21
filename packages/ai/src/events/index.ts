import type { DomainEvent } from '@ltdjms/shared';

export interface AIMessageEvent extends DomainEvent {
  readonly eventType: 'ai_message';
  readonly guildId: string;
  readonly channelId: string;
  readonly threadId: number | null;
  readonly userId: string;
  readonly userMessage: string;
  readonly aiResponse: string;
  readonly timestamp: Date;
  readonly messageId: number;
}

export interface AIAgentChannelConfigChangedEvent extends DomainEvent {
  readonly eventType: 'ai_agent_channel_config_changed';
  readonly guildId: string;
  readonly channelId: number;
  readonly agentEnabled: boolean;
  readonly changedAt: Date;
}

export interface ConversationMessage {
  readonly role: string;
  readonly content: string;
}

export interface AgentCompletedEvent extends DomainEvent {
  readonly eventType: 'agent_completed';
  readonly guildId: string;
  readonly channelId: string;
  readonly userId: string;
  readonly conversationId: string;
  readonly finalResponse: string;
  readonly fullHistory: ConversationMessage[];
  readonly timestamp: Date;
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

export interface ToolExecutionStartedEvent extends DomainEvent {
  readonly eventType: 'tool_execution_started';
  readonly guildId: string;
  readonly channelId: number;
  readonly userId: number;
  readonly toolName: string;
  readonly timestamp: Date;
}

export interface ToolExecutedEvent extends DomainEvent {
  readonly eventType: 'tool_executed';
  readonly guildId: string;
  readonly channelId: number;
  readonly userId: number;
  readonly toolName: string;
  readonly result: string;
  readonly success: boolean;
  readonly timestamp: Date;
}
