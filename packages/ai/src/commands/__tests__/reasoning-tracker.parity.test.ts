import { describe, it, expect, vi } from 'vitest';
import { ReasoningMessageTracker } from '../reasoning-message-tracker.js';
import type { Message } from 'discord.js';

/** UT-AIC-014 — ReasoningMessageTrackerTest.java parity */
describe('UT-AIC-014 reasoning-tracker parity', () => {
  it('deleteAll_shouldDeleteMessagesAddedAfterDeletionRequest', async () => {
    const tracker = new ReasoningMessageTracker();

    const initialMessage = mockMessage();
    const existingReasoningMessage = mockMessage();
    const lateReasoningMessage = mockMessage();

    tracker.setInitialMessage(initialMessage);
    tracker.addReasoningMessage(existingReasoningMessage);

    await tracker.deleteAll();

    tracker.addReasoningMessage(lateReasoningMessage);

    expect(initialMessage.delete).toHaveBeenCalled();
    expect(existingReasoningMessage.delete).toHaveBeenCalled();
    expect(lateReasoningMessage.delete).toHaveBeenCalled();
  });
});

function mockMessage(): Message {
  return {
    delete: vi.fn().mockResolvedValue(undefined),
  } as unknown as Message;
}
