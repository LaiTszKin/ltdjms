import type { GuildCurrencyConfig, DiceGame1Config, DiceGame2Config, BalanceView } from '@ltdjms/economy';
import type { Product } from '@ltdjms/shop';
import type { AllowedChannel, AllowedCategory } from '@ltdjms/ai';
import type { EscortOptionCatalogEntry } from '@ltdjms/dispatch';
/**
 * Generic embed view builder for admin panels.
 * Provides structured data that Discord embed builders consume.
 * Matches Java AdminPanelViewFactory.
 *
 * RESPONSIBILITY: This factory handles shared/generic admin panel views
 * (main menu, balance, tokens, games, AI config, dispatch, escort pricing/catalog).
 * Product-specific views (product list, detail, codes) are in AdminProductPanelViewFactory
 * under panel/admin/product/.
 *
 * @see AdminProductPanelViewFactory — product-specific views
 */
export declare class AdminPanelViewFactory {
    /**
     * Builds the main admin panel embed data.
     */
    buildMainPanelEmbed(guildName: string, currencyConfig: GuildCurrencyConfig | null, dispatchCount: number): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        footer: string;
        color: number;
        buttons: {
            id: string;
            label: string;
            style: number;
            disabled: boolean;
        }[];
    };
    /**
     * Builds the balance view embed data.
     */
    buildBalanceView(balanceInfo: BalanceView): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
        buttons: {
            id: string;
            label: string;
            style: number;
            disabled: boolean;
        }[];
    };
    /**
     * Builds the token view embed data.
     */
    buildTokenView(tokenInfo: {
        tokens: number;
    }): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
        buttons: {
            id: string;
            label: string;
            style: number;
            disabled: boolean;
        }[];
    };
    /**
     * Builds the dice game 1 settings view.
     */
    buildDiceGame1SettingsView(config: DiceGame1Config): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
        buttons: {
            id: string;
            label: string;
            style: number;
            disabled: boolean;
        }[];
    };
    /**
     * Builds the dice game 2 settings view.
     */
    buildDiceGame2SettingsView(config: DiceGame2Config): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
        buttons: {
            id: string;
            label: string;
            style: number;
            disabled: boolean;
        }[];
    };
    /**
     * Builds the product list embed.
     */
    buildProductListView(products: Product[], page: number, totalPages: number): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
        buttons: {
            id: string;
            label: string;
            style: number;
            disabled: boolean;
        }[];
    };
    /**
     * Builds the AI channel config embed.
     */
    buildAIChannelConfigView(channels: AllowedChannel[], categories: AllowedCategory[]): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
        buttons: {
            id: string;
            label: string;
            style: number;
            disabled: boolean;
        }[];
    };
    /**
     * Builds the AI agent config embed.
     */
    buildAIAgentConfigView(configs: {
        channelId: string;
    }[]): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
        buttons: {
            id: string;
            label: string;
            style: number;
            disabled: boolean;
        }[];
    };
    /**
     * Builds the dispatch after-sales staff embed.
     */
    buildDispatchAfterSalesView(staffs: {
        userId: string;
    }[]): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
        buttons: {
            id: string;
            label: string;
            style: number;
            disabled: boolean;
        }[];
    };
    /**
     * Builds the escort pricing embed.
     */
    buildEscortPricingView(globalCatalog: EscortOptionCatalogEntry[], guildOverrides: Map<string, number>): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
        buttons: {
            id: string;
            label: string;
            style: number;
            disabled: boolean;
        }[];
    };
    /**
     * Builds the escort catalog embed.
     *
     * TODO(P1-35): Verify EscortOptionCatalogEntry field mappings against the
     * finalized type once EscortOptionCatalogRepository is available from
     * @ltdjms/dispatch. The current EscortOptionCatalogEntry (defined in the
     * dispatch package) uses: code, type, level, mapScope, target, priceTwd.
     * The template substitutes: {name}=type-target, {category}=level,
     * {price}=priceTwd, {description}=mapScope. Confirm these match the
     * production catalog schema.
     */
    buildEscortCatalogView(catalogEntries: EscortOptionCatalogEntry[]): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
        buttons: {
            id: string;
            label: string;
            style: number;
            disabled: boolean;
        }[];
    };
}
