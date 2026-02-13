package ltdjms.discord.currency.bot;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.commands.CurrencyConfigCommandHandler;
import ltdjms.discord.dispatch.commands.DispatchPanelCommandHandler;
import ltdjms.discord.gametoken.commands.DiceGame1CommandHandler;
import ltdjms.discord.gametoken.commands.DiceGame2CommandHandler;
import ltdjms.discord.panel.commands.AdminPanelCommandHandler;
import ltdjms.discord.panel.commands.UserPanelCommandHandler;
import ltdjms.discord.shared.localization.CommandLocalizations;
import ltdjms.discord.shop.commands.ShopCommandHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * Listener that receives slash command events and delegates to appropriate command handlers.
 * Includes metrics collection for command latency and success/error tracking.
 */
public class SlashCommandListener extends ListenerAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(SlashCommandListener.class);

  // Command names - only canonical commands retained
  public static final String CMD_CURRENCY_CONFIG = "currency-config";
  public static final String CMD_DICE_GAME_1 = "dice-game-1";
  public static final String CMD_DICE_GAME_2 = "dice-game-2";
  public static final String CMD_USER_PANEL = "user-panel";
  public static final String CMD_ADMIN_PANEL = "admin-panel";
  public static final String CMD_SHOP = "shop";
  public static final String CMD_DISPATCH_PANEL = "dispatch-panel";

  private final CurrencyConfigCommandHandler configHandler;
  private final DiceGame1CommandHandler diceGame1Handler;
  private final DiceGame2CommandHandler diceGame2Handler;
  private final UserPanelCommandHandler userPanelHandler;
  private final AdminPanelCommandHandler adminPanelHandler;
  private final ShopCommandHandler shopHandler;
  private final DispatchPanelCommandHandler dispatchPanelHandler;
  private final SlashCommandMetrics metrics;

  public SlashCommandListener(
      CurrencyConfigCommandHandler configHandler,
      DiceGame1CommandHandler diceGame1Handler,
      DiceGame2CommandHandler diceGame2Handler,
      UserPanelCommandHandler userPanelHandler,
      AdminPanelCommandHandler adminPanelHandler,
      ShopCommandHandler shopHandler,
      DispatchPanelCommandHandler dispatchPanelHandler) {
    this(
        configHandler,
        diceGame1Handler,
        diceGame2Handler,
        userPanelHandler,
        adminPanelHandler,
        shopHandler,
        dispatchPanelHandler,
        new SlashCommandMetrics());
  }

  public SlashCommandListener(
      CurrencyConfigCommandHandler configHandler,
      DiceGame1CommandHandler diceGame1Handler,
      DiceGame2CommandHandler diceGame2Handler,
      UserPanelCommandHandler userPanelHandler,
      AdminPanelCommandHandler adminPanelHandler,
      ShopCommandHandler shopHandler,
      DispatchPanelCommandHandler dispatchPanelHandler,
      SlashCommandMetrics metrics) {
    this.configHandler = configHandler;
    this.diceGame1Handler = diceGame1Handler;
    this.diceGame2Handler = diceGame2Handler;
    this.userPanelHandler = userPanelHandler;
    this.adminPanelHandler = adminPanelHandler;
    this.shopHandler = shopHandler;
    this.dispatchPanelHandler = dispatchPanelHandler;
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
   * Builds the complete list of slash command definitions with zh-TW localization. This method can
   * be used for both global and guild-specific command registration.
   *
   * <p>Note: Legacy commands (/balance, /adjust-balance, /game-token-adjust, /dice-game-1-config,
   * /dice-game-2-config) have been removed. Their functionality is now available through
   * /user-panel and /admin-panel respectively.
   *
   * @return the list of slash command definitions
   */
  public List<SlashCommandData> buildAllCommandDefinitions() {
    return List.of(
        // /currency-config - admin only
        Commands.slash(CMD_CURRENCY_CONFIG, "Configure the server's currency settings")
            .setNameLocalizations(CommandLocalizations.getNameLocalizations(CMD_CURRENCY_CONFIG))
            .setDescriptionLocalizations(
                CommandLocalizations.getDescriptionLocalizations(CMD_CURRENCY_CONFIG))
            .addOptions(
                new OptionData(
                        OptionType.STRING, "name", "The name of the currency (e.g., 'Gold')", false)
                    .setNameLocalizations(CommandLocalizations.getOptionNameLocalizations("name"))
                    .setDescriptionLocalizations(
                        CommandLocalizations.getOptionDescriptionLocalizations("name")),
                new OptionData(
                        OptionType.STRING,
                        "icon",
                        "The icon/emoji for the currency (e.g., '💰')",
                        false)
                    .setNameLocalizations(CommandLocalizations.getOptionNameLocalizations("icon"))
                    .setDescriptionLocalizations(
                        CommandLocalizations.getOptionDescriptionLocalizations("icon")))
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),

        // /dice-game-1 - available to all users
        Commands.slash(CMD_DICE_GAME_1, "Play the dice mini-game (costs game tokens)")
            .setNameLocalizations(CommandLocalizations.getNameLocalizations(CMD_DICE_GAME_1))
            .setDescriptionLocalizations(
                CommandLocalizations.getDescriptionLocalizations(CMD_DICE_GAME_1))
            .addOptions(
                new OptionData(
                        OptionType.INTEGER,
                        "tokens",
                        "Number of tokens to wager for this play (optional)",
                        false)
                    .setNameLocalizations(CommandLocalizations.getOptionNameLocalizations("tokens"))
                    .setDescriptionLocalizations(
                        CommandLocalizations.getOptionDescriptionLocalizations("tokens"))),

        // /dice-game-2 - available to all users
        Commands.slash(
                CMD_DICE_GAME_2,
                "Play the dice game 2 mini-game with straights and triples (costs game tokens)")
            .setNameLocalizations(CommandLocalizations.getNameLocalizations(CMD_DICE_GAME_2))
            .setDescriptionLocalizations(
                CommandLocalizations.getDescriptionLocalizations(CMD_DICE_GAME_2))
            .addOptions(
                new OptionData(
                        OptionType.INTEGER,
                        "tokens",
                        "Number of tokens to wager for this play (optional)",
                        false)
                    .setNameLocalizations(CommandLocalizations.getOptionNameLocalizations("tokens"))
                    .setDescriptionLocalizations(
                        CommandLocalizations.getOptionDescriptionLocalizations("tokens"))),

        // /user-panel - available to all users
        Commands.slash(
                CMD_USER_PANEL, "View your currency balance, game tokens, and transaction history")
            .setNameLocalizations(CommandLocalizations.getNameLocalizations(CMD_USER_PANEL))
            .setDescriptionLocalizations(
                CommandLocalizations.getDescriptionLocalizations(CMD_USER_PANEL)),

        // /admin-panel - admin only
        Commands.slash(CMD_ADMIN_PANEL, "Manage member balances, game tokens, and game settings")
            .setNameLocalizations(CommandLocalizations.getNameLocalizations(CMD_ADMIN_PANEL))
            .setDescriptionLocalizations(
                CommandLocalizations.getDescriptionLocalizations(CMD_ADMIN_PANEL))
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),

        // /shop - available to all users
        Commands.slash(CMD_SHOP, "Browse available products in the shop")
            .setNameLocalizations(CommandLocalizations.getNameLocalizations(CMD_SHOP))
            .setDescriptionLocalizations(
                CommandLocalizations.getDescriptionLocalizations(CMD_SHOP)),

        // /dispatch-panel - admin only
        Commands.slash(CMD_DISPATCH_PANEL, "Assign escort orders through interactive panel")
            .setNameLocalizations(CommandLocalizations.getNameLocalizations(CMD_DISPATCH_PANEL))
            .setDescriptionLocalizations(
                CommandLocalizations.getDescriptionLocalizations(CMD_DISPATCH_PANEL))
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)));
  }

  /**
   * Registers all slash commands for all guilds the bot is currently in. Should be called after JDA
   * is ready. Uses guild-specific command registration for faster propagation. Includes zh-TW
   * localization for command names and descriptions.
   *
   * @param jda the JDA instance
   */
  public void registerCommands(JDA jda) {
    // Clear any existing global application commands to avoid duplicate entries
    // when we manage commands on a per-guild basis.
    jda.updateCommands().queue();

    var guilds = jda.getGuilds();
    var commandDefinitions = buildAllCommandDefinitions();
    int commandCount = commandDefinitions.size();

    LOG.info(
        "Syncing {} slash commands for {} guilds with zh-TW localization...",
        commandCount,
        guilds.size());

    int successCount = 0;
    int failCount = 0;

    for (var guild : guilds) {
      try {
        syncCommandsForGuild(guild, commandDefinitions);
        successCount++;
      } catch (Exception e) {
        failCount++;
        LOG.warn(
            "Failed to submit command sync for guild={}: {}", guild.getIdLong(), e.getMessage());
      }
    }

    LOG.info(
        "Submitted command sync for {} guilds ({} commands each). Success={}, Failed={}",
        guilds.size(),
        commandCount,
        successCount,
        failCount);
  }

  /**
   * Synchronizes slash commands for a single guild. This method can be used both during startup and
   * when joining a new guild.
   *
   * @param guild the guild to sync commands for
   */
  public void syncCommandsForGuild(net.dv8tion.jda.api.entities.Guild guild) {
    syncCommandsForGuild(guild, buildAllCommandDefinitions());
  }

  /**
   * Synchronizes slash commands for a single guild with pre-built command definitions.
   *
   * @param guild the guild to sync commands for
   * @param commandDefinitions the command definitions to register
   */
  private void syncCommandsForGuild(
      net.dv8tion.jda.api.entities.Guild guild, List<SlashCommandData> commandDefinitions) {
    guild
        .updateCommands()
        .addCommands(commandDefinitions)
        .queue(
            commands ->
                LOG.debug("Synced {} commands for guild={}", commands.size(), guild.getIdLong()),
            error ->
                LOG.warn(
                    "Failed to sync commands for guild={}: {}",
                    guild.getIdLong(),
                    error.getMessage()));
  }

  /**
   * Handles the event when the bot joins a new guild. Automatically syncs all slash commands to the
   * new guild.
   *
   * @param event the guild join event
   */
  @Override
  public void onGuildJoin(GuildJoinEvent event) {
    var guild = event.getGuild();
    LOG.info(
        "Bot joined new guild={} ({}). Syncing slash commands...",
        guild.getIdLong(),
        guild.getName());

    try {
      syncCommandsForGuild(guild);
      LOG.info("Successfully submitted command sync for new guild={}", guild.getIdLong());
    } catch (Exception e) {
      LOG.error(
          "Failed to sync commands for new guild={}: {}", guild.getIdLong(), e.getMessage(), e);
    }
  }

  @Override
  public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
    // Only process commands in guilds
    if (!event.isFromGuild()) {
      event.reply("This command can only be used in a server.").setEphemeral(true).queue();
      return;
    }

    String commandName = event.getName();
    LOG.debug(
        "Received slash command: {} from user={} in guild={}",
        commandName,
        event.getUser().getIdLong(),
        event.getGuild().getIdLong());

    // Start metrics tracking
    SlashCommandMetrics.ExecutionContext metricsContext = metrics.recordStart(commandName);
    boolean succeeded = false;

    try {
      switch (commandName) {
        case CMD_CURRENCY_CONFIG -> handleWithAdminCheck(event, configHandler);
        case CMD_DICE_GAME_1 -> diceGame1Handler.handle(event);
        case CMD_DICE_GAME_2 -> diceGame2Handler.handle(event);
        case CMD_USER_PANEL -> userPanelHandler.handle(event);
        case CMD_ADMIN_PANEL -> handleWithAdminCheck(event, adminPanelHandler);
        case CMD_SHOP -> shopHandler.handle(event);
        case CMD_DISPATCH_PANEL -> handleWithAdminCheck(event, dispatchPanelHandler);
        default -> {
          LOG.warn("Unknown command received: {}", commandName);
          event.reply("Unknown command.").setEphemeral(true).queue();
        }
      }
      succeeded = true;
    } catch (Exception e) {
      LOG.error(
          "Error handling command: {} for user={} in guild={}",
          commandName,
          event.getUser().getIdLong(),
          event.getGuild().getIdLong(),
          e);
      BotErrorHandler.handleUnexpectedError(event, e);
    } finally {
      metrics.recordEnd(metricsContext, succeeded);
    }
  }

  private void handleWithAdminCheck(SlashCommandInteractionEvent event, CommandHandler handler) {
    // Double-check admin permissions (Discord should enforce this, but we verify)
    if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
      event
          .reply("You need Administrator permission to use this command.")
          .setEphemeral(true)
          .queue();
      return;
    }
    handler.handle(event);
  }

  /** Functional interface for command handlers. */
  @FunctionalInterface
  public interface CommandHandler {
    void handle(SlashCommandInteractionEvent event);
  }
}
