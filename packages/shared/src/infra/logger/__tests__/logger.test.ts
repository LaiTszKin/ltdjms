import { describe, it, expect } from 'vitest';
import { createRootLogger, createChildLogger } from '../logger.js';

describe('Logger', () => {
  it('creates a root logger', () => {
    const logger = createRootLogger('info');
    expect(logger).toBeDefined();
    expect(logger.level).toBe('info');
  });

  it('creates child logger with bindings', () => {
    const parent = createRootLogger('info');
    const child = createChildLogger(parent, { module: 'test' });
    expect(child).toBeDefined();
    // Child logger should be bound to parent
    expect((child as any).bindings).toBeDefined();
  });

  it('supports different log levels', () => {
    const debugLogger = createRootLogger('debug');
    expect(debugLogger.level).toBe('debug');

    const warnLogger = createRootLogger('warn');
    expect(warnLogger.level).toBe('warn');

    const errorLogger = createRootLogger('error');
    expect(errorLogger.level).toBe('error');
  });
});
