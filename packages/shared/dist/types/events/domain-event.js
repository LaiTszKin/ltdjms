/**
 * Base interface for all domain events.
 * All events must have a guildId to identify the server context.
 * Matches the Java DomainEvent sealed interface pattern.
 */
// ---- Dice Game Events ----
export var GameType;
(function (GameType) {
    GameType["DICE_GAME_1"] = "DICE_GAME_1";
    GameType["DICE_GAME_2"] = "DICE_GAME_2";
})(GameType || (GameType = {}));
// ---- Product / Redemption Events ----
export var OperationType;
(function (OperationType) {
    OperationType["CREATED"] = "CREATED";
    OperationType["UPDATED"] = "UPDATED";
    OperationType["DELETED"] = "DELETED";
})(OperationType || (OperationType = {}));
//# sourceMappingURL=domain-event.js.map