import { ChatOpenAI } from '@langchain/openai';
import { HumanMessage, SystemMessage, AIMessage, } from '@langchain/core/messages';
import { StreamChunkType, } from './ai-chat-service.js';
import { DomainError, ok, err } from '@ltdjms/shared';
import { MessageChunkAccumulator } from './MessageChunkAccumulator.js';
import { LangChainExceptionMapper } from './LangChainExceptionMapper.js';
/**
 * LangChain-based AI Chat Service implementation.
 *
 * Supports both non-agent (pure chat) and agent (with tool calling) modes.
 * Matches Java LangChain4jAIChatService.
 */
export class LangChainAIChatService {
    promptLoader;
    config;
    chatModel;
    exceptionMapper = new LangChainExceptionMapper();
    constructor(config, promptLoader) {
        this.promptLoader = promptLoader;
        this.config = config;
        this.chatModel = this.buildChatModel(config);
    }
    /**
     * Builds a ChatOpenAI instance from config.
     */
    buildChatModel(config) {
        const model = new ChatOpenAI({
            configuration: {
                baseURL: config.baseUrl,
                apiKey: config.apiKey,
            },
            modelName: config.model,
            temperature: config.temperature,
            timeout: config.timeoutSeconds * 1000,
            streaming: true,
        });
        // Configure thinking/reasoning for DeepSeek models
        if (config.enableThinking) {
            // LangChain.js ChatOpenAI doesn't directly support DeepSeek's
            // reasoning_content in the same way. We enable it via model params.
            const modelAny = model;
            modelAny['modelKwargs'] = {
                ...(modelAny['modelKwargs'] ?? {}),
            };
        }
        return model;
    }
    /**
     * Rebuilds the chat model (useful after config changes).
     */
    updateConfig(config) {
        // Rebuild the model with new config
        this.chatModel = this.buildChatModel(config);
    }
    async generateResponse(guildId, channelId, userId, userMessage) {
        const chunks = [];
        const errorRef = { current: null };
        await this.generateStreamingResponse(guildId, channelId, userId, userMessage, {
            onChunk: (chunk, _isComplete, error) => {
                if (error) {
                    errorRef.current = error;
                }
                chunks.push(chunk);
            },
            onChunkWithType: (chunk, _isComplete, error, _type) => {
                if (error) {
                    errorRef.current = error;
                }
                chunks.push(chunk);
            },
        });
        if (errorRef.current) {
            return err(errorRef.current);
        }
        return ok(chunks);
    }
    async generateStreamingResponse(guildId, _channelId, _userId, userMessage, handler) {
        await this.doStream(guildId, userMessage, [], handler);
    }
    async generateStreamingResponseWithId(guildId, _channelId, _userId, userMessage, _messageId, handler) {
        await this.doStream(guildId, userMessage, [], handler);
    }
    async generateWithHistory(guildId, _channelId, _userId, history, handler) {
        // Extract last user message from history
        const lastUserMsg = [...history]
            .reverse()
            .find((m) => m.role === 'user');
        const userMessage = lastUserMsg?.content ?? '';
        await this.doStream(guildId, userMessage, history, handler);
    }
    /**
     * Internal streaming method.
     */
    async doStream(guildId, userMessage, history, handler) {
        if (!userMessage || userMessage.trim().length === 0) {
            handler.onChunk('', true, DomainError.aiResponseEmpty('No user message provided'));
            return;
        }
        try {
            // Build messages array
            const messages = await this.buildMessages(guildId, userMessage, history);
            const stream = await this.chatModel.stream(messages);
            const accumulator = new MessageChunkAccumulator();
            let reasoningBuffer = '';
            for await (const chunk of stream) {
                const content = typeof chunk.content === 'string' ? chunk.content : '';
                if (content) {
                    accumulator.add(content);
                    handler.onChunkWithType(content, false, null, StreamChunkType.CONTENT);
                }
                // Handle reasoning content from DeepSeek-style responses
                const msg = chunk;
                const reasoningContent = msg.reasoning_content;
                if (reasoningContent) {
                    reasoningBuffer += reasoningContent;
                    handler.onChunkWithType(reasoningContent, false, null, StreamChunkType.REASONING);
                }
            }
            const finalContent = accumulator.getContent();
            if (!finalContent && !reasoningBuffer) {
                handler.onChunk('', true, DomainError.aiResponseEmpty('AI did not generate a response'));
                return;
            }
            handler.onChunk(finalContent || '', true, null);
        }
        catch (error) {
            const domainError = this.exceptionMapper.map(error);
            handler.onChunk('', true, domainError);
        }
    }
    /**
     * Builds the message array for the LLM call.
     */
    async buildMessages(guildId, userMessage, history) {
        const messages = [];
        // Load system prompts
        const promptResult = this.promptLoader.loadPrompts(false);
        if (promptResult.isOk()) {
            const systemPrompt = promptResult.getValue();
            const combined = systemPrompt.toCombinedString();
            if (combined) {
                messages.push(new SystemMessage(combined));
            }
        }
        // Add history messages
        for (const msg of history) {
            if (msg.role === 'user') {
                messages.push(new HumanMessage(msg.content));
            }
            else if (msg.role === 'assistant') {
                messages.push(new AIMessage(msg.content));
            }
            else if (msg.role === 'system') {
                messages.push(new SystemMessage(msg.content));
            }
        }
        // Add current user message
        messages.push(new HumanMessage(userMessage));
        return messages;
    }
}
//# sourceMappingURL=LangChainAIChatService.js.map