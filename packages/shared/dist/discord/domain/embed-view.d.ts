/**
 * Immutable data structure for Embed view data.
 * Provides decoupling from discord.js MessageEmbed.
 * Matches Java EmbedView record.
 */
export interface EmbedView {
    readonly title?: string;
    readonly description?: string;
    readonly color?: number;
    readonly fields?: FieldView[];
    readonly footer?: string;
}
/**
 * Field view for an embed field.
 * Matches Java EmbedView.FieldView record.
 */
export interface FieldView {
    readonly name: string;
    readonly value: string;
    readonly inline: boolean;
}
/**
 * Immutable data structure for Button view data.
 * Matches Java ButtonView record.
 */
export interface ButtonView {
    readonly id: string;
    readonly label: string;
    readonly style: ButtonStyle;
    readonly disabled: boolean;
}
/** Button style enum matching discord.js ButtonStyle. */
export declare enum ButtonStyle {
    PRIMARY = 1,
    SECONDARY = 2,
    SUCCESS = 3,
    DANGER = 4,
    LINK = 5
}
