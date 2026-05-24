import { type Message } from 'discord.js';

/**
 * Tracks reasoning messages for cleanup after streaming completes.
 * Matches Java AIChatMentionListener.ReasoningMessageTracker.
 */
export class ReasoningMessageTracker {
  private initialMessage: Message | null = null;
  private reasoningMessages: Message[] = [];
  private deletionRequested = false;

  setInitialMessage(message: Message | null): void {
    if (!message) {
      return;
    }
    if (this.deletionRequested) {
      void this.deleteMessage(message);
      return;
    }
    this.initialMessage = message;
  }

  addReasoningMessage(message: Message | null): void {
    if (!message) {
      return;
    }
    if (this.deletionRequested) {
      void this.deleteMessage(message);
      return;
    }
    this.reasoningMessages.push(message);
  }

  /**
   * Deletes only reasoning messages, keeping the initial message intact.
   */
  async deleteReasoningMessages(): Promise<void> {
    await Promise.allSettled(this.reasoningMessages.map((msg) => this.deleteMessage(msg)));
    this.reasoningMessages = [];
  }

  /**
   * Deletes all tracked messages and invokes callback when complete.
   */
  async deleteAll(completionCallback?: () => void): Promise<void> {
    this.deletionRequested = true;

    const toDelete: Message[] = [];
    if (this.initialMessage) {
      toDelete.push(this.initialMessage);
    }
    toDelete.push(...this.reasoningMessages);
    this.reasoningMessages = [];
    this.initialMessage = null;

    if (toDelete.length === 0) {
      completionCallback?.();
      return;
    }

    let deletedCount = 0;
    const total = toDelete.length;
    await Promise.allSettled(
      toDelete.map(async (msg) => {
        await this.deleteMessage(msg);
        deletedCount++;
        if (deletedCount === total) {
          completionCallback?.();
        }
      }),
    );
  }

  private async deleteMessage(message: Message): Promise<void> {
    try {
      await message.delete();
    } catch {
      // Ignore deletion failures
    }
  }
}
