import { describe, it, expect, vi } from 'vitest';
import { MockDiscordInteraction, Ok } from '@ltdjms/shared';
import { EscortCatalogHandler } from '../panel/admin/handlers/EscortCatalogHandler.js';
import { EscortPricingHandler } from '../panel/admin/handlers/EscortPricingHandler.js';

// ============================================================
// Helpers
// ============================================================

const MAX_ROWS = 5;
const GUILD_ID = '1';
const USER_ID = '100';

interface SessionMock {
  getSession: ReturnType<typeof vi.fn>;
  setViewState: ReturnType<typeof vi.fn>;
  getContext: ReturnType<typeof vi.fn>;
  setContext: ReturnType<typeof vi.fn>;
}

function createSessionManager(session: Record<string, unknown> | null): SessionMock {
  return {
    getSession: vi.fn().mockReturnValue(session),
    setViewState: vi.fn(),
    getContext: vi.fn().mockReturnValue(null),
    setContext: vi.fn(),
  };
}

function createErrorHandler() {
  return { handle: vi.fn() };
}

function makeCatalogEntries(count: number) {
  return Array.from({ length: count }, (_, i) => ({
    code: `CODE_${i}`,
    type: '一般護航',
    level: '一般',
    mapScope: `地圖${i}`,
    target: `Boss${i}`,
    priceTwd: 1000 + i * 100,
  }));
}

function makePricingEntries(count: number) {
  return Array.from({ length: count }, (_, i) => ({
    optionCode: `CODE_${i}`,
    option: {
      code: `CODE_${i}`,
      type: '一般護航',
      level: '一般',
      mapScope: `地圖${i}`,
      target: `Boss${i}`,
      defaultPriceTwd: 1000,
    },
    defaultPriceTwd: 1000,
    effectivePriceTwd: 1000 + i * 100,
    overridden: i % 2 === 0,
  }));
}

/**
 * Tracks the number of ActionRows passed to editWithComponents.
 * Returns the number of rows, or -1 if editWithComponents was not called.
 */
async function captureRowCount(
  handler: { execute: (interaction: MockDiscordInteraction, context: unknown) => Promise<void> },
  interaction: MockDiscordInteraction,
): Promise<number> {
  const spy = vi.spyOn(interaction, 'editWithComponents');

  await handler.execute(interaction, undefined);

  if (spy.mock.calls.length === 0) return -1;

  const components = spy.mock.calls[0][1];
  return Array.isArray(components) ? components.length : -1;
}

// ============================================================
// EscortCatalogHandler
// ============================================================

describe('EscortCatalogHandler — ActionRow limit', () => {
  const validSession = {
    guildId: GUILD_ID,
    userId: USER_ID,
    viewState: 0,
    context: {},
    createdAt: Date.now(),
    lastAccessedAt: Date.now(),
  };

  function createHandler(entriesCount: number) {
    const sessionManager = createSessionManager(validSession);
    const facade = {
      listCatalog: vi.fn().mockResolvedValue(new Ok(makeCatalogEntries(entriesCount))),
    };
    const modalFactory = {};
    const errorHandler = createErrorHandler();

    const handler = new EscortCatalogHandler(
      sessionManager as never,
      facade as never,
      modalFactory as never,
      errorHandler as never,
    );

    return { handler, sessionManager, facade, errorHandler };
  }

  function makeInteraction(customId = 'admin_escortcatalog') {
    return new MockDiscordInteraction(GUILD_ID, USER_ID, undefined, false, customId, true);
  }

  it.each([
    [0, 'empty catalog'],
    [1, '1 entry'],
    [3, '3 entries (full page, no pagination)'],
    [4, '4 entries (triggers pagination, 2 pages)'],
    [6, '6 entries (2 full pages)'],
    [9, '9 entries (3 pages)'],
    [100, '100 entries (many pages)'],
  ])('should not exceed 5 ActionRows with %s', async (_count, _label) => {
    const count = _count as number;
    const { handler } = createHandler(count);
    const interaction = makeInteraction();

    const rowCount = await captureRowCount(handler, interaction);
    expect(rowCount).toBeGreaterThanOrEqual(1);
    expect(rowCount).toBeLessThanOrEqual(MAX_ROWS);
  });

  it('should handle empty catalog gracefully (1 row: add button only)', async () => {
    const { handler } = createHandler(0);
    const interaction = makeInteraction();

    const rowCount = await captureRowCount(handler, interaction);
    expect(rowCount).toBe(1);
  });
});

// ============================================================
// EscortPricingHandler
// ============================================================

describe('EscortPricingHandler — ActionRow limit', () => {
  const validSession = {
    guildId: GUILD_ID,
    userId: USER_ID,
    viewState: 0,
    context: {},
    createdAt: Date.now(),
    lastAccessedAt: Date.now(),
  };

  function createHandler(entriesCount: number) {
    const sessionManager = createSessionManager(validSession);
    const facade = {
      listPricing: vi.fn().mockResolvedValue(new Ok(makePricingEntries(entriesCount))),
    };
    const modalFactory = {};
    const errorHandler = createErrorHandler();

    const handler = new EscortPricingHandler(
      sessionManager as never,
      facade as never,
      modalFactory as never,
      errorHandler as never,
    );

    return { handler, sessionManager, facade, errorHandler };
  }

  function makeInteraction(customId = 'admin_escortprice') {
    return new MockDiscordInteraction(GUILD_ID, USER_ID, undefined, false, customId, true);
  }

  it.each([
    [0, 'empty pricing — 0 rows'],
    [1, '1 entry — 1 row (1 item row)'],
    [2, '2 entries — 1 row (1 item row)'],
    [5, '5 entries — 3 rows (3 item rows)'],
    [6, '6 entries (full page, no nav) — 3 rows'],
    [7, '7 entries (2 pages, page 1) — 3 item + 1 nav = 4 rows'],
    [10, '10 entries (2 pages, page 1) — 3 item + 1 nav = 4 rows'],
    [12, '12 entries (2 pages, page 1) — 3 item + 1 nav = 4 rows'],
    [20, '20 entries (4 pages, page 1) — 3 item + 1 nav = 4 rows'],
  ])('should not exceed 5 ActionRows with %s', async (_count, _label) => {
    const count = _count as number;
    const { handler } = createHandler(count);
    const interaction = makeInteraction();

    const rowCount = await captureRowCount(handler, interaction);
    expect(rowCount).toBeGreaterThanOrEqual(0);
    expect(rowCount).toBeLessThanOrEqual(MAX_ROWS);
  });

  it('should render empty pricing as text-only embed (0 rows)', async () => {
    const { handler } = createHandler(0);
    const interaction = makeInteraction();

    const rowCount = await captureRowCount(handler, interaction);
    expect(rowCount).toBe(0);
  });
});
