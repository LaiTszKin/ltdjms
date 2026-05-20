// ===== Chunk Types =====
/**
 * Type of streaming chunk.
 */
export var StreamChunkType;
(function (StreamChunkType) {
    StreamChunkType["REASONING"] = "REASONING";
    StreamChunkType["CONTENT"] = "CONTENT";
    StreamChunkType["TOOL_INTENT"] = "TOOL_INTENT";
})(StreamChunkType || (StreamChunkType = {}));
// ===== Routing Types =====
export var Route;
(function (Route) {
    Route["AGENT_ROUTE"] = "AGENT_ROUTE";
    Route["AI_CHAT_ROUTE"] = "AI_CHAT_ROUTE";
    Route["DENY"] = "DENY";
})(Route || (Route = {}));
export var Source;
(function (Source) {
    Source["AGENT_CONFIG"] = "AGENT_CONFIG";
    Source["CHANNEL_ALLOWLIST"] = "CHANNEL_ALLOWLIST";
    Source["CATEGORY_ALLOWLIST"] = "CATEGORY_ALLOWLIST";
    Source["AGENT_CONFIG_UNAVAILABLE"] = "AGENT_CONFIG_UNAVAILABLE";
    Source["NO_ALLOWLIST"] = "NO_ALLOWLIST";
})(Source || (Source = {}));
// ===== Message Splitter Types =====
export const MAX_MESSAGE_LENGTH = 1980;
export var RedactionMode;
(function (RedactionMode) {
    RedactionMode["NONE"] = "NONE";
    RedactionMode["REDACTED"] = "REDACTED";
    RedactionMode["OMITTED"] = "OMITTED";
})(RedactionMode || (RedactionMode = {}));
export var ConversationIdStrategy;
(function (ConversationIdStrategy) {
    ConversationIdStrategy["THREAD_LEVEL"] = "THREAD_LEVEL";
    ConversationIdStrategy["MESSAGE_LEVEL"] = "MESSAGE_LEVEL";
})(ConversationIdStrategy || (ConversationIdStrategy = {}));
//# sourceMappingURL=ai-chat-service.js.map