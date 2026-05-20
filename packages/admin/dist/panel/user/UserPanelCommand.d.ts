import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type CommandHandler } from '../../commands/infra/CommandHandler.js';
import { MemberInfoFacade } from '../../facades/MemberInfoFacade.js';
import { PanelSessionManager } from '../../session/PanelSessionManager.js';
import { UserPanelEmbedBuilder } from './UserPanelEmbedBuilder.js';
/**
 * /user-panel slash command handler.
 * Opens the user panel showing balance, tokens, and action buttons.
 */
export declare class UserPanelCommand implements CommandHandler {
    private readonly memberInfoFacade;
    private readonly sessionManager;
    private readonly embedBuilder;
    readonly commandName = "user-panel";
    constructor(memberInfoFacade: MemberInfoFacade, sessionManager: PanelSessionManager, embedBuilder: UserPanelEmbedBuilder);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
