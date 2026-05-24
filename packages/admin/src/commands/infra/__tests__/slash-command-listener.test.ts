import { describe, it, expect, vi } from 'vitest';
import type { DiscordRuntimeGateway } from '@ltdjms/shared';
import { SlashCommandListener } from '../SlashCommandListener.js';

describe('SlashCommandListener idempotency', () => {
  it('should only attach one interactionCreate listener when listen is called twice', () => {
    const listener = new SlashCommandListener();
    const on = vi.fn();
    const gateway = {
      requireReadyClient: () => ({ on }),
    } as unknown as DiscordRuntimeGateway;

    listener.listen(gateway);
    listener.listen(gateway);

    expect(on).toHaveBeenCalledTimes(1);
    expect(on).toHaveBeenCalledWith('interactionCreate', expect.any(Function));
  });
});
