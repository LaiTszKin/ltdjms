import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ToolExecutionListener } from '../../listeners/tool-execution-listener.js';
import type { DiscordRuntimeGateway } from '@ltdjms/shared';

/** UT-AG-022 — ToolExecutionListenerTest.java */
describe('UT-AG-022 tool execution listener parity', () => {
  let runtimeGateway: DiscordRuntimeGateway;
  let send: ReturnType<typeof vi.fn>;
  let listener: ToolExecutionListener;

  beforeEach(() => {
    send = vi.fn().mockResolvedValue(undefined);
    const channel = {
      isTextBased: () => true,
      send,
    };
    runtimeGateway = {
      findGuildChannel: vi.fn(() => null),
      findThreadChannel: vi.fn(() => channel),
    } as unknown as DiscordRuntimeGateway;
    listener = new ToolExecutionListener(runtimeGateway);
  });

  it('handles tool execution started event', () => {
    listener.accept({
      eventType: 'langchain4j_tool_execution_started',
      guildId: '123',
      channelId: '456',
      userId: '789',
      toolName: 'WeatherTool',
      timestamp: new Date(),
    });
    expect(send).toHaveBeenCalledWith(
      '🤖 我先執行這一步：正在呼叫工具「WeatherTool」...',
    );
  });

  it('handles successful tool executed event', () => {
    listener.accept({
      eventType: 'langchain4j_tool_executed',
      guildId: '123',
      channelId: '456',
      userId: '789',
      toolName: 'WeatherTool',
      result: 'ok',
      success: true,
      timestamp: new Date(),
    });
    expect(send).toHaveBeenCalledWith('✅ 工具「WeatherTool」執行成功');
  });

  it('handles failed tool executed event', () => {
    listener.accept({
      eventType: 'langchain4j_tool_executed',
      guildId: '123',
      channelId: '456',
      userId: '789',
      toolName: 'WeatherTool',
      result: 'API timeout',
      success: false,
      timestamp: new Date(),
    });
    expect(send).toHaveBeenCalledWith('❌ 工具「WeatherTool」執行失敗：API timeout');
  });

  it('ignores null event gracefully', () => {
    listener.accept(null);
    expect(send).not.toHaveBeenCalled();
  });

  it('does not send when guild channel cannot be resolved', () => {
    vi.mocked(runtimeGateway.findThreadChannel).mockReturnValue(null);
    listener.accept({
      eventType: 'langchain4j_tool_execution_started',
      guildId: '123',
      channelId: '456',
      userId: '789',
      toolName: 'TestTool',
      timestamp: new Date(),
    });
    expect(send).not.toHaveBeenCalled();
  });
});
