import { describe, it, expect, beforeEach } from 'vitest';
import {
  InMemoryToolCallHistory,
  ConversationIdBuilder,
} from '../../services/memory/tool-call-history.js';
import { RedactionMode, ConversationIdStrategy } from '../../services/ai-chat-service.js';

describe('InMemoryToolCallHistory', () => {
  let history: InMemoryToolCallHistory;

  beforeEach(() => {
    history = new InMemoryToolCallHistory();
  });

  it('should add and retrieve tool call entries', () => {
    history.addToolCall('thread-1', 'user-1', {
      timestamp: new Date(),
      toolName: 'create_channel',
      parameters: { name: 'test' },
      success: true,
      memorySummary: 'Created channel test',
      redactionMode: RedactionMode.NONE,
    });

    const entries = history.getToolCallMessages('thread-1', 'user-1');
    expect(entries).toHaveLength(1);
    expect(entries[0].toolName).toBe('create_channel');
  });

  it('should separate history by different conversations', () => {
    history.addToolCall('thread-1', 'user-1', {
      timestamp: new Date(),
      toolName: 'create_channel',
      parameters: {},
      success: true,
      memorySummary: 'Ch-1',
      redactionMode: RedactionMode.NONE,
    });

    history.addToolCall('thread-2', 'user-2', {
      timestamp: new Date(),
      toolName: 'create_role',
      parameters: {},
      success: true,
      memorySummary: 'Role-2',
      redactionMode: RedactionMode.NONE,
    });

    expect(history.getToolCallMessages('thread-1', 'user-1')).toHaveLength(1);
    expect(history.getToolCallMessages('thread-2', 'user-2')).toHaveLength(1);
  });

  it('should enforce max 50 entries (FIFO eviction)', () => {
    // Add 55 entries
    for (let i = 0; i < 55; i++) {
      history.addToolCall('thread-1', 'user-1', {
        timestamp: new Date(),
        toolName: `tool-${i}`,
        parameters: {},
        success: true,
        memorySummary: `Entry ${i}`,
        redactionMode: RedactionMode.NONE,
      });
    }

    const entries = history.getToolCallMessages('thread-1', 'user-1');
    expect(entries).toHaveLength(50);
    // First 5 entries should be evicted (FIFO)
    expect(entries[0].toolName).toBe('tool-5');
  });

  it('should filter OMITTED entries from tool call messages', () => {
    history.addToolCall('thread-1', 'user-1', {
      timestamp: new Date(),
      toolName: 'visible',
      parameters: {},
      success: true,
      memorySummary: 'Visible',
      redactionMode: RedactionMode.NONE,
    });

    history.addToolCall('thread-1', 'user-1', {
      timestamp: new Date(),
      toolName: 'hidden',
      parameters: {},
      success: true,
      memorySummary: 'Hidden',
      redactionMode: RedactionMode.OMITTED,
    });

    const entries = history.getToolCallMessages('thread-1', 'user-1');
    expect(entries).toHaveLength(1);
    expect(entries[0].toolName).toBe('visible');
  });

  it('should include REDACTED entries but with safe summaries', () => {
    history.addToolCall('thread-1', 'user-1', {
      timestamp: new Date(),
      toolName: 'search_messages',
      parameters: { keywords: 'secret' },
      success: true,
      memorySummary: '[REDACTED] 已搜尋關鍵字「secret」的相關訊息',
      redactionMode: RedactionMode.REDACTED,
    });

    const entries = history.getToolCallMessages('thread-1', 'user-1');
    expect(entries).toHaveLength(1);
    expect(entries[0].memorySummary).toContain('[REDACTED]');
  });

  it('should clear history for a specific conversation', () => {
    history.addToolCall('thread-1', 'user-1', {
      timestamp: new Date(),
      toolName: 'test',
      parameters: {},
      success: true,
      memorySummary: 'Test',
      redactionMode: RedactionMode.NONE,
    });

    history.clearHistory('thread-1', 'user-1');
    expect(history.getToolCallMessages('thread-1', 'user-1')).toHaveLength(0);
  });

  it('should clear all history', () => {
    history.addToolCall('thread-1', 'user-1', {
      timestamp: new Date(),
      toolName: 'test',
      parameters: {},
      success: true,
      memorySummary: 'Test',
      redactionMode: RedactionMode.NONE,
    });

    history.clearAll();
    expect(history.size).toBe(0);
  });

  it('should create REDACTED memory summary for search operations', () => {
    const { memorySummary, redactionMode } = InMemoryToolCallHistory.createMemorySummary(
      'search_messages',
      { keywords: 'important' },
      'Found some results',
    );

    expect(redactionMode).toBe(RedactionMode.REDACTED);
    expect(memorySummary).toContain('已從跨回合記憶隔離');
  });

  it('should redact Discord URLs in memory summaries', () => {
    const { memorySummary, redactionMode } = InMemoryToolCallHistory.createMemorySummary(
      'send_messages',
      {},
      'Sent message to https://discord.com/channels/123/456',
    );

    expect(redactionMode).toBe(RedactionMode.REDACTED);
    expect(memorySummary).toContain('[Discord URL 已隱藏]');
  });
});

describe('ConversationIdBuilder', () => {
  it('should build thread-level ID', () => {
    const id = ConversationIdBuilder.build('guild-1', 'channel-1', 'thread-1', 'user-1', null);
    expect(id).toBe('guild-1:thread-1:user-1');
  });

  it('should build message-level ID', () => {
    const id = ConversationIdBuilder.build('guild-1', 'channel-1', null, 'user-1', 'msg-1');
    expect(id).toBe('guild-1:channel-1:user-1:msg-1');
  });

  it('should detect thread-level strategy', () => {
    expect(ConversationIdBuilder.parseStrategy('guild-1:thread-1:user-1')).toBe(
      ConversationIdStrategy.THREAD_LEVEL,
    );
  });

  it('should detect message-level strategy', () => {
    expect(ConversationIdBuilder.parseStrategy('guild-1:channel-1:user-1:msg-1')).toBe(
      ConversationIdStrategy.MESSAGE_LEVEL,
    );
  });

  it('should extract components correctly', () => {
    expect(ConversationIdBuilder.extractGuildId('guild-1:thread-1:user-1')).toBe('guild-1');
    expect(ConversationIdBuilder.extractThreadId('guild-1:thread-1:user-1')).toBe('thread-1');
    expect(ConversationIdBuilder.extractUserId('guild-1:thread-1:user-1')).toBe('user-1');
  });

  it('should build tool call key', () => {
    expect(ConversationIdBuilder.buildToolCallKey('thread-1', 'user-1')).toBe('thread-1:user-1');
  });
});
