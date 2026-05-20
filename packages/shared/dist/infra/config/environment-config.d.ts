import { type ConfigValues } from './schema.js';
/**
 * Loads configuration from environment variables with fallback to .env file and Zod defaults.
 *
 * Priority order (highest to lowest):
 * 1. System environment variables (process.env)
 * 2. .env file in project root
 * 3. Zod schema defaults
 */
export declare class EnvironmentConfig {
    private readonly dotEnvDirectory?;
    private readonly envSource;
    private config;
    constructor(dotEnvDirectory?: string | undefined, envSource?: Record<string, string | undefined>);
    /**
     * Parses and validates configuration from all sources.
     * @returns the validated config values
     * @throws Error if required fields are missing
     */
    parse(): ConfigValues;
    /** Returns the validated config (calls parse() if not yet parsed). */
    private get;
    getDiscordBotToken(): string;
    getDatabaseUrl(): string;
    getDatabaseUsername(): string;
    getDatabasePassword(): string;
    getPoolMaxSize(): number;
    getPoolMinIdle(): number;
    getPoolConnectionTimeout(): number;
    getPoolIdleTimeout(): number;
    getPoolMaxLifetime(): number;
    getRedisUri(): string;
    getAIServiceBaseUrl(): string;
    getAIServiceApiKey(): string;
    getAIServiceModel(): string;
    getAIServiceTemperature(): number;
    getAIServiceTimeoutSeconds(): number;
    getPromptsDirPath(): string;
    getPromptMaxSizeBytes(): number;
    getAIShowReasoning(): boolean;
    getAIMarkdownValidationEnabled(): boolean;
    getAIMarkdownValidationStreamingBypass(): boolean;
    getAppPublicBaseUrl(): string;
    getEcpayMerchantId(): string;
    getEcpayHashKey(): string;
    getEcpayHashIv(): string;
    getEcpayReturnUrl(): string;
    getEcpayStageMode(): boolean;
    getEcpayCvsExpireMinutes(): number;
    getEcpayCallbackBindHost(): string;
    getEcpayCallbackBindPort(): number;
    getEcpayCallbackPath(): string;
    /** Returns the raw config object for advanced usage. */
    getConfig(): ConfigValues;
}
