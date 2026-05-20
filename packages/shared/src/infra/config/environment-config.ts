import { join } from 'node:path';
import pino, { type Logger } from 'pino';
import { loadDotEnv } from './env-loader.js';
import { ConfigSchema, type ConfigValues } from './schema.js';

/**
 * Loads configuration from environment variables with fallback to .env file and Zod defaults.
 *
 * Priority order (highest to lowest):
 * 1. System environment variables (process.env)
 * 2. .env file in project root
 * 3. Zod schema defaults
 */
export class EnvironmentConfig {
  private config: ConfigValues | null = null;

  constructor(
    private readonly dotEnvDirectory?: string,
    private readonly envSource: Record<string, string | undefined> = process.env as Record<
      string,
      string | undefined
    >,
    private readonly logger: Logger = pino({ level: 'silent' }),
  ) {}

  /**
   * Parses and validates configuration from all sources.
   * @returns the validated config values
   * @throws Error if required fields are missing
   */
  parse(): ConfigValues {
    if (this.config) {
      return this.config;
    }

    // Load .env file values
    const dotEnvPath = this.dotEnvDirectory
      ? join(this.dotEnvDirectory, '.env')
      : join(process.cwd(), '.env');
    const dotEnvValues = loadDotEnv(dotEnvPath);

    // Merge: process.env (highest) > .env file > Zod defaults
    const merged: Record<string, string | undefined> = { ...dotEnvValues };

    // process.env values override .env values
    for (const key of Object.keys(ConfigSchema.shape)) {
      const envVal = this.envSource[key];
      if (envVal !== undefined && envVal !== '') {
        merged[key] = envVal;
      } else if (dotEnvValues[key] !== undefined) {
        merged[key] = dotEnvValues[key];
      }
    }

    // Log process.env keys that are not in the schema
    const schemaKeys = new Set(Object.keys(ConfigSchema.shape));
    const processEnvWithoutSchemaKeys = Object.keys(this.envSource).filter(
      (k) => !schemaKeys.has(k),
    );
    if (processEnvWithoutSchemaKeys.length > 0) {
      this.logger.debug(
        { extraKeys: processEnvWithoutSchemaKeys },
        'Ignored process.env keys not in schema',
      );
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
  protected get(): ConfigValues {
    if (!this.config) {
      return this.parse();
    }
    return this.config;
  }

  // ---- Discord ----

  getDiscordBotToken(): string {
    return this.get().DISCORD_BOT_TOKEN;
  }

  // ---- Database ----

  getDatabaseUrl(): string {
    const cfg = this.get();
    if (cfg.DB_URL) {
      return cfg.DB_URL;
    }
    const user = cfg.DB_USERNAME ?? cfg.DATABASE_USER;
    const pass = cfg.DB_PASSWORD ?? cfg.DATABASE_PASSWORD;
    return `postgresql://${user}:${pass}@${cfg.DATABASE_HOST}:${cfg.DATABASE_PORT}/${cfg.DATABASE_NAME}`;
  }

  getDatabaseUsername(): string {
    const cfg = this.get();
    return cfg.DB_USERNAME ?? cfg.DATABASE_USER;
  }

  getDatabasePassword(): string {
    const cfg = this.get();
    return cfg.DB_PASSWORD ?? cfg.DATABASE_PASSWORD;
  }

  getPoolMaxSize(): number {
    return this.get().DB_POOL_MAX_SIZE;
  }

  getPoolMinIdle(): number {
    return this.get().DB_POOL_MIN_IDLE;
  }

  getPoolConnectionTimeout(): number {
    return this.get().DB_POOL_CONNECTION_TIMEOUT;
  }

  getPoolIdleTimeout(): number {
    return this.get().DB_POOL_IDLE_TIMEOUT;
  }

  getPoolMaxLifetime(): number {
    return this.get().DB_POOL_MAX_LIFETIME;
  }

  // ---- Redis ----

  getRedisUri(): string {
    return this.get().REDIS_URI;
  }

  // ---- AI Service ----

  getAIServiceBaseUrl(): string {
    return this.get().AI_SERVICE_BASE_URL;
  }

  getAIServiceApiKey(): string {
    return this.get().AI_SERVICE_API_KEY;
  }

  getAIServiceModel(): string {
    return this.get().AI_SERVICE_MODEL;
  }

  getAIServiceTemperature(): number {
    return this.get().AI_SERVICE_TEMPERATURE;
  }

  getAIServiceTimeoutSeconds(): number {
    return this.get().AI_SERVICE_TIMEOUT_SECONDS;
  }

  // ---- Prompts ----

  getPromptsDirPath(): string {
    return this.get().PROMPTS_DIR_PATH;
  }

  getPromptMaxSizeBytes(): number {
    return this.get().PROMPT_MAX_SIZE_BYTES;
  }

  // ---- AI Features ----

  getAIShowReasoning(): boolean {
    return this.get().AI_SHOW_REASONING;
  }

  getAIMarkdownValidationEnabled(): boolean {
    return this.get().AI_MARKDOWN_VALIDATION_ENABLED;
  }

  getAIMarkdownValidationStreamingBypass(): boolean {
    return this.get().AI_MARKDOWN_VALIDATION_STREAMING_BYPASS;
  }

  // ---- App ----

  getAppPublicBaseUrl(): string {
    return normalizePublicBaseUrl(this.get().APP_PUBLIC_BASE_URL);
  }

  // ---- ECPay ----

  getEcpayMerchantId(): string {
    return this.get().ECPAY_MERCHANT_ID;
  }

  getEcpayHashKey(): string {
    return this.get().ECPAY_HASH_KEY;
  }

  getEcpayHashIv(): string {
    return this.get().ECPAY_HASH_IV;
  }

  getEcpayReturnUrl(): string {
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

  getEcpayStageMode(): boolean {
    return this.get().ECPAY_STAGE_MODE;
  }

  getEcpayCvsExpireMinutes(): number {
    return this.get().ECPAY_CVS_EXPIRE_MINUTES;
  }

  getEcpayCallbackBindHost(): string {
    return this.get().ECPAY_CALLBACK_BIND_HOST;
  }

  getEcpayCallbackBindPort(): number {
    return this.get().ECPAY_CALLBACK_BIND_PORT;
  }

  getEcpayCallbackPath(): string {
    return this.get().ECPAY_CALLBACK_PATH;
  }

  /** Returns the raw config object for advanced usage. */
  getConfig(): ConfigValues {
    return this.get();
  }
}

function normalizePublicBaseUrl(rawValue: string): string {
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

function normalizeCallbackPath(rawPath: string): string {
  const trimmed = rawPath.trim();
  if (!trimmed) {
    return '/ecpay/callback';
  }
  return trimmed.startsWith('/') ? trimmed : '/' + trimmed;
}
