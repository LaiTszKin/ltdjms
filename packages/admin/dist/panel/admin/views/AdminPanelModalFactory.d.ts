import type { DiceGame1Config, DiceGame2Config } from '@ltdjms/economy';
import type { EscortOptionCatalogEntry } from '@ltdjms/dispatch';
/**
 * Builder for admin panel modal configurations.
 * Provides structured data that Discord modal builders consume.
 * Matches Java AdminPanelModalFactory.
 *
 * RESPONSIBILITY: This factory handles shared/generic admin panel modals
 * (balance, tokens, games, escort pricing/catalog).
 * Product-specific modals (create/edit product, generate codes) are in
 * AdminProductPanelModalFactory under panel/admin/product/.
 *
 * @see AdminProductPanelModalFactory — product-specific modals
 */
export declare class AdminPanelModalFactory {
    /**
     * Builds a balance adjustment modal.
     */
    buildBalanceAdjustModal(mode: 'add' | 'deduct' | 'set'): {
        title: string;
        fields: {
            label: string;
            placeholder: string;
            value?: string;
            minLength: number;
            maxLength: number;
            required: boolean;
        }[];
    };
    /**
     * Builds a token adjustment modal.
     */
    buildTokenAdjustModal(mode: 'add' | 'deduct' | 'set'): {
        title: string;
        fields: {
            label: string;
            placeholder: string;
            minLength: number;
            maxLength: number;
            required: boolean;
        }[];
    };
    /**
     * Builds a dice game 1 settings modal.
     */
    buildDiceGame1SettingsModal(currentConfig: DiceGame1Config): {
        title: string;
        fields: {
            label: string;
            value: string;
            minLength: number;
            maxLength: number;
            required: boolean;
        }[];
    };
    /**
     * Builds a dice game 2 settings modal with all multipliers.
     */
    buildDiceGame2SettingsModal(currentConfig: DiceGame2Config): {
        title: string;
        fields: {
            label: string;
            value: string;
            minLength: number;
            maxLength: number;
            required: boolean;
        }[];
    };
    /**
     * Builds a product creation modal.
     */
    buildProductCreateModal(): {
        title: string;
        fields: {
            label: string;
            placeholder: string;
            minLength: number;
            maxLength: number;
            required: boolean;
        }[];
    };
    /**
     * Builds a generate codes modal.
     */
    buildGenerateCodesModal(): {
        title: string;
        fields: {
            label: string;
            placeholder: string;
            minLength: number;
            maxLength: number;
            required: boolean;
        }[];
    };
    /**
     * Builds an escort pricing edit modal.
     */
    buildEscortPricingEditModal(optionName: string, _globalPrice: number, currentOverride: number | null): {
        title: string;
        fields: {
            label: string;
            value: string;
            placeholder: string;
            minLength: number;
            maxLength: number;
            required: boolean;
        }[];
    };
    /**
     * Builds an escort catalog create/edit modal.
     */
    buildEscortCatalogModal(currentEntry?: Partial<EscortOptionCatalogEntry> | null): {
        title: string;
        fields: {
            label: string;
            value?: string;
            placeholder: string;
            minLength: number;
            maxLength: number;
            required: boolean;
        }[];
    };
}
