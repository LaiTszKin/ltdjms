/**
 * CLI entry point for slash command registration.
 * Usage: npx tsx packages/admin/src/commands/registration/register.ts [--guild-id <guildId>]
 *
 * Loads environment variables and registers all slash commands
 * with the Discord API.
 */
import { parseDotEnv, ConfigSchema } from '@ltdjms/shared';
import { SlashCommandRegistrar } from './SlashCommandRegistrar.js';
import { readFileSync } from 'node:fs';
async function main() {
    // Parse CLI args
    const args = process.argv.slice(2);
    const guildIdIndex = args.indexOf('--guild-id');
    const guildId = guildIdIndex >= 0 && guildIdIndex + 1 < args.length
        ? args[guildIdIndex + 1]
        : undefined;
    // Load env from .env file if it exists
    let env = {};
    try {
        const content = readFileSync('.env', 'utf-8');
        env = parseDotEnv(content);
    }
    catch {
        // .env file not found
    }
    // Merge with process.env (process.env takes precedence)
    const mergedEnv = {
        ...env,
        ...process.env,
    };
    // Remove undefined values for zod
    const cleanEnv = {};
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
    // Create REST client from discord.js
    const { REST } = await import('discord.js');
    const rest = new REST({ version: '10' }).setToken(token);
    // Register commands
    const result = await SlashCommandRegistrar.registerAll(applicationId, async (route, body) => {
        const response = await rest.put(route, { body });
        return response;
    }, guildId);
    console.log(result.message);
    if (!result.success) {
        process.exit(1);
    }
}
main().catch((err) => {
    console.error('Unexpected error:', err);
    process.exit(1);
});
//# sourceMappingURL=register.js.map