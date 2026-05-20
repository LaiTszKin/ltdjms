import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type EscortDispatchOrderService } from '../service/index.js';
/**
 * `/dispatch-panel` slash command handler.
 * Opens the escort dispatch management panel for the invoking user.
 */
export declare class DispatchPanelCommandHandler {
    private readonly dispatchOrderService;
    readonly commandName = "dispatch-panel";
    constructor(dispatchOrderService: EscortDispatchOrderService);
    execute(interaction: DiscordInteraction, _context: DiscordContext): Promise<void>;
    private formatPanelText;
}
