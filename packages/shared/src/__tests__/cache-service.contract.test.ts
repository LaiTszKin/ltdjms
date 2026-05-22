import { describe, it, expect } from 'vitest';
import { NoOpCacheService } from '../infra/cache/noop-cache-service.js';
import type { CacheService } from '../infra/cache/cache-service.js';

describe('CacheService contract', () => {
  describe('NoOpCacheService', () => {
    const noop: CacheService = NoOpCacheService.getInstance();

    it('get always returns null', async () => {
      const result = await noop.get('any-key');
      expect(result).toBeNull();
    });

    it('get returns null for any key name', async () => {
      const result = await noop.get('another-key');
      expect(result).toBeNull();
    });

    it('put is a no-op and never throws', async () => {
      await expect(noop.put('some-key', { data: 123 }, 300)).resolves.toBeUndefined();
    });

    it('put with zero TTL is a no-op', async () => {
      await expect(noop.put('ttl-key', 'value', 0)).resolves.toBeUndefined();
    });

    it('invalidate is a no-op and never throws', async () => {
      await expect(noop.invalidate('some-key')).resolves.toBeUndefined();
    });

    it('get still returns null after put (no-op contract)', async () => {
      await noop.put('stored-key', 'stored-value', 300);
      const result = await noop.get('stored-key');
      expect(result).toBeNull();
    });
  });
});
