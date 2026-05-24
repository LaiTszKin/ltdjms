/**
 * @deprecated Use `npx tsx apps/bot/scripts/register-slash-commands.ts` instead.
 * Slash registration is composed at the app layer to keep admin independent of user-panel.
 */
console.error(
  'Deprecated entry point. Run: npx tsx apps/bot/scripts/register-slash-commands.ts [--guild-id <guildId>]',
);
process.exit(1);
