import { describe, it, expect } from 'vitest';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { EnvironmentConfig } from '../environment-config.js';

/**
 * Creates an EnvironmentConfig that does not read from the project's .env file.
 * This prevents test pollution from any existing .env file in the repo root.
 */
function createIsolatedConfig(envSource: Record<string, string | undefined>) {
  const tmpDir = mkdtempSync(join(tmpdir(), 'env-test-'));
  return new EnvironmentConfig(tmpDir, envSource);
}

describe('EnvironmentConfig', () => {
  it('parses config from process.env with DISCORD_BOT_TOKEN', () => {
    const config = createIsolatedConfig({
      DISCORD_BOT_TOKEN: 'test-token',
    });
    const parsed = config.parse();
    expect(parsed.DISCORD_BOT_TOKEN).toBe('test-token');
  });

  it('throws if DISCORD_BOT_TOKEN is missing', () => {
    const config = createIsolatedConfig({});
    expect(() => config.parse()).toThrow('Configuration validation failed');
  });

  it('provides typed getters', () => {
    const config = createIsolatedConfig({
      DISCORD_BOT_TOKEN: 'token',
      REDIS_URI: 'redis://myredis:6379',
      DATABASE_HOST: 'db.example.com',
      DATABASE_PORT: '5432',
      AI_SERVICE_BASE_URL: 'https://ai.example.com',
    });
    config.parse();
    expect(config.getDiscordBotToken()).toBe('token');
    expect(config.getRedisUri()).toBe('redis://myredis:6379');
    expect(config.getDatabaseUrl()).toBe(
      'postgresql://postgres:postgres@db.example.com:5432/currency_bot',
    );
    expect(config.getAIServiceBaseUrl()).toBe('https://ai.example.com');
  });

  it('generates database URL from components', () => {
    const config = createIsolatedConfig({
      DISCORD_BOT_TOKEN: 'token',
      DATABASE_HOST: 'myhost',
      DATABASE_PORT: '7777',
      DATABASE_NAME: 'mydb',
      DATABASE_USER: 'admin',
      DATABASE_PASSWORD: 'secret',
    });
    config.parse();
    expect(config.getDatabaseUrl()).toBe(
      'postgresql://admin:secret@myhost:7777/mydb',
    );
  });

  it('uses DB_URL when provided', () => {
    const config = createIsolatedConfig({
      DISCORD_BOT_TOKEN: 'token',
      DB_URL: 'postgresql://custom:url@host/db',
    });
    config.parse();
    expect(config.getDatabaseUrl()).toBe('postgresql://custom:url@host/db');
  });

  it('returns empty strings for unset ECPay values', () => {
    const config = createIsolatedConfig({
      DISCORD_BOT_TOKEN: 'token',
    });
    config.parse();
    expect(config.getEcpayMerchantId()).toBe('');
    expect(config.getEcpayHashKey()).toBe('');
  });

  it('process.env overrides .env values', () => {
    const config = createIsolatedConfig({
      DISCORD_BOT_TOKEN: 'env-token',
      REDIS_URI: 'redis://env:6379',
    });
    config.parse();
    expect(config.getDiscordBotToken()).toBe('env-token');
    expect(config.getRedisUri()).toBe('redis://env:6379');
  });

  it('getAppPublicBaseUrl normalizes with https:// prefix', () => {
    const config = createIsolatedConfig({
      DISCORD_BOT_TOKEN: 'token',
      APP_PUBLIC_BASE_URL: 'example.com',
    });
    config.parse();
    expect(config.getAppPublicBaseUrl()).toBe('https://example.com');
  });

  it('returns defaults for port numbers', () => {
    const config = createIsolatedConfig({
      DISCORD_BOT_TOKEN: 'token',
    });
    config.parse();
    expect(config.getEcpayCallbackBindPort()).toBe(8085);
    expect(config.getEcpayCvsExpireMinutes()).toBe(10080);
    expect(config.getPoolMaxSize()).toBe(10);
  });
});
