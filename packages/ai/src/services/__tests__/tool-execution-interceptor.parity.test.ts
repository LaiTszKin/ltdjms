import { describe, it, expect, vi, beforeEach } from 'vitest';
import auditOracle from '../../../../../docs/plans/2026-05-24/java-parity-shop-ai/ai-agent-java-parity/fixtures/java-tool-audit-oracle.json';
import { ToolExecutionInterceptor } from '../ToolExecutionInterceptor.js';
import { ToolExecutionContext } from '../../tools/ToolExecutionContext.js';
import {
  InMemoryToolExecutionLogRepository,
  type ToolExecutionLogRepository,
} from '../../persistence/drizzle-tool-execution-log-repository.js';
import type { DomainEventPublisher } from '@ltdjms/shared';

/** UT-AG-018 — ToolExecutionInterceptorTest.java */
describe('UT-AG-018 tool execution interceptor parity', () => {
  const TEST_GUILD_ID = '123456789';
  const TEST_CHANNEL_ID = '987654321';
  const TEST_USER_ID = '111222333';

  let repository: ToolExecutionLogRepository;
  let eventPublisher: DomainEventPublisher;
  let interceptor: ToolExecutionInterceptor;

  beforeEach(() => {
    repository = new InMemoryToolExecutionLogRepository();
    eventPublisher = {
      publish: vi.fn(),
      register: vi.fn(),
      unregister: vi.fn(),
      getLastPublishedEvent: vi.fn(),
    };
    interceptor = new ToolExecutionInterceptor(repository, eventPublisher);
  });

  function withContext<T>(fn: () => T): T {
    return ToolExecutionContext.run(
      { guildId: TEST_GUILD_ID, channelId: TEST_CHANNEL_ID, userId: TEST_USER_ID },
      fn,
    );
  }

  it('publishes started event when context is set', () => {
    withContext(() => {
      interceptor.onToolExecutionStarted('list_channels', {});
    });
    expect(eventPublisher.publish).toHaveBeenCalledWith(
      expect.objectContaining({
        eventType: 'langchain4j_tool_execution_started',
        toolName: 'list_channels',
      }),
    );
  });

  it('does not publish started event without context', () => {
    interceptor.onToolExecutionStarted('create_channel', { name: 'test' });
    expect(eventPublisher.publish).not.toHaveBeenCalled();
  });

  it('records successful execution with redacted parameters', async () => {
    const result = await withContext(async () =>
      interceptor.runTracked(
        'create_channel',
        { name: 'test-channel', type: 'text' },
        async () => 'Channel created successfully',
      ),
    );

    expect(result).toBe('Channel created successfully');

    const logs = await repository.findByChannelId(TEST_CHANNEL_ID, 10);
    expect(logs.isOk()).toBe(true);
    const saved = logs.getValue()[0];
    expect(saved.parameters).toContain('"redacted":true');
    expect(saved.parameters).toContain('"sha256"');
    expect(saved.parameters).not.toContain('test-channel');
    expect(saved.executionResult).toContain('"redacted":true');
    expect(saved.executionResult).not.toContain('Channel created successfully');
  });

  it('returns original result when no context on complete', () => {
    const result = interceptor.onToolExecutionCompleted('Test result');
    expect(result).toBe('Test result');
  });

  it('stores empty parameter summary for null parameters', async () => {
    withContext(() => {
      interceptor.onToolExecutionStarted('testTool', null as unknown as Record<string, unknown>);
      interceptor.onToolExecutionCompleted('Success');
    });

    const logs = await repository.findByChannelId(TEST_CHANNEL_ID, 1);
    expect(logs.getValue()[0].parameters).toBe(auditOracle.emptyParametersSummary);
  });

  it('uses safe fallback when serialization fails', async () => {
    const failingInterceptor = new ToolExecutionInterceptor(repository, eventPublisher, undefined, {
      forceJsonFailure: true,
    });

    withContext(() => {
      failingInterceptor.onToolExecutionStarted('testTool', { key: 'value' });
      const result = failingInterceptor.onToolExecutionCompleted('Success');
      expect(result).toContain('✅');
    });

    const logs = await repository.findByChannelId(TEST_CHANNEL_ID, 1);
    expect(logs.getValue()[0].parameters).toBe(auditOracle.serializationFailureFallback.parameters);
    expect(logs.getValue()[0].executionResult).toBe(
      auditOracle.serializationFailureFallback.executionResult,
    );
  });

  it('records failed execution with redacted error', async () => {
    await withContext(async () => {
      try {
        await interceptor.runTracked('create_channel', { name: 'secret' }, async () => {
          throw new Error('Permission denied');
        });
      } catch {
        // expected
      }
    });

    const logs = await repository.findByChannelId(TEST_CHANNEL_ID, 1);
    const saved = logs.getValue()[0];
    expect(saved.parameters).not.toContain('secret');
    expect(saved.errorMessage).toContain('"redacted":true');
    expect(saved.errorMessage).not.toContain('Permission denied');
  });

  it('cleans context after success', () => {
    withContext(() => {
      interceptor.onToolExecutionStarted('testTool', {});
      interceptor.onToolExecutionCompleted('Success');
    });
    const second = interceptor.onToolExecutionCompleted('Another');
    expect(second).toBe('Another');
  });

  it('isolates concurrent tool executions with correct audit logs', async () => {
    const contexts = [
      { guildId: '111', channelId: '222', userId: '333', toolName: 'list_channels' },
      { guildId: '444', channelId: '555', userId: '666', toolName: 'create_channel' },
      { guildId: '777', channelId: '888', userId: '999', toolName: 'list_roles' },
    ] as const;

    await Promise.all(
      contexts.map(({ guildId, channelId, userId, toolName }) =>
        ToolExecutionContext.run({ guildId, channelId, userId }, async () =>
          interceptor.runTracked(toolName, { key: toolName }, async () => {
            await new Promise((resolve) => setTimeout(resolve, 10));
            return `${toolName}-result`;
          }),
        ),
      ),
    );

    for (const { guildId, channelId, userId, toolName } of contexts) {
      const logs = await repository.findByChannelId(channelId, 10);
      expect(logs.isOk()).toBe(true);
      const matching = logs
        .getValue()
        .filter(
          (log) =>
            log.guildId === guildId && log.triggerUserId === userId && log.toolName === toolName,
        );
      expect(matching).toHaveLength(1);
      expect(matching[0].executionResult).toContain('"redacted":true');
    }
  });
});
