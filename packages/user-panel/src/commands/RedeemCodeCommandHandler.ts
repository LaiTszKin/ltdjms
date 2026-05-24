import { type DiscordInteraction, type DiscordContext, type CommandHandler } from '@ltdjms/shared';
import { UserPanelButtonHandler } from '../handlers/UserPanelButtonHandler.js';

/**
 * Handler for the /redeem-code slash command.
 * Shows a modal for code input; modal submit is handled by UserPanelButtonHandler.
 */
export class RedeemCodeCommandHandler implements CommandHandler {
  readonly commandName = 'redeem-code';

  async execute(interaction: DiscordInteraction, _context: DiscordContext): Promise<void> {
    await interaction.showModal(UserPanelButtonHandler.buildRedeemModal());
  }
}
