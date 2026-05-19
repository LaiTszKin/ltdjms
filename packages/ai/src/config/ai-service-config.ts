import { z } from 'zod';
import { DomainError, ok, okVoid, err, type Result } from '@ltdjms/shared';
import type { EnvironmentConfig } from '@ltdjms/shared';

/**
 * Zod schema for AI service configuration values.
 * Matches Java AIServiceConfig record fields.
 */
export const AIServiceConfigSchema = z.object({
  baseUrl: z.string().min(1, 'AI service base URL is required'),
  apiKey: z.string().min(1, 'AI service API key is required'),
  model: z.string().min(1, 'AI model name is required'),
  temperature: z.number().min(0).max(2),
  timeoutSeconds: z.number().int().min(1).max(120),
  showReasoning: z.boolean().default(false),
  enableMarkdownValidation: z.boolean().default(true),
  streamingBypassValidation: z.boolean().default(false),
});

export type AIServiceConfigValues = z.infer<typeof AIServiceConfigSchema>;

/**
 * AI service configuration — immutable value object.
 * Constructed from EnvironmentConfig or from a raw values object.
 * Matches Java AIServiceConfig record.
 */
export class AIServiceConfig {
  readonly baseUrl: string;
  readonly apiKey: string;
  readonly model: string;
  readonly temperature: number;
  readonly timeoutSeconds: number;
  readonly showReasoning: boolean;
  readonly enableMarkdownValidation: boolean;
  readonly streamingBypassValidation: boolean;
  readonly enableThinking: boolean;

  private constructor(values: AIServiceConfigValues) {
    this.baseUrl = values.baseUrl;
    this.apiKey = values.apiKey;
    this.model = values.model;
    this.temperature = values.temperature;
    this.timeoutSeconds = values.timeoutSeconds;
    this.showReasoning = values.showReasoning;
    this.enableMarkdownValidation = values.enableMarkdownValidation;
    this.streamingBypassValidation = values.streamingBypassValidation;
    // DeepSeek models automatically enable thinking
    this.enableThinking = values.showReasoning || /deepseek/i.test(values.model);
  }

  /**
   * Creates an AIServiceConfig from the shared EnvironmentConfig.
   */
  static from(env: EnvironmentConfig): AIServiceConfig {
    return new AIServiceConfig({
      baseUrl: env.getAIServiceBaseUrl(),
      apiKey: env.getAIServiceApiKey(),
      model: env.getAIServiceModel(),
      temperature: env.getAIServiceTemperature(),
      timeoutSeconds: env.getAIServiceTimeoutSeconds(),
      showReasoning: env.getAIShowReasoning(),
      enableMarkdownValidation: env.getAIMarkdownValidationEnabled(),
      streamingBypassValidation: env.getAIMarkdownValidationStreamingBypass(),
    });
  }

  /**
   * Creates an AIServiceConfig from a raw values object (for testing).
   */
  static fromValues(values: Partial<AIServiceConfigValues> & {
    baseUrl: string;
    apiKey: string;
    model: string;
  }): AIServiceConfig {
    const parsed = AIServiceConfigSchema.parse({
      temperature: 0.7,
      timeoutSeconds: 30,
      showReasoning: false,
      enableMarkdownValidation: true,
      streamingBypassValidation: false,
      ...values,
    });
    return new AIServiceConfig(parsed);
  }

  /**
   * Validates range constraints.
   * Returns okVoid or err(DomainError).
   */
  validate(): Result<void, DomainError> {
    const result = AIServiceConfigSchema.safeParse({
      baseUrl: this.baseUrl,
      apiKey: this.apiKey,
      model: this.model,
      temperature: this.temperature,
      timeoutSeconds: this.timeoutSeconds,
      showReasoning: this.showReasoning,
      enableMarkdownValidation: this.enableMarkdownValidation,
      streamingBypassValidation: this.streamingBypassValidation,
    });

    if (!result.success) {
      const firstIssue = result.error.issues[0];
      return err(DomainError.invalidInput(
        `AI config validation failed: ${firstIssue.path.join('.')} — ${firstIssue.message}`,
      ));
    }

    return okVoid<DomainError>() as unknown as Result<void, DomainError>;
  }

  /** DeepSeek model detection. */
  isDeepSeekModel(): boolean {
    return /deepseek/i.test(this.model);
  }
}
