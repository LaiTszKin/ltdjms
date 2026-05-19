/**
 * View object representing a single escort option's price display in a guild.
 */
export interface EscortOrderOption {
    readonly code: string;
    readonly type: string;
    readonly level: string;
    readonly mapScope: string;
    readonly target: string;
    readonly defaultPriceTwd: number;
}
export interface OptionPriceView {
    readonly optionCode: string;
    readonly option: EscortOrderOption;
    readonly defaultPriceTwd: number;
    readonly effectivePriceTwd: number;
    readonly overridden: boolean;
}
/** Formatter for OptionPriceView display in Discord. */
export declare function optionPriceToDisplayLine(view: OptionPriceView): string;
