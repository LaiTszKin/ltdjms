import { AsyncLocalStorage } from 'node:async_hooks';
import { createHash } from 'node:crypto';
import pino from 'pino';
import type {
  DomainEventPublisher,
  LangChain4jToolExecutedEvent,
  LangChain4jToolExecutionStartedEvent,
} from '@ltdjms/shared';
import { ToolExecutionContext } from '../tools/ToolExecutionContext.js';
import {
  createFailureToolExecutionLog,
  createSuccessToolExecutionLog,
} from '../domain/tool-execution-log.js';
import type { ToolExecutionLogRepository } from '../persistence/drizzle-tool-execution-log-repository.js';

interface ExecutionContext {
  guildId: string;
  channelId: string;
  userId: string;
  toolName: string;
  parameters: Record<string, unknown> | null;
  startTime: number;
  managedByRun?: boolean;
}

const EMPTY_PARAMETERS_SUMMARY = '{"redacted":true,"entryCount":0,"keys":[]}';
const TEXT_FALLBACK_SUMMARY = '{"redacted":true,"type":"text"}';

/**
 * Intercepts tool execution for audit logging and domain events.
 * Matches Java ToolExecutionInterceptor (redacted params/results, DB + events).
 */
export class ToolExecutionInterceptor {
  private static readonly executionStorage = new AsyncLocalStorage<ExecutionContext>();

  private readonly logger: pino.Logger;
  private readonly forceJsonFailure: boolean;

  constructor(
    private readonly logRepository?: ToolExecutionLogRepository,
    private readonly eventPublisher?: DomainEventPublisher,
    logger?: pino.Logger,
    options?: { forceJsonFailure?: boolean },
  ) {
    this.logger = logger ?? pino({ name: 'tool-execution-interceptor' });
    this.forceJsonFailure = options?.forceJsonFailure ?? false;
  }

  /**
   * Runs a tool execution fn with isolated interceptor context (concurrency-safe).
   */
  async runTracked<T>(
    toolName: string,
    parameters: Record<string, unknown>,
    fn: () => Promise<T>,
  ): Promise<T> {
    const ctx = ToolExecutionContext.getContext();
    if (!ctx) {
      return fn();
    }

    const executionCtx: ExecutionContext = {
      guildId: ctx.guildId,
      channelId: ctx.channelId,
      userId: ctx.userId,
      toolName,
      parameters: parameters ?? null,
      startTime: Date.now(),
      managedByRun: true,
    };

    return ToolExecutionInterceptor.executionStorage.run(executionCtx, async () => {
      this.onToolExecutionStarted(toolName, parameters);
      try {
        const result = await fn();
        if (typeof result === 'string') {
          this.onToolExecutionCompleted(result);
        }
        return result;
      } catch (error) {
        this.onToolExecutionFailed(error);
        throw error;
      }
    });
  }

  onToolExecutionStarted(toolName: string, _parameters: Record<string, unknown>): void {
    try {
      const ctx = ToolExecutionContext.getContext();
      if (!ctx) {
        this.logger.warn('無法獲取工具執行上下文，跳過審計記錄');
        return;
      }

      if (!ToolExecutionInterceptor.executionStorage.getStore()) {
        this.logger.debug('無 runTracked 上下文，跳過工具開始審計');
        return;
      }

      if (this.eventPublisher) {
        const event: LangChain4jToolExecutionStartedEvent = {
          eventType: 'langchain4j_tool_execution_started',
          guildId: ctx.guildId,
          channelId: ctx.channelId,
          userId: ctx.userId,
          toolName,
          timestamp: new Date(),
        };
        this.eventPublisher.publish(event);
      }
    } catch (error) {
      this.logger.warn({ err: error }, 'Failed to record tool execution start');
    }
  }

  onToolExecutionCompleted(result: string): string {
    const ctx = ToolExecutionInterceptor.executionStorage.getStore();
    if (!ctx) {
      this.logger.debug('無工具執行上下文，跳過成功記錄');
      return result;
    }

    try {
      const parametersJson = this.summarizeParameters(ctx.parameters);
      const resultSummary = this.summarizeTextPayload(result);
      const log = createSuccessToolExecutionLog(
        ctx.guildId,
        ctx.channelId,
        ctx.userId,
        ctx.toolName,
        parametersJson,
        resultSummary,
      );

      void this.persistLog(log);

      if (this.eventPublisher) {
        const event: LangChain4jToolExecutedEvent = {
          eventType: 'langchain4j_tool_executed',
          guildId: ctx.guildId,
          channelId: ctx.channelId,
          userId: ctx.userId,
          toolName: ctx.toolName,
          result,
          success: true,
          timestamp: new Date(),
        };
        this.eventPublisher.publish(event);
      }

      return `✅ 工具「${this.getToolDisplayName(ctx.toolName)}」執行成功`;
    } catch (error) {
      this.logger.error({ err: error, toolName: ctx.toolName }, '記錄工具執行成功日誌失敗');
      return result;
    } finally {
      this.clearExecutionContext();
    }
  }

