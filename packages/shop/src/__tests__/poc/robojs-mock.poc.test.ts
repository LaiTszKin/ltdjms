import { describe, it, expect, afterAll } from 'vitest';

/**
 * POC-ED-004: @robojs/mock smoke test.
 *
 * LTDJMS is not a Robo.js bot — full startMockRobo() requires a Robo project entrypoint.
 * This PoC validates session infrastructure; shop-java-parity falls back to shared
 * MockDiscordInteraction for handler-level slash/button tests (see contract.md EXT-005).
 */
describe('@robojs/mock smoke (POC-ED-004)', () => {
  let sessionId: string | undefined;

  afterAll(async () => {
    if (!sessionId) return;
    try {
      const { sessionManager } = await import('@robojs/mock');
      await sessionManager.delete(sessionId);
    } catch {
      // best-effort cleanup
    }
  });

  it('creates an isolated mock session with guild and channel fixtures', async () => {
    const { sessionManager } = await import('@robojs/mock');

    const session = await sessionManager.create({
      name: 'shop-poc',
      config: {
        guilds: [{ name: 'LTDJMS Test Guild' }],
        users: [{ username: 'ShopTester' }],
      },
    });

    sessionId = session.id;
    expect(session.id).toMatch(/^sess_/);

    const state = session.state;
    expect(state.guilds.size).toBeGreaterThan(0);
    expect(state.channels.size).toBeGreaterThan(0);

    const guild = state.guilds.values().next().value;
    expect(guild?.name).toBe('LTDJMS Test Guild');
  });

  it('documents hand-mock fallback for /shop handler parity', () => {
    // shop-java-parity UT-306–308 will use @ltdjms/shared MockDiscordInteraction
    // when @robojs/mock cannot drive non-Robo Discord.js bots end-to-end.
    expect(true).toBe(true);
  });
});
