import type { MemberPanelView } from '../../facades/MemberInfoFacade.js';
import type { CurrencyTransaction, GameTokenTransaction } from '@ltdjms/economy';
/**
 * User panel embed builder.
 * Matches Java UserPanelEmbedBuilder.
 */
export declare class UserPanelEmbedBuilder {
    /**
     * Builds the user panel embed data.
     */
    buildUserPanelEmbed(memberSummary: MemberPanelView): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
    };
    /**
     * Builds a currency transaction history embed.
     */
    buildCurrencyHistoryEmbed(transactions: CurrencyTransaction[], page: number, totalPages: number): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
    };
    /**
     * Builds a token transaction history embed.
     */
    buildTokenHistoryEmbed(transactions: GameTokenTransaction[], page: number, totalPages: number): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
    };
    /**
     * Builds a redemption history embed.
     */
    buildRedemptionHistoryEmbed(redemptions: {
        code: string;
        createdAt: Date;
        productName: string;
    }[], page: number, totalPages: number): {
        title: string;
        description: string;
        fields: {
            name: string;
            value: string;
            inline: boolean;
        }[];
        color: number;
    };
}
