import { describe, it, expect } from 'vitest';
import { InMemoryToolCallHistory } from '../memory/tool-call-history.js';
import { RedactionMode } from '../ai-chat-service.js';

/** UT-AG-020 — InMemoryToolCallHistoryTest.java */
describe('UT-AG-020 tool call history parity', () => {
  it('stores memory-safe summary as retrievable entries', () => {
    const history = new InMemoryToolCallHistory();
    history.addToolCall('42', '1001', {
      toolName: 'list_channels',
      parameters: { limit: 3 },
      memorySummary: '工具「list_channels」已成功執行；完整結果不會保留於跨回合記憶。',
      redactionMode: RedactionMode.NONE,
      timestamp: new Date('2026-01-01T00:00:00Z'),
      success: true,
    });

    const messages = history.getToolCallMessages('42', '1001');
    expect(messages).toHaveLength(1);
    expect(messages[0].memorySummary).toContain('完整結果不會保留於跨回合記憶');
  });

  it('redacts search_messages summaries', () => {
    const history = new InMemoryToolCallHistory();
    const summary = InMemoryToolCallHistory.createMemorySummary(
      'search_messages',
      { keywords: 'secret' },
      'raw results with https://discord.com/channels/1/2/3',
    );

    expect(summary.redactionMode).toBe(RedactionMode.REDACTED);
    expect(summary.memorySummary).toContain('已從跨回合記憶隔離');
    expect(summary.memorySummary).not.toContain('discord.com/channels/');
  });

  it('isolates history by user within same thread', () => {
    const history = new InMemoryToolCallHistory();
    history.addToolCall('42', '1001', {
      toolName: 'toolA',
      parameters: {},
      memorySummary: 'A',
      redactionMode: RedactionMode.NONE,
      timestamp: new Date(),
      success: true,
    });
    history.addToolCall('42', '1002', {
      toolName: 'toolB',
      parameters: {},
      memorySummary: 'B',
      redactionMode: RedactionMode.NONE,
      timestamp: new Date(),
      success: true,
    });

    expect(history.getToolCallMessages('42', '1001')).toHaveLength(1);
    expect(history.getToolCallMessages('42', '1002')).toHaveLength(1);
  });

  it('redacts discord URLs in normal tool results', () => {
    const summary = InMemoryToolCallHistory.createMemorySummary(
      'list_channels',
      {},
      'see https://discord.com/channels/1/2/3',
    );
    expect(summary.memorySummary).toContain('[Discord URL 已隱藏]');
    expect(summary.redactionMode).toBe(RedactionMode.REDACTED);
  });
});
