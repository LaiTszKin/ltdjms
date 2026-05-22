import { z } from 'zod';
import { ok, err, DomainError, type Result, safeSnowflakeToNumber } from '@ltdjms/shared';

/** Length of generated redemption codes. */
export const CODE_LENGTH = 16;

/** Characters for generating codes. Excludes confusing chars: 0/O, 1/I/L */
export const CODE_CHARACTERS = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

/** Zod schema for RedemptionCode. */
export const RedemptionCodeSchema = z
  .object({
    id: z.number().int().positive().nullable(),
    code: z.string().min(1, 'code must not be blank').max(32, 'code must not exceed 32 characters'),
    productId: z.number().int().nullable(),
    guildId: z.number(),
    expiresAt: z.date().nullable(),
    redeemedBy: z.number().int().nullable(),
    redeemedAt: z.date().nullable(),
    createdAt: z.date(),
    invalidatedAt: z.date().nullable(),
    quantity: z.number().int().min(1, 'quantity must be positive').max(1000, 'quantity must not exceed 1000'),
  })
  .refine(
    (data) => (data.redeemedBy === null) === (data.redeemedAt === null),
    {
      message: 'redeemedBy and redeemedAt must both be specified or both be null',
    },
  );

export type RedemptionCode = z.infer<typeof RedemptionCodeSchema>;

export function createRedemptionCode(
  code: string,
  productId: number,
  guildId: number,
  expiresAt: Date | null,
  quantity = 1,
): RedemptionCode {
  const validated = RedemptionCodeSchema.parse({
    id: null,
    code: code.toUpperCase(),
    productId,
    guildId,
    expiresAt,
    redeemedBy: null,
    redeemedAt: null,
    createdAt: new Date(),
    invalidatedAt: null,
    quantity,
  });
  return validated;
}

export function withRedeemed(code: RedemptionCode, userId: string): Result<RedemptionCode, DomainError> {
  if (isRedeemed(code)) {
    return err(DomainError.invalidInput('Code has already been redeemed'));
  }
  return ok(RedemptionCodeSchema.parse({
    ...code,
    redeemedBy: safeSnowflakeToNumber(userId),
    redeemedAt: new Date(),
  }));
}

export function isRedeemed(code: RedemptionCode): boolean {
  return code.redeemedBy !== null;
}

export function isExpired(code: RedemptionCode): boolean {
  return code.expiresAt !== null && new Date() > code.expiresAt;
}

export function isValid(code: RedemptionCode): boolean {
  return !isInvalidated(code) && !isRedeemed(code) && !isExpired(code);
}

export function isInvalidated(code: RedemptionCode): boolean {
  return code.invalidatedAt !== null;
}

export function withInvalidated(code: RedemptionCode): Result<RedemptionCode, DomainError> {
  if (isInvalidated(code)) {
    return err(DomainError.invalidInput('Code has already been invalidated'));
  }
  return ok(RedemptionCodeSchema.parse({
    ...code,
    productId: null,
    invalidatedAt: new Date(),
  }));
}

export function belongsToGuild(code: RedemptionCode, guildId: number): boolean {
  return code.guildId === guildId;
}

export function getMaskedCode(code: RedemptionCode): string {
  if (code.code.length <= 8) return code.code;
  return code.code.substring(0, 4) + '****' + code.code.substring(code.code.length - 4);
}
