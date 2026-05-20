/**
 * Barrel file for economy module slash command handlers.
 *
 * Each handler follows the CommandHandler shape:
 * - `commandName` — the slash command name for routing
 * - `execute(interaction, context)` — handles the command invocation
 *
 * Handlers are not registered with SlashCommandListener here;
 * registration is done by the admin module's DI configuration.
 */

export { BalanceHandler } from './balance-handler.js';
export { CurrencyConfigHandler } from './currency-config-handler.js';
export { DiceGame1Handler } from './dice-game-1-handler.js';
export { DiceGame2Handler } from './dice-game-2-handler.js';
export { DiceGame1ConfigHandler, DiceGame2ConfigHandler } from './dice-config-handlers.js';
export { GameTokenAdjustHandler } from './game-token-adjust-handler.js';
