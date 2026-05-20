import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type CommandHandler } from '../../commands/infra/CommandHandler.js';
import { MemberInfoFacade } from '../../facades/MemberInfoFacade.js';
import { PanelSessionManager } from '../../session/PanelSessionManager.js';
/**
 * /user-panel slash command handler.
 * Opens the user panel showing balance, tokens, and action buttons.
 */
export declare class UserPanelCommand implements CommandHandler {
    private readonly memberInfoFacade;
    private readonly sessionManager;
    readonly commandName = "user-panel";
    constructor(memberInfoFacade: MemberInfoFacade, sessionManager: PanelSessionManager);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
