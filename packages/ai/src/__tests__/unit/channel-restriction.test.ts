import 'reflect-metadata';
import { describe, it, expect, beforeEach } from 'vitest';
import {
  InMemoryAIChannelRestrictionRepository,
  DefaultAIChannelRestrictionService,
} from '../../services/routing/channel-restriction-service.js';
import { DomainErrorCategory } from '@ltdjms/shared';

describe('AIChannelRestrictionService', () => {
  let repo: InMemoryAIChannelRestrictionRepository;
  let service: DefaultAIChannelRestrictionService;

  beforeEach(() => {
    repo = new InMemoryAIChannelRestrictionRepository();
    service = new DefaultAIChannelRestrictionService(repo);
  });

  describe('isChannelAllowed', () => {
    it('should return true when channel is in the allowlist', async () => {
      await repo.addChannel('guild-1', { channelId: 'ch-1', channelName: 'general' });
      const result = await service.isChannelAllowed('guild-1', 'ch-1');
      expect(result).toBe(true);
    });

    it('should return true when category is allowlisted and channel belongs to it', async () => {
      await repo.addCategory('guild-1', { categoryId: 'cat-1', categoryName: 'Text' });
      const result = await service.isChannelAllowed('guild-1', 'ch-1', 'cat-1');
      expect(result).toBe(true);
    });

    it('should return false when no allowlist exists (default deny)', async () => {
      const result = await service.isChannelAllowed('guild-1', 'ch-1');
      expect(result).toBe(false);
    });

    it('should return false when channel is not in allowlist and no category matches', async () => {
      await repo.addChannel('guild-1', { channelId: 'ch-1', channelName: 'general' });
      const result = await service.isChannelAllowed('guild-1', 'ch-2', 'cat-1');
      expect(result).toBe(false);
    });

    it('should check channel allowlist first, then category allowlist', async () => {
      await repo.addChannel('guild-1', { channelId: 'ch-1', channelName: 'general' });
      await repo.addCategory('guild-1', { categoryId: 'cat-1', categoryName: 'Text' });

      // Channel match
      expect(await service.isChannelAllowed('guild-1', 'ch-1', 'cat-1')).toBe(true);
      // Category match
      expect(await service.isChannelAllowed('guild-1', 'ch-2', 'cat-1')).toBe(true);
      // No match
      expect(await service.isChannelAllowed('guild-1', 'ch-3')).toBe(false);
    });
  });

  describe('addAllowedChannel', () => {
    it('should add a channel successfully', async () => {
      const result = await service.addAllowedChannel('guild-1', {
        channelId: 'ch-1',
        channelName: 'general',
      });
      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue().channelId).toBe('ch-1');
      }
    });

    it('should return error for duplicate channel', async () => {
      await service.addAllowedChannel('guild-1', {
        channelId: 'ch-1',
        channelName: 'general',
      });
      const result = await service.addAllowedChannel('guild-1', {
        channelId: 'ch-1',
        channelName: 'general',
      });
      expect(result.isErr()).toBe(true);
      if (result.isErr()) {
        expect(result.getError().category).toBe(DomainErrorCategory.DUPLICATE_CHANNEL);
      }
    });
  });

  describe('removeAllowedChannel', () => {
    it('should remove a channel successfully', async () => {
      await service.addAllowedChannel('guild-1', {
        channelId: 'ch-1',
        channelName: 'general',
      });
      const result = await service.removeAllowedChannel('guild-1', 'ch-1');
      expect(result.isOk()).toBe(true);
      expect(await service.isChannelAllowed('guild-1', 'ch-1')).toBe(false);
    });

    it('should return error when removing non-existent channel', async () => {
      const result = await service.removeAllowedChannel('guild-1', 'ch-999');
      expect(result.isErr()).toBe(true);
      if (result.isErr()) {
        expect(result.getError().category).toBe(DomainErrorCategory.CHANNEL_NOT_FOUND);
      }
    });
  });

  describe('addAllowedCategory', () => {
    it('should add a category successfully', async () => {
      const result = await service.addAllowedCategory('guild-1', {
        categoryId: 'cat-1',
        categoryName: 'Text',
      });
      expect(result.isOk()).toBe(true);
    });

    it('should return error for duplicate category', async () => {
      await service.addAllowedCategory('guild-1', {
        categoryId: 'cat-1',
        categoryName: 'Text',
      });
      const result = await service.addAllowedCategory('guild-1', {
        categoryId: 'cat-1',
        categoryName: 'Text',
      });
      expect(result.isErr()).toBe(true);
      if (result.isErr()) {
        expect(result.getError().category).toBe(DomainErrorCategory.DUPLICATE_CATEGORY);
      }
    });
  });

  describe('removeAllowedCategory', () => {
    it('should remove a category successfully', async () => {
      await service.addAllowedCategory('guild-1', {
        categoryId: 'cat-1',
        categoryName: 'Text',
      });
      const result = await service.removeAllowedCategory('guild-1', 'cat-1');
      expect(result.isOk()).toBe(true);
    });

    it('should return error when removing non-existent category', async () => {
      const result = await service.removeAllowedCategory('guild-1', 'cat-999');
      expect(result.isErr()).toBe(true);
      if (result.isErr()) {
        expect(result.getError().category).toBe(DomainErrorCategory.CATEGORY_NOT_FOUND);
      }
    });
  });

  describe('getAllowedChannels and getAllowedCategories', () => {
    it('should return all allowed channels for a guild', async () => {
      await service.addAllowedChannel('guild-1', {
        channelId: 'ch-1',
        channelName: 'general',
      });
      await service.addAllowedChannel('guild-1', {
        channelId: 'ch-2',
        channelName: 'bot-commands',
      });

      const result = await service.getAllowedChannels('guild-1');
      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue()).toHaveLength(2);
      }
    });

    it('should return all allowed categories for a guild', async () => {
      await service.addAllowedCategory('guild-1', {
        categoryId: 'cat-1',
        categoryName: 'Text',
      });

      const result = await service.getAllowedCategories('guild-1');
      expect(result.isOk()).toBe(true);
      if (result.isOk()) {
        expect(result.getValue()).toHaveLength(1);
      }
    });
  });
});
