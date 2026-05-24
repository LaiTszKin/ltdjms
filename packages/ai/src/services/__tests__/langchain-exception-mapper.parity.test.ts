import { describe, it, expect } from 'vitest';
import { LangChainExceptionMapper } from '../../services/LangChainExceptionMapper.js';
import { DomainErrorCategory } from '@ltdjms/shared';

/** UT-AIC-016 — LangChain4jExceptionMapperTest.java parity */
describe('UT-AIC-016 langchain exception mapper parity', () => {
  const mapper = new LangChainExceptionMapper();

  it('maps null to empty response', () => {
    const error = mapper.map(null);
    expect(error.category).toBe(DomainErrorCategory.AI_RESPONSE_EMPTY);
  });

  it('maps timeout exceptions', () => {
    const error = mapper.map(new Error('Connection timeout after 30s'));
    expect(error.category).toBe(DomainErrorCategory.AI_SERVICE_TIMEOUT);
  });

  it('maps 503 to unavailable', () => {
    const error = mapper.map(new Error('HTTP 503 Service Unavailable'));
    expect(error.category).toBe(DomainErrorCategory.AI_SERVICE_UNAVAILABLE);
  });

  it('maps 500 to unavailable', () => {
    const error = mapper.map(new Error('HTTP 500 Internal Server Error'));
    expect(error.category).toBe(DomainErrorCategory.AI_SERVICE_UNAVAILABLE);
  });

  it('maps connection reset style failures', () => {
    const error = mapper.map(Object.assign(new Error('socket hang up'), { code: 'ECONNRESET' }));
    expect(error.category).toBe(DomainErrorCategory.AI_SERVICE_UNAVAILABLE);
  });

  it('maps auth failures', () => {
    const error = mapper.map(Object.assign(new Error('Unauthorized'), { status: 401 }));
    expect(error.category).toBe(DomainErrorCategory.AI_SERVICE_AUTH_FAILED);
  });

  it('maps rate limit failures', () => {
    const error = mapper.map(Object.assign(new Error('Too many requests'), { status: 429 }));
    expect(error.category).toBe(DomainErrorCategory.AI_SERVICE_RATE_LIMITED);
  });
});
