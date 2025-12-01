package ltdjms.discord.currency.bot;

import ltdjms.discord.currency.commands.BalanceAdjustmentCommandHandler;
import ltdjms.discord.currency.commands.BalanceCommandHandler;
import ltdjms.discord.currency.commands.CurrencyConfigCommandHandler;
import ltdjms.discord.gametoken.commands.DiceGame1CommandHandler;
import ltdjms.discord.gametoken.commands.DiceGame1ConfigCommandHandler;
import ltdjms.discord.gametoken.commands.DiceGame2CommandHandler;
import ltdjms.discord.gametoken.commands.DiceGame2ConfigCommandHandler;
import ltdjms.discord.gametoken.commands.GameTokenAdjustCommandHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener that receives slash command events and delegates to appropriate command handlers.
 * Includes metrics collection for command latency and success/error tracking.
 */
public class SlashCommandListener extends ListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(SlashCommandListener.class);

    // Command names
    public static final String CMD_BALANCE = "balance";
    public static final String CMD_CURRENCY_CONFIG = "currency-config";
    public static final String CMD_ADJUST_BALANCE = "adjust-balance";
    public static final String CMD_GAME_TOKEN_ADJUST = "game-token-adjust";
    public static final String CMD_DICE_GAME_1 = "dice-game-1";
    public static final String CMD_DICE_GAME_1_CONFIG = "dice-game-1-config";
    public static final String CMD_DICE_GAME_2 = "dice-game-2";
    public static final String CMD_DICE_GAME_2_CONFIG = "dice-game-2-config";

    private final BalanceCommandHandler balanceHandler;
    private final CurrencyConfigCommandHandler configHandler;
    private final BalanceAdjustmentCommandHandler adjustmentHandler;
    private final GameTokenAdjustCommandHandler gameTokenAdjustHandler;
    private final DiceGame1CommandHandler diceGame1Handler;
    private final DiceGame1ConfigCommandHandler diceGame1ConfigHandler;
    private final DiceGame2CommandHandler diceGame2Handler;
    private final DiceGame2ConfigCommandHandler diceGame2ConfigHandler;
    private final SlashCommandMetrics metrics;

    public SlashCommandListener(
            BalanceCommandHandler balanceHandler,
            CurrencyConfigCommandHandler configHandler,
            BalanceAdjustmentCommandHandler adjustmentHandler,
            GameTokenAdjustCommandHandler gameTokenAdjustHandler,
            DiceGame1CommandHandler diceGame1Handler,
            DiceGame1ConfigCommandHandler diceGame1ConfigHandler,
            DiceGame2CommandHandler diceGame2Handler,
            DiceGame2ConfigCommandHandler diceGame2ConfigHandler) {
        this(balanceHandler, configHandler, adjustmentHandler,
                gameTokenAdjustHandler, diceGame1Handler, diceGame1ConfigHandler,
                diceGame2Handler, diceGame2ConfigHandler,
                new SlashCommandMetrics());
    }

    public SlashCommandListener(
            BalanceCommandHandler balanceHandler,
            CurrencyConfigCommandHandler configHandler,
            BalanceAdjustmentCommandHandler adjustmentHandler,
            GameTokenAdjustCommandHandler gameTokenAdjustHandler,
            DiceGame1CommandHandler diceGame1Handler,
            DiceGame1ConfigCommandHandler diceGame1ConfigHandler,
            DiceGame2CommandHandler diceGame2Handler,
            DiceGame2ConfigCommandHandler diceGame2ConfigHandler,
            SlashCommandMetrics metrics) {
        this.balanceHandler = balanceHandler;
        this.configHandler = configHandler;
        this.adjustmentHandler = adjustmentHandler;
        this.gameTokenAdjustHandler = gameTokenAdjustHandler;
        this.diceGame1Handler = diceGame1Handler;
        this.diceGame1ConfigHandler = diceGame1ConfigHandler;
        this.diceGame2Handler = diceGame2Handler;
        this.diceGame2ConfigHandler = diceGame2ConfigHandler;
        this.metrics = metrics;
    }

    /**
     * Gets the metrics collector for this listener.
     *
     * @return the metrics instance
     */
    public SlashCommandMetrics getMetrics() {
        return metrics;
    }

    /**
     * Registers all slash commands with Discord.
     * Should be called after JDA is ready.
     *
     * @param jda the JDA instance
     */
    public void registerCommands(JDA jda) {
        LOG.info("Registering slash commands...");

        jda.updateCommands().addCommands(
                // /balance - available to all users
                Commands.slash(CMD_BALANCE, "Check your current currency balance"),

                // /currency-config - admin only
                Commands.slash(CMD_CURRENCY_CONFIG, "Configure the server's currency settings")
                        .addOption(OptionType.STRING, "name", "The name of the currency (e.g., 'Gold')", false)
                        .addOption(OptionType.STRING, "icon", "The icon/emoji for the currency (e.g., '💰')", false)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),

                // /adjust-balance - admin only
                Commands.slash(CMD_ADJUST_BALANCE, "Adjust a member's currency balance")
                        .addOptions(
                                new OptionData(OptionType.STRING, "mode", "The adjustment mode", true)
                                        .addChoice("add", "add")
                                        .addChoice("deduct", "deduct")
                                        .addChoice("adjust", "adjust"),
                                new OptionData(OptionType.USER, "member", "The member whose balance to adjust", true),
                                new OptionData(OptionType.INTEGER, "amount", "Amount to add/deduct, or target balance for adjust mode", true)
                        )
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),

                // /game-token-adjust - admin only
                Commands.slash(CMD_GAME_TOKEN_ADJUST, "Adjust a member's game token balance")
                        .addOption(OptionType.USER, "member", "The member whose tokens to adjust", true)
                        .addOption(OptionType.INTEGER, "amount", "Amount to add (positive) or subtract (negative)", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),

                // /dice-game-1 - available to all users
                Commands.slash(CMD_DICE_GAME_1, "Play the dice mini-game (costs game tokens)"),

                // /dice-game-1-config - admin only
                Commands.slash(CMD_DICE_GAME_1_CONFIG, "Configure the dice game settings")
                        .addOption(OptionType.INTEGER, "token-cost", "Number of game tokens required per play", false)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),

                // /dice-game-2 - available to all users
                Commands.slash(CMD_DICE_GAME_2, "Play the dice game 2 mini-game with straights and triples (costs game tokens)"),

                // /dice-game-2-config - admin only
                Commands.slash(CMD_DICE_GAME_2_CONFIG, "Configure the dice game 2 settings")
                        .addOption(OptionType.INTEGER, "token-cost", "Number of game tokens required per play", false)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
        ).queue(
                commands -> LOG.info("Registered {} slash commands", commands.size()),
                error -> LOG.error("Failed to register slash commands", error)
        );
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // Only process commands in guilds
        if (!event.isFromGuild()) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        String commandName = event.getName();
        LOG.debug("Received slash command: {} from user={} in guild={}",
                commandName, event.getUser().getIdLong(), event.getGuild().getIdLong());

        // Start metrics tracking
        SlashCommandMetrics.ExecutionContext metricsContext = metrics.recordStart(commandName);
        boolean succeeded = false;

        try {
            switch (commandName) {
                case CMD_BALANCE -> balanceHandler.handle(event);
                case CMD_CURRENCY_CONFIG -> handleWithAdminCheck(event, configHandler);
                case CMD_ADJUST_BALANCE -> handleWithAdminCheck(event, adjustmentHandler);
                case CMD_GAME_TOKEN_ADJUST -> handleWithAdminCheck(event, gameTokenAdjustHandler);
                case CMD_DICE_GAME_1 -> diceGame1Handler.handle(event);
                case CMD_DICE_GAME_1_CONFIG -> handleWithAdminCheck(event, diceGame1ConfigHandler);
                case CMD_DICE_GAME_2 -> diceGame2Handler.handle(event);
                case CMD_DICE_GAME_2_CONFIG -> handleWithAdminCheck(event, diceGame2ConfigHandler);
                default -> {
                    LOG.warn("Unknown command received: {}", commandName);
                    event.reply("Unknown command.").setEphemeral(true).queue();
                }
            }
            succeeded = true;
        } catch (Exception e) {
            LOG.error("Error handling command: {} for user={} in guild={}",
                    commandName, event.getUser().getIdLong(), event.getGuild().getIdLong(), e);
            BotErrorHandler.handleUnexpectedError(event, e);
        } finally {
            metrics.recordEnd(metricsContext, succeeded);
        }
    }

    private void handleWithAdminCheck(SlashCommandInteractionEvent event, CommandHandler handler) {
        // Double-check admin permissions (Discord should enforce this, but we verify)
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("You need Administrator permission to use this command.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        handler.handle(event);
    }

    /**
     * Functional interface for command handlers.
     */
    @FunctionalInterface
    public interface CommandHandler {
        void handle(SlashCommandInteractionEvent event);
    }
}
