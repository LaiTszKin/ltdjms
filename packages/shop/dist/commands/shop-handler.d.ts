import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { ShopService } from '../services/shop.service.js';
import { FiatOrderService } from '../services/fiat-order.service.js';
import { CurrencyPurchaseService } from '../services/currency-purchase.service.js';
/**
 * Handler for the /shop slash command and its associated component interactions.
 *
 * Flow:
 *   /shop -> fetches first page -> shows embed + action row (prev/buy/search/next)
 *   Button clicks -> paginate, trigger buy flow, or open search modal
 *   Search modal submit -> shows search results with separate pagination
 *   Buy flow -> product selection -> payment method choice -> confirmation
 */
export declare class ShopCommandHandler {
    private readonly shopService;
    private readonly fiatOrderService?;
    private readonly currencyPurchaseService?;
    readonly commandName = "shop";
    constructor(shopService: ShopService, fiatOrderService?: FiatOrderService | undefined, currencyPurchaseService?: CurrencyPurchaseService | undefined);
    /**
     * Handles the initial /shop slash command.
     * Fetches the first page of products and replies with an embed.
     */
    execute(interaction: DiscordInteraction, _context: DiscordContext): Promise<void>;
    /**
     * Handles component interactions (button clicks, select menus, modal submits)
     * for the shop panel. The customId determines which action to take.
     */
    handleInteraction(interaction: DiscordInteraction, _context: DiscordContext, customId: string): Promise<void>;
    /**
     * Shows a specific page of products in the shop embed with pagination buttons.
     */
    private showPage;
    /**
     * Shows the buy product selection interface.
     */
    private showBuySelection;
    /**
     * Shows the currency purchase confirmation.
     * Wires up CurrencyPurchaseService.purchaseProduct when available (P2-15).
     */
    private showCurrencyPurchaseConfirm;
    /**
     * Parses the guild ID string to a number. Returns null on invalid input.
     */
    private parseGuildId;
    /**
     * Checks whether a shop page is empty.
     */
    private static pageIsEmpty;
    /**
     * Edits the interaction reply with an embed and action row components.
     * Uses the raw hook because the abstract DiscordInteraction.editEmbed only accepts an embed.
     */
    private editWithComponents;
    /**
     * Parses a search pagination customId to extract the keyword and page number.
     * Format: <prefix><base64keyword>_<pageNum>
     */
    private parseSearchCustomId;
}
