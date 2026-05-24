export enum ToolExecutionStatus {
  SUCCESS = 'SUCCESS',
  FAILED = 'FAILED',
}

export interface ToolExecutionLog {
  id?: number;
  guildId: string;
  channelId: string;
  triggerUserId: string;
  toolName: string;
  parameters: string;
  executionResult: string | null;
  errorMessage: string | null;
  status: ToolExecutionStatus;
  executedAt: Date;
}

export function createSuccessToolExecutionLog(
  guildId: string,
  channelId: string,
  triggerUserId: string,
  toolName: string,
  parameters: string,
  resultSummary: string,
): ToolExecutionLog {
  return {
    guildId,
    channelId,
    triggerUserId,
    toolName,
    parameters,
    executionResult: resultSummary,
    errorMessage: null,
    status: ToolExecutionStatus.SUCCESS,
    executedAt: new Date(),
  };
}

export function createFailureToolExecutionLog(
  guildId: string,
  channelId: string,
  triggerUserId: string,
  toolName: string,
  parameters: string,
  errorSummary: string,
): ToolExecutionLog {
  return {
    guildId,
    channelId,
    triggerUserId,
    toolName,
    parameters,
    executionResult: null,
    errorMessage: errorSummary,
    status: ToolExecutionStatus.FAILED,
    executedAt: new Date(),
  };
}
