// ============================================================
// Events
// ============================================================

export type { GameTokenChangedEvent, DiceGameConfigChangedEvent } from './events/index.js';
export { GameType } from './events/index.js';

// ============================================================
// Domain
// ============================================================

export {
  GameTokenTransactionSource,
} from './domain/types.js';

export type {
  GameTokenAccount,
  GameTokenTransaction,
  DiceGame1Config,
  DiceGame2Config,
  DiceGame1Result,
  DiceGame2Result,
  TokenAdjustmentResult,
  TransactionPage,
} from './domain/types.js';

export {
  MAX_ADJUSTMENT_AMOUNT,
  TOKEN_CACHE_TTL,
  DICE_GAME_2_DICE_PER_TOKEN,
  isValidAdjustmentAmount,
} from './domain/types.js';

// ============================================================
// Token Services
// ============================================================

export { GameTokenService } from './token/services/game-token-service.js';
export type { GameTokenTransactionService } from './token/services/game-token-tx-service.js';

// ============================================================
// Dice Services
// ============================================================

export { GameRewardService } from './dice/services/game-reward-service.js';
export { DiceGame1Service, calculateTotalReward } from './dice/services/dice-game-1-service.js';
export { DiceGame2Service } from './dice/services/dice-game-2-service.js';
export { DiceConfigService } from './dice/services/dice-config-service.js';
export { DefaultRandom } from './dice/services/random.js';
export type { Random } from './dice/services/random.js';

// Internal utilities exposed for testing
export { rollDice } from './dice/services/random.js';

// ============================================================
// Command Handlers
// ============================================================

export type {
  DiceGame1Handler,
  DiceGame2Handler,
  DiceGame1ConfigHandler,
  DiceGame2ConfigHandler,
  GameTokenAdjustHandler,
} from './commands/index.js';

// ============================================================
// Facades
// ============================================================

export { GameConfigManagementFacade } from './facades/GameConfigManagementFacade.js';
export { GameTokenManagementFacade } from './facades/GameTokenManagementFacade.js';
export type { DiceGame1ConfigUpdate, DiceGame2ConfigUpdate } from './facades/GameConfigManagementFacade.js';

// ============================================================
// DI
// ============================================================

export { GAMES_TOKENS, configureGamesContainer } from './di/games-module.js';
