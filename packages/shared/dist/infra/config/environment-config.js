import { join } from 'node:path';
import { loadDotEnv } from './env-loader.js';
import { ConfigSchema } from './schema.js';
/**
 * Loads configuration from environment variables with fallback to .env file and Zod defaults.
 *
 * Priority order (highest to lowest):
 * 1. System environment variables (process.env)
 * 2. .env file in project root
 * 3. Zod schema defaults
 */
export class EnvironmentConfig {
    dotEnvDirectory;
    envSource;
    config = null;
    constructor(dotEnvDirectory, envSource = process.env) {
        this.dotEnvDirectory = dotEnvDirectory;
        this.envSource = envSource;
    }
    /**
     * Parses and validates configuration from all sources.
     * @returns the validated config values
     * @throws Error if required fields are missing
     */
    parse() {
        if (this.config) {
            return this.config;
        }
        // Load .env file values
        const dotEnvPath = this.dotEnvDirectory
            ? join(this.dotEnvDirectory, '.env')
            : join(process.cwd(), '.env');
        const dotEnvValues = loadDotEnv(dotEnvPath);
        // Merge: process.env (highest) > .env file > Zod defaults
        const merged = { ...dotEnvValues };
        // process.env values override .env values
        for (const key of Object.keys(ConfigSchema.shape)) {
            const envVal = this.envSource[key];
            if (envVal !== undefined && envVal !== '') {
                merged[key] = envVal;
            }
            else if (dotEnvValues[key] !== undefined) {
                merged[key] = dotEnvValues[key];
            }
        }
        const result = ConfigSchema.safeParse(merged);
        if (!result.success) {
            const missingFields = result.error.issues
                .map((i) => `${i.path.join('.')}: ${i.message}`)
                .join('; ');
            throw new Error(`Configuration validation failed: ${missingFields}`);
        }
        this.config = result.data;
        return this.config;
    }
    /** Returns the validated config (calls parse() if not yet parsed). */
    get() {
        if (!this.config) {
            return this.parse();
        }
        return this.config;
    }
    // ---- Discord ----
    getDiscordBotToken() {
        return this.get().DISCORD_BOT_TOKEN;
    }
    // ---- Database ----
    getDatabaseUrl() {
        const cfg = this.get();
        if (cfg.DB_URL) {
            return cfg.DB_URL;
        }
        const user = cfg.DB_USERNAME ?? cfg.DATABASE_USER;
        const pass = cfg.DB_PASSWORD ?? cfg.DATABASE_PASSWORD;
        return `postgresql://${user}:${pass}@${cfg.DATABASE_HOST}:${cfg.DATABASE_PORT}/${cfg.DATABASE_NAME}`;
    }
    getDatabaseUsername() {
        const cfg = this.get();
        return cfg.DB_USERNAME ?? cfg.DATABASE_USER;
    }
    getDatabasePassword() {
        const cfg = this.get();
        return cfg.DB_PASSWORD ?? cfg.DATABASE_PASSWORD;
    }
    getPoolMaxSize() {
        return this.get().DB_POOL_MAX_SIZE;
    }
    getPoolMinIdle() {
        return this.get().DB_POOL_MIN_IDLE;
    }
    getPoolConnectionTimeout() {
        return this.get().DB_POOL_CONNECTION_TIMEOUT;
    }
    getPoolIdleTimeout() {
        return this.get().DB_POOL_IDLE_TIMEOUT;
    }
    getPoolMaxLifetime() {
        return this.get().DB_POOL_MAX_LIFETIME;
    }
    // ---- Redis ----
    getRedisUri() {
        return this.get().REDIS_URI;
    }
    // ---- AI Service ----
    getAIServiceBaseUrl() {
        return this.get().AI_SERVICE_BASE_URL;
    }
    getAIServiceApiKey() {
        return this.get().AI_SERVICE_API_KEY;
    }
    getAIServiceModel() {
        return this.get().AI_SERVICE_MODEL;
    }
    getAIServiceTemperature() {
        return this.get().AI_SERVICE_TEMPERATURE;
    }
    getAIServiceTimeoutSeconds() {
        return this.get().AI_SERVICE_TIMEOUT_SECONDS;
    }
    // ---- Prompts ----
    getPromptsDirPath() {
        return this.get().PROMPTS_DIR_PATH;
    }
    getPromptMaxSizeBytes() {
        return this.get().PROMPT_MAX_SIZE_BYTES;
    }
    // ---- AI Features ----
    getAIShowReasoning() {
        return this.get().AI_SHOW_REASONING;
    }
    getAIMarkdownValidationEnabled() {
        return this.get().AI_MARKDOWN_VALIDATION_ENABLED;
    }
    getAIMarkdownValidationStreamingBypass() {
        return this.get().AI_MARKDOWN_VALIDATION_STREAMING_BYPASS;
    }
    // ---- App ----
    getAppPublicBaseUrl() {
        return normalizePublicBaseUrl(this.get().APP_PUBLIC_BASE_URL);
    }
    // ---- ECPay ----
    getEcpayMerchantId() {
        return this.get().ECPAY_MERCHANT_ID;
    }
    getEcpayHashKey() {
        return this.get().ECPAY_HASH_KEY;
    }
    getEcpayHashIv() {
        return this.get().ECPAY_HASH_IV;
    }
    getEcpayReturnUrl() {
        const cfg = this.get();
        const explicitUrl = cfg.ECPAY_RETURN_URL.trim();
        if (explicitUrl) {
            return explicitUrl;
        }
        const baseUrl = this.getAppPublicBaseUrl();
        if (!baseUrl) {
            return '';
        }
        return baseUrl + normalizeCallbackPath(cfg.ECPAY_CALLBACK_PATH);
    }
    getEcpayStageMode() {
        return this.get().ECPAY_STAGE_MODE;
    }
    getEcpayCvsExpireMinutes() {
        return this.get().ECPAY_CVS_EXPIRE_MINUTES;
    }
    getEcpayCallbackBindHost() {
        return this.get().ECPAY_CALLBACK_BIND_HOST;
    }
    getEcpayCallbackBindPort() {
        return this.get().ECPAY_CALLBACK_BIND_PORT;
    }
    getEcpayCallbackPath() {
        return this.get().ECPAY_CALLBACK_PATH;
    }
    /** Returns the raw config object for advanced usage. */
    getConfig() {
        return this.get();
    }
}
function normalizePublicBaseUrl(rawValue) {
    const normalized = rawValue.trim();
    if (!normalized) {
        return '';
    }
    let result = normalized;
    if (!/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\/.*$/.test(result)) {
        result = 'https://' + result;
    }
    while (result.endsWith('/')) {
        result = result.slice(0, -1);
    }
    return result;
}
function normalizeCallbackPath(rawPath) {
    const trimmed = rawPath.trim();
    if (!trimmed) {
        return '/ecpay/callback';
    }
    return trimmed.startsWith('/') ? trimmed : '/' + trimmed;
}
//# sourceMappingURL=environment-config.js.map