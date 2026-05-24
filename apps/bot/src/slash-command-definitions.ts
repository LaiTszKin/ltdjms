import {
  SlashCommandRegistrar,
  type SlashCommandDefinition,
} from '@ltdjms/admin';
import { UserPanelSlashCommand, RedeemCodeSlashCommand } from '@ltdjms/user-panel';

/**
 * Composes slash command definitions from all packages without coupling admin to user-panel.
 */
export function getAllSlashCommandDefinitions(): SlashCommandDefinition[] {
  return [
    ...SlashCommandRegistrar.getCoreDefinitions(),
    UserPanelSlashCommand,
    RedeemCodeSlashCommand,
  ];
}
