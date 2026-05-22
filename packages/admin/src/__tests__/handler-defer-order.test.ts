import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const HANDLERS_DIR = resolve(__dirname, '../panel/admin');

interface HandlerCheck {
  file: string;
  /** CustomId prefix(es) for modal-showing paths that must come before ensureDeferred */
  modalPrefixes: string[];
  /** Whether the handler has modal-showing paths at all */
  hasModalPaths: boolean;
}

const handlers: HandlerCheck[] = [
  {
    file: 'handlers/BalanceManagementHandler.ts',
    modalPrefixes: ['admin_balance_modal_'],
    hasModalPaths: true,
  },
  {
    file: 'handlers/TokenManagementHandler.ts',
    modalPrefixes: ['admin_token_modal_'],
    hasModalPaths: true,
  },
  {
    file: 'handlers/EscortPricingHandler.ts',
    modalPrefixes: ['admin_escortprice_edit_'],
    hasModalPaths: true,
  },
  {
    file: 'handlers/EscortCatalogHandler.ts',
    modalPrefixes: ['admin_escortcatalog_create', 'admin_escortcatalog_edit_'],
    hasModalPaths: true,
  },
  {
    file: 'handlers/GameSettingsHandler.ts',
    modalPrefixes: ['admin_game_edit_'],
    hasModalPaths: true,
  },
  {
    file: 'product/AdminProductPanelHandler.ts',
    modalPrefixes: [
      'admin_product_codes_',
      'admin_product_create',
      'admin_product_fiat_',
      'admin_product_edit_',
    ],
    hasModalPaths: true,
  },
];

describe('Admin handler defer order', () => {
  for (const h of handlers) {
    describe(h.file, () => {
      const source = readFileSync(resolve(HANDLERS_DIR, h.file), 'utf-8');

      // Find the execute() method body
      const executeStart = source.indexOf('async execute(');
      const executeBodyStart = source.indexOf('{', executeStart);

      it('should have async execute method', () => {
        expect(executeStart).toBeGreaterThanOrEqual(0);
      });

      it('should not call ensureDeferred before showModal-related paths', () => {
        // Find the first occurrence of ensureDeferred in execute()
        const bodyAfterBrace = source.slice(executeBodyStart);
        const deferPos = bodyAfterBrace.indexOf('ensureDeferred');

        // For handlers with modal paths, check each modal prefix appears before ensureDeferred
        if (h.hasModalPaths) {
          for (const prefix of h.modalPrefixes) {
            const prefixPos = bodyAfterBrace.indexOf(prefix);
            expect(prefixPos).toBeGreaterThanOrEqual(0);
            expect(prefixPos).toBeLessThan(deferPos);
          }
        }
      });

      it('should return immediately after showModal paths (no fall-through to defer)', () => {
        if (h.hasModalPaths) {
          // For each modal prefix, verify there's a 'return;' between the
          // modal path check and the 'ensureDeferred' call
          const bodyAfterBrace = source.slice(executeBodyStart);
          const deferPos = bodyAfterBrace.indexOf('ensureDeferred');

          for (const prefix of h.modalPrefixes) {
            const prefixPos = bodyAfterBrace.indexOf(prefix);
            const betweenSection = bodyAfterBrace.slice(prefixPos, deferPos);
            // Each modal path should return before ensureDeferred is reached
            expect(betweenSection).toContain('return');
          }
        }
      });

      it('should call ensureDeferred for remaining non-modal paths', () => {
        const bodyAfterBrace = source.slice(executeBodyStart);
        expect(bodyAfterBrace).toContain('ensureDeferred');
      });
    });
  }
});
