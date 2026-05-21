/**
 * Agent mode enum matching the UI options in AIAgentConfigHandler.
 * CHAT:純聊天模式，不包含 Agent 工具
 * AGENT:含工具執行的完整 Agent 模式
 * HYBRID:聊天 + Agent 混合模式
 */
export enum AgentMode {
  CHAT = 'CHAT',
  AGENT = 'AGENT',
  HYBRID = 'HYBRID',
}
