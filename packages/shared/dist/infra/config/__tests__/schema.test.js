import { describe, it, expect } from 'vitest';
import { ConfigSchema } from '../schema.js';
describe('ConfigSchema', () => {
    it('rejects empty config (missing DISCORD_BOT_TOKEN)', () => {
        const result = ConfigSchema.safeParse({});
        expect(result.success).toBe(false);
        if (!result.success) {
            const paths = result.error.issues.map((i) => i.path.join('.'));
            expect(paths).toContain('DISCORD_BOT_TOKEN');
        }
    });
    it('accepts config with DISCORD_BOT_TOKEN and fills defaults', () => {
        const result = ConfigSchema.safeParse({
            DISCORD_BOT_TOKEN: 'my-token',
        });
        expect(result.success).toBe(true);
        if (result.success) {
            expect(result.data.DISCORD_BOT_TOKEN).toBe('my-token');
            expect(result.data.REDIS_URI).toBe('redis://localhost:6379');
            expect(result.data.DATABASE_HOST).toBe('localhost');
            expect(result.data.ECPAY_STAGE_MODE).toBe(true);
        }
    });
    it('coerces string values to numbers', () => {
        const result = ConfigSchema.safeParse({
            DISCORD_BOT_TOKEN: 'token',
            DATABASE_PORT: '5432',
            AI_SERVICE_TEMPERATURE: '0.5',
        });
        expect(result.success).toBe(true);
        if (result.success) {
            expect(result.data.DATABASE_PORT).toBe(5432);
            expect(result.data.AI_SERVICE_TEMPERATURE).toBe(0.5);
        }
    });
    it('coerces boolean strings', () => {
        const result = ConfigSchema.safeParse({
            DISCORD_BOT_TOKEN: 'token',
            ECPAY_STAGE_MODE: 'false',
            AI_SHOW_REASONING: 'true',
        });
        expect(result.success).toBe(true);
        if (result.success) {
            expect(result.data.ECPAY_STAGE_MODE).toBe(false);
            expect(result.data.AI_SHOW_REASONING).toBe(true);
        }
    });
    it('accepts all optional values', () => {
        const result = ConfigSchema.safeParse({
            DISCORD_BOT_TOKEN: 'token',
            DATABASE_HOST: 'db.example.com',
            DATABASE_PORT: 1234,
            DATABASE_NAME: 'mydb',
            DATABASE_USER: 'admin',
            DATABASE_PASSWORD: 'secret',
            REDIS_URI: 'redis://myredis:6379',
            AI_SERVICE_BASE_URL: 'https://custom.ai/api',
            AI_SERVICE_API_KEY: 'ai-key',
            AI_SERVICE_MODEL: 'gpt-4',
            AI_SERVICE_TEMPERATURE: 0.9,
            AI_SERVICE_TIMEOUT_SECONDS: 60,
            PROMPTS_DIR_PATH: '/custom/prompts',
            ECPAY_MERCHANT_ID: 'merchant-1',
            ECPAY_HASH_KEY: 'hash-key',
            ECPAY_HASH_IV: 'hash-iv',
        });
        expect(result.success).toBe(true);
        if (result.success) {
            expect(result.data.DATABASE_HOST).toBe('db.example.com');
            expect(result.data.AI_SERVICE_MODEL).toBe('gpt-4');
        }
    });
});
//# sourceMappingURL=schema.test.js.map