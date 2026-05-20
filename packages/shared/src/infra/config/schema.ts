import { z } from 'zod';

/**
 * Custom boolean coercion that properly handles 'true'/'false' strings.
 */
function coerceBoolean() {
  return z.preprocess((val) => {
    if (typeof val === 'string') {
      const lower = val.toLowerCase().trim();
      if (lower === 'true') return true;
      if (lower === 'false') return false;
    }
    return val;
  }, z.boolean());
}

/**
 * Zod schema for all configuration values.
 * Matches the Java EnvironmentConfig fields.
 *
 * NOTE: The Java implementation has an additional Typesafe Config fallback layer via
 * application.properties. This is intentionally omitted here for simplicity —
 * Zod's default() serves as the built-in default layer, and EnvironmentConfig
 * merges process.env > .env file > Zod defaults.
 */
export const ConfigSchema = z.object({
  // Discord
  DISCORD_BOT_TOKEN: z.string().min(1, 'Discord bot token is required'),

  // Database
  DATABASE_HOST: z.string().default('localhost'),
  DATABASE_PORT: z.coerce.number().int().positive().default(5432),
  DATABASE_NAME: z.string().default('currency_bot'),
  /** @deprecated Use DATABASE_USER instead. Kept for backward compatibility. */
  DB_USERNAME: z.string().optional(),
  DATABASE_USER: z.string().default('postgres'),
  /** @deprecated Use DATABASE_PASSWORD instead. Kept for backward compatibility. */
  DB_PASSWORD: z.string().optional(),
  DATABASE_PASSWORD: z.string().default('postgres'),
  DB_URL: z.string().optional(),

  // Connection pool
  DB_POOL_MAX_SIZE: z.coerce.number().int().positive().default(10),
  DB_POOL_MIN_IDLE: z.coerce.number().int().min(0).default(2),
  DB_POOL_CONNECTION_TIMEOUT: z.coerce.number().positive().default(30000),
  DB_POOL_IDLE_TIMEOUT: z.coerce.number().positive().default(600000),
  DB_POOL_MAX_LIFETIME: z.coerce.number().positive().default(1800000),

  // Redis
  REDIS_URI: z.string().default('redis://localhost:6379'),

  // AI Service
  AI_SERVICE_BASE_URL: z.string().default('https://api.openai.com/v1'),
  AI_SERVICE_API_KEY: z.string().default(''),
  AI_SERVICE_MODEL: z.string().default('gpt-3.5-turbo'),
  AI_SERVICE_TEMPERATURE: z.coerce.number().min(0).max(2).default(0.7),
  AI_SERVICE_TIMEOUT_SECONDS: z.coerce.number().int().positive().default(30),

  // Prompts
  PROMPTS_DIR_PATH: z.string().default('./prompts'),
  PROMPT_MAX_SIZE_BYTES: z.coerce.number().positive().default(1048576),

  // AI Features
  AI_SHOW_REASONING: coerceBoolean().default(false),
  AI_MARKDOWN_VALIDATION_ENABLED: coerceBoolean().default(true),
  AI_MARKDOWN_VALIDATION_STREAMING_BYPASS: coerceBoolean().default(false),

  // App
  APP_PUBLIC_BASE_URL: z.string().default(''),

  // ECPay
  ECPAY_MERCHANT_ID: z.string().default(''),
  ECPAY_HASH_KEY: z.string().default(''),
  ECPAY_HASH_IV: z.string().default(''),
  ECPAY_RETURN_URL: z.string().default(''),
  ECPAY_STAGE_MODE: coerceBoolean().default(true),
  ECPAY_CVS_EXPIRE_MINUTES: z.coerce.number().int().positive().default(10080),
  ECPAY_CALLBACK_BIND_HOST: z.string().default('127.0.0.1'),
  ECPAY_CALLBACK_BIND_PORT: z.coerce.number().int().positive().default(8085),
  ECPAY_CALLBACK_PATH: z.string().default('/ecpay/callback'),
});

export type ConfigValues = z.infer<typeof ConfigSchema>;