  onToolExecutionFailed(error: unknown): string {
    const ctx = ToolExecutionInterceptor.executionStorage.getStore();
    const message = error instanceof Error ? error.message : String(error);

    if (!ctx) {
      this.logger.debug('無工具執行上下文，跳過失敗記錄');
      return `❌ 工具執行失敗：${message}`;
    }

    try {
      const parametersJson = this.summarizeParameters(ctx.parameters);
      const errorSummary = this.summarizeTextPayload(message);
      const log = createFailureToolExecutionLog(
        ctx.guildId,
        ctx.channelId,
        ctx.userId,
        ctx.toolName,
        parametersJson,
        errorSummary,
      );

      void this.persistLog(log);

      if (this.eventPublisher) {
        const event: LangChain4jToolExecutedEvent = {
          eventType: 'langchain4j_tool_executed',
          guildId: ctx.guildId,
          channelId: ctx.channelId,
          userId: ctx.userId,
          toolName: ctx.toolName,
          result: message,
          success: false,
          timestamp: new Date(),
        };
        this.eventPublisher.publish(event);
      }

      return `❌ 工具「${this.getToolDisplayName(ctx.toolName)}」執行失敗：${message}`;
    } catch (persistError) {
      this.logger.error({ err: persistError, toolName: ctx.toolName }, '記錄工具執行失敗日誌失敗');
      return `❌ 工具執行失敗：${message}`;
    } finally {
      this.clearExecutionContext();
    }
  }

  private clearExecutionContext(): void {
    // Context lifetime is scoped to runTracked(); no AsyncLocalStorage mutation here.
  }

  private async persistLog(log: ReturnType<typeof createSuccessToolExecutionLog>): Promise<void> {
    if (!this.logRepository) {
      return;
    }
    const result = await this.logRepository.save(log);
    if (result.isErr()) {
      this.logger.error({ err: result.getError() }, 'Failed to persist tool execution log');
    }
  }

  private getToolDisplayName(toolName: string): string {
    const displayNames: Record<string, string> = {
      create_channel: '創建頻道',
      createChannel: '創建頻道',
      create_category: '創建類別',
      createCategory: '創建類別',
      create_role: '創建角色',
      createRole: '創建角色',
      list_channels: '列出頻道',
      listChannels: '列出頻道',
      list_categories: '列出類別',
      listCategories: '列出類別',
      list_roles: '列出角色',
      listRoles: '列出角色',
      get_channel_permissions: '獲取頻道權限',
      getChannelPermissions: '獲取頻道權限',
      get_role_permissions: '獲取角色權限',
      getRolePermissions: '獲取角色權限',
      modify_channel_permissions: '修改頻道設定',
      modifyChannelPermissions: '修改頻道設定',
      modify_category_permissions: '修改類別設定',
      modifyCategoryPermissions: '修改類別設定',
      modify_role_permissions: '修改角色設定',
      modifyRolePermissions: '修改角色設定',
    };
    return displayNames[toolName] ?? toolName;
  }

  summarizeParameters(parameters: Record<string, unknown> | null): string {
    if (!parameters || Object.keys(parameters).length === 0) {
      return EMPTY_PARAMETERS_SUMMARY;
    }

    const keys = Object.keys(parameters).sort();
    const valueSummaries: Record<string, unknown> = {};
    for (const key of keys) {
      valueSummaries[key] = this.summarizeValue(parameters[key]);
    }

    return this.toJson(
      {
        redacted: true,
        entryCount: keys.length,
        keys,
        values: valueSummaries,
      },
      EMPTY_PARAMETERS_SUMMARY,
    );
  }

  summarizeTextPayload(payload: string | null | undefined): string {
    const text = payload ?? '';
    return this.toJson(
      {
        redacted: true,
        type: 'text',
        length: text.length,
        blank: text.trim().length === 0,
        sha256: this.sha256Hex(text),
      },
      TEXT_FALLBACK_SUMMARY,
    );
  }

  private summarizeValue(value: unknown): Record<string, unknown> {
    const summary: Record<string, unknown> = { redacted: true };

    if (value === null || value === undefined) {
      summary.type = 'null';
      return summary;
    }

    if (Array.isArray(value)) {
      summary.type = 'Array';
      summary.size = value.length;
    } else if (value instanceof Map) {
      summary.type = 'Map';
      summary.size = value.size;
    } else if (typeof value === 'object') {
      summary.type = 'Object';
      summary.size = Object.keys(value as object).length;
    } else {
      summary.type = typeof value;
      if (typeof value === 'string') {
        summary.length = value.length;
        summary.blank = value.trim().length === 0;
      }
    }

    summary.sha256 = this.sha256Hex(this.safeValueFingerprint(value));
    return summary;
  }

  private safeValueFingerprint(value: unknown): string {
    try {
      return JSON.stringify(value);
    } catch {
      return String(value);
    }
  }

  private toJson(value: unknown, fallback: string): string {
    if (this.forceJsonFailure) {
      return fallback;
    }
    try {
      return JSON.stringify(value);
    } catch {
      return fallback;
    }
  }

  private sha256Hex(value: string): string {
    return createHash('sha256').update(value, 'utf8').digest('hex');
  }
}
