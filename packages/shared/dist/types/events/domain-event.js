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
export var ProductOperationType;
(function (ProductOperationType) {
    ProductOperationType["CREATED"] = "CREATED";
    ProductOperationType["UPDATED"] = "UPDATED";
    ProductOperationType["DELETED"] = "DELETED";
})(ProductOperationType || (ProductOperationType = {}));
//# sourceMappingURL=domain-event.js.map