import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { MemberInfoFacade } from '../../../facades/MemberInfoFacade.js';
import { PanelSessionManager } from '../../../session/PanelSessionManager.js';
/**
 * Handler for redemption code interactions (user_redeem_*).
 * Supports inputting a code via modal and executing the redemption.
 */
export declare class RedemptionCodeHandler implements InteractionHandler {
    private readonly memberInfoFacade;
    private readonly sessionManager;
    readonly customIdPrefix = "user_redeem";
    constructor(memberInfoFacade: MemberInfoFacade, sessionManager: PanelSessionManager);
    execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void>;
}
