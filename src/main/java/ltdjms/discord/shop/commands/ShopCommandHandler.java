package ltdjms.discord.shop.commands;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.currency.bot.SlashCommandListener;
import ltdjms.discord.membership.services.EscortPriceQuote;
import ltdjms.discord.product.services.ProductService;
import ltdjms.discord.shop.services.ShopService;
import ltdjms.discord.shop.services.ShopView;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;

/** Handles the /shop slash command for browsing products. */
public class ShopCommandHandler implements SlashCommandListener.CommandHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ShopCommandHandler.class);

  private final ShopService shopService;
  private final ProductService productService;

  public ShopCommandHandler(ShopService shopService, ProductService productService) {
    this.shopService = shopService;
    this.productService = productService;
  }

  @Override
  public void handle(SlashCommandInteractionEvent event) {
    if (!event.isFromGuild() || event.getGuild() == null) {
      event.reply("此功能只能在伺服器中使用").setEphemeral(true).queue();
      return;
    }

    long guildId = event.getGuild().getIdLong();
    long userId = event.getUser().getIdLong();
    LOG.debug(
        "Processing /shop command: userId={}, guildId={}", userId, guildId);

    try {
      ShopService.ShopPage shopPage = shopService.getShopPage(guildId, 0);

      MessageEmbed embed;
      List<ActionRow> components;

      if (shopPage.isEmpty()) {
        embed = ShopView.buildEmptyShopEmbed();
        components = List.of();
      } else {
        Map<Long, EscortPriceQuote> quotes =
            shopService.quoteEscortPrices(userId, shopPage.products(), guildId);
        embed =
            ShopView.buildShopEmbed(
                shopPage.products(),
                shopPage.currentPage(),
                shopPage.totalPages(),
                guildId,
                quotes);

        components =
            ShopView.buildShopComponents(shopPage.currentPage(), shopPage.totalPages(), true);
      }

      event.replyEmbeds(embed).addComponents(components).setEphemeral(true).queue();

      LOG.debug(
          "Shop page displayed: {} products, page {}/{}",
          shopPage.products().size(),
          shopPage.currentPage(),
          shopPage.totalPages());

    } catch (Exception e) {
      LOG.error("Error displaying shop page for guildId={}", guildId, e);
      event.reply("發生錯誤，請稍後再試").setEphemeral(true).queue();
    }
  }
}
