/**
 * CLI entry point for slash command registration.
 * Usage: npx tsx apps/bot/scripts/register-slash-commands.ts [--guild-id <guildId>]
 */

import { parseDotEnv, ConfigSchema } from '@ltdjms/shared';
import { SlashCommandRegistrar } from '@ltdjms/admin';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { getAllSlashCommandDefinitions } from '../src/slash-command-definitions.js';

async function main(): Promise<void> {
  const args = process.argv.slice(2);

  const guildIdIndex = args.indexOf('--guild-id');
  const guildId =
    guildIdIndex >= 0 && guildIdIndex + 1 < args.length ? args[guildIdIndex + 1] : undefined;

  const scriptDir = dirname(fileURLToPath(import.meta.url));
  const envPath = resolve(scriptDir, '..', '..', '..', '.env');
  let env: Record<string, string> = {};
  try {
    const content = readFileSync(envPath, 'utf-8');
    env = parseDotEnv(content);
  } catch {
    // .env file not found
  }

  const mergedEnv: Record<string, string | undefined> = {
    ...env,
    ...(process.env as Record<string, string | undefined>),
  };

  const cleanEnv: Record<string, string> = {};
  for (const [key, value] of Object.entries(mergedEnv)) {
    if (value !== undefined) {
      cleanEnv[key] = value;
    }
  }

  const configResult = ConfigSchema.safeParse(cleanEnv);

  if (!configResult.success) {
    console.error('Failed to load configuration:');
    for (const issue of configResult.error.issues) {
      console.error(`  - ${issue.path.join('.')}: ${issue.message}`);
    }
    process.exit(1);
  }

  const config = configResult.data;
  const token = config.DISCORD_BOT_TOKEN;
  const applicationId = cleanEnv['DISCORD_APPLICATION_ID'] ?? null;

  if (!token) {
    console.error('DISCORD_BOT_TOKEN is not set');
    process.exit(1);
  }
  if (!applicationId) {
    console.error('DISCORD_APPLICATION_ID is not set');
    process.exit(1);
  }

  const { REST } = await import('discord.js');
  const rest = new REST({ version: '10' }).setToken(token);

  const result = await SlashCommandRegistrar.registerDefinitions(
    getAllSlashCommandDefinitions(),
    applicationId,
    async (route: string, body: unknown) => {
      const response = await rest.put(route as `/${string}`, { body });
      return response;
    },
    guildId,
  );

  console.log(result.message);

  if (!result.success) {
    process.exit(1);
  }
}

main().catch((err) => {
  console.error('Unexpected error:', err);
  process.exit(1);
});
