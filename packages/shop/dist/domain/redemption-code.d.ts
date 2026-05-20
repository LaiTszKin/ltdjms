import { z } from 'zod';
/** Length of generated redemption codes. */
export declare const CODE_LENGTH = 16;
/** Characters for generating codes. Excludes confusing chars: 0/O, 1/I/L */
export declare const CODE_CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
/** Zod schema for RedemptionCode. */
export declare const RedemptionCodeSchema: z.ZodEffects<z.ZodObject<{
    id: z.ZodNullable<z.ZodNumber>;
    code: z.ZodString;
    productId: z.ZodNullable<z.ZodNumber>;
    guildId: z.ZodNumber;
    expiresAt: z.ZodNullable<z.ZodDate>;
    redeemedBy: z.ZodNullable<z.ZodNumber>;
    redeemedAt: z.ZodNullable<z.ZodDate>;
    createdAt: z.ZodDate;
    invalidatedAt: z.ZodNullable<z.ZodDate>;
    quantity: z.ZodNumber;
}, "strip", z.ZodTypeAny, {
    id: number | null;
    guildId: number;
    productId: number | null;
    code: string;
    createdAt: Date;
    expiresAt: Date | null;
    redeemedBy: number | null;
    redeemedAt: Date | null;
    invalidatedAt: Date | null;
    quantity: number;
}, {
    id: number | null;
    guildId: number;
    productId: number | null;
    code: string;
    createdAt: Date;
    expiresAt: Date | null;
    redeemedBy: number | null;
    redeemedAt: Date | null;
    invalidatedAt: Date | null;
    quantity: number;
}>, {
    id: number | null;
    guildId: number;
    productId: number | null;
    code: string;
    createdAt: Date;
    expiresAt: Date | null;
    redeemedBy: number | null;
    redeemedAt: Date | null;
    invalidatedAt: Date | null;
    quantity: number;
}, {
    id: number | null;
    guildId: number;
    productId: number | null;
    code: string;
    createdAt: Date;
    expiresAt: Date | null;
    redeemedBy: number | null;
    redeemedAt: Date | null;
    invalidatedAt: Date | null;
    quantity: number;
}>;
export type RedemptionCode = z.infer<typeof RedemptionCodeSchema>;
export declare function createRedemptionCode(code: string, productId: number, guildId: number, expiresAt: Date | null, quantity?: number): RedemptionCode;
export declare function withRedeemed(code: RedemptionCode, userId: number): RedemptionCode;
export declare function isRedeemed(code: RedemptionCode): boolean;
export declare function isExpired(code: RedemptionCode): boolean;
export declare function isValid(code: RedemptionCode): boolean;
export declare function isInvalidated(code: RedemptionCode): boolean;
export declare function withInvalidated(code: RedemptionCode): RedemptionCode;
export declare function belongsToGuild(code: RedemptionCode, guildId: number): boolean;
export declare function getMaskedCode(code: RedemptionCode): string;
