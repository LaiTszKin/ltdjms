import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  SimplifiedChatMemoryProvider,
  DiscordThreadHistoryProvider,
} from '../memory/chat-memory-provider.js';
import { InMemoryToolCallHistory } from '../memory/tool-call-history.js';
import { RedactionMode } from '../ai-chat-service.js';
import { TokenEstimator } from '../memory/TokenEstimator.js';
import type { DiscordRuntimeGateway } from '@ltdjms/shared';

/** UT-AG-019 — SimplifiedChatMemoryProviderTest.java */
describe('UT-AG-019 chat memory provider parity', () => {
  let threadHistoryProvider: DiscordThreadHistoryProvider;
  let toolCallHistory: InMemoryToolCallHistory;
  let runtimeGateway: DiscordRuntimeGateway;
  let provider: SimplifiedChatMemoryProvider;

  beforeEach(() => {
    threadHistoryProvider = {
      getThreadHistory: vi.fn().mockResolvedValue([{ role: 'user', content: 'hello' }]),
    } as unknown as DiscordThreadHistoryProvider;
    toolCallHistory = new InMemoryToolCallHistory();
    runtimeGateway = {
      selfUserId: vi.fn().mockReturnValue('900'),
      requireReadyClient: vi.fn(),
    } as unknown as DiscordRuntimeGateway;
    provider = new SimplifiedChatMemoryProvider(
      threadHistoryProvider,
      toolCallHistory,
      runtimeGateway,
      new TokenEstimator(),
    );
  });

  it('falls back when thread conversation id is malformed', async () => {
    const memory = await provider.getMemory('guild:thread:user');
    expect(memory).toEqual([]);
    expect(threadHistoryProvider.getThreadHistory).not.toHaveBeenCalled();
  });

  it('loads thread history for valid numeric conversation id', async () => {
    const memory = await provider.getMemory('100:200:300');
    expect(memory.length).toBeGreaterThan(0);
    expect(threadHistoryProvider.getThreadHistory).toHaveBeenCalledWith('100', '200', '300', '900');
  });

  it('injects memory-safe tool summaries only', async () => {
    toolCallHistory.addToolCall('200', '300', {
      toolName: 'search_messages',
      parameters: { keywords: 'secret' },
      memorySummary:
        '工具「search_messages」已執行，結果因敏感內容已從跨回合記憶隔離。',
      redactionMode: RedactionMode.REDACTED,
      timestamp: new Date(),
      success: true,
    });

    const memory = await provider.getMemory('100:200:300');
    const toolSummary = memory.find((m) => m.content.includes('跨回合記憶隔離'));
    expect(toolSummary).toBeDefined();
    expect(toolSummary?.role).toBe('assistant');
    expect(toolSummary?.content).not.toContain('discord.com/channels/');
  });

  it('falls back when runtime gateway is not ready', async () => {
    vi.mocked(runtimeGateway.selfUserId).mockImplementation(() => {
      throw new Error('JDA 尚未初始化');
    });
    const memory = await provider.getMemory('100:200:300');
    expect(memory).toEqual([]);
  });
});
