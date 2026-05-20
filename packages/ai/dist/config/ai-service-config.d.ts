import { z } from 'zod';
import { DomainError, type Result } from '@ltdjms/shared';
import type { EnvironmentConfig } from '@ltdjms/shared';
/**
 * Zod schema for AI service configuration values.
 * Matches Java AIServiceConfig record fields.
 */
export declare const AIServiceConfigSchema: z.ZodObject<{
    baseUrl: z.ZodString;
    apiKey: z.ZodString;
    model: z.ZodString;
    temperature: z.ZodNumber;
    timeoutSeconds: z.ZodNumber;
    showReasoning: z.ZodDefault<z.ZodBoolean>;
    enableMarkdownValidation: z.ZodDefault<z.ZodBoolean>;
    streamingBypassValidation: z.ZodDefault<z.ZodBoolean>;
}, "strip", z.ZodTypeAny, {
    baseUrl: string;
    apiKey: string;
    model: string;
    temperature: number;
    timeoutSeconds: number;
    showReasoning: boolean;
    enableMarkdownValidation: boolean;
    streamingBypassValidation: boolean;
}, {
    baseUrl: string;
    apiKey: string;
    model: string;
    temperature: number;
    timeoutSeconds: number;
    showReasoning?: boolean | undefined;
    enableMarkdownValidation?: boolean | undefined;
    streamingBypassValidation?: boolean | undefined;
}>;
export type AIServiceConfigValues = z.infer<typeof AIServiceConfigSchema>;
/**
 * AI service configuration — immutable value object.
 * Constructed from EnvironmentConfig or from a raw values object.
 * Matches Java AIServiceConfig record.
 */
export declare class AIServiceConfig {
    readonly baseUrl: string;
    readonly apiKey: string;
    readonly model: string;
    readonly temperature: number;
    readonly timeoutSeconds: number;
    readonly showReasoning: boolean;
    readonly enableMarkdownValidation: boolean;
    readonly streamingBypassValidation: boolean;
    readonly enableThinking: boolean;
    private constructor();
    /**
     * Creates an AIServiceConfig from the shared EnvironmentConfig.
     */
    static from(env: EnvironmentConfig): AIServiceConfig;
    /**
     * Creates an AIServiceConfig from a raw values object (for testing).
     */
    static fromValues(values: Partial<AIServiceConfigValues> & {
        baseUrl: string;
        apiKey: string;
        model: string;
    }): AIServiceConfig;
    /**
     * Validates range constraints.
     * Returns okVoid or err(DomainError).
     */
    validate(): Result<void, DomainError>;
    /** DeepSeek model detection. */
    isDeepSeekModel(): boolean;
}
