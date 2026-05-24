import 'reflect-metadata';
import { describe, it, expect } from 'vitest';
import { AIServiceConfig, AIServiceConfigSchema } from '../../config/ai-service-config.js';
import { DomainErrorCategory } from '@ltdjms/shared';

/** UT-AIC-009 — AIServiceConfigTest.java parity */
describe('UT-AIC-009 ai-service-config parity', () => {
  it('valid config passes validation', () => {
    const config = AIServiceConfig.fromValues({
      baseUrl: 'https://api.openai.com/v1',
      apiKey: 'test-api-key',
      model: 'gpt-3.5-turbo',
      temperature: 0.7,
      timeoutSeconds: 30,
    });
    expect(config.validate().isOk()).toBe(true);
  });

  it('missing baseUrl fails validation', () => {
    const result = AIServiceConfigSchema.safeParse({
      baseUrl: '',
      apiKey: 'test-api-key',
      model: 'gpt-3.5-turbo',
      temperature: 0.7,
      timeoutSeconds: 30,
      showReasoning: false,
      enableMarkdownValidation: true,
      streamingBypassValidation: false,
    });
    expect(result.success).toBe(false);
  });

  it('temperature too high fails validation', () => {
    const result = AIServiceConfigSchema.safeParse({
      baseUrl: 'https://api.openai.com/v1',
      apiKey: 'key',
      model: 'gpt-3.5-turbo',
      temperature: 2.1,
      timeoutSeconds: 30,
      showReasoning: false,
      enableMarkdownValidation: true,
      streamingBypassValidation: false,
    });
    expect(result.success).toBe(false);
  });

  it('timeout too low fails validation', () => {
    const result = AIServiceConfigSchema.safeParse({
      baseUrl: 'https://api.openai.com/v1',
      apiKey: 'key',
      model: 'gpt-3.5-turbo',
      temperature: 0.7,
      timeoutSeconds: 0,
      showReasoning: false,
      enableMarkdownValidation: true,
      streamingBypassValidation: false,
    });
    expect(result.success).toBe(false);
  });

  it('showReasoning defaults to false', () => {
    const config = AIServiceConfig.fromValues({
      baseUrl: 'https://api.openai.com/v1',
      apiKey: 'key',
      model: 'gpt-3.5-turbo',
    });
    expect(config.showReasoning).toBe(false);
  });

  it('validate returns INVALID_INPUT for invalid instance fields', () => {
    const config = AIServiceConfig.fromValues({
      baseUrl: 'https://api.openai.com/v1',
      apiKey: 'key',
      model: 'gpt-3.5-turbo',
    });
    const invalid = Object.assign(Object.create(Object.getPrototypeOf(config)), config, {
      timeoutSeconds: 999,
    }) as AIServiceConfig;
    const result = invalid.validate();
    expect(result.isErr()).toBe(true);
    if (result.isErr()) {
      expect(result.getError().category).toBe(DomainErrorCategory.INVALID_INPUT);
    }
  });
});
