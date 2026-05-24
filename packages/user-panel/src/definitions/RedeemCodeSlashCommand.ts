/**
 * Slash command definition for /redeem-code.
 * Used by slash command registration.
 */
export const RedeemCodeSlashCommand = {
  name: 'redeem-code',
  description: '輸入兌換碼',
  defaultMemberPermissions: null,
  nameLocalizations: { 'zh-TW': '兌換碼' },
  descriptionLocalizations: { 'zh-TW': '輸入兌換碼來兌換商品' },
} as const;
