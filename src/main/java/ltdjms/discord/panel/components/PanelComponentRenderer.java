package ltdjms.discord.panel.components;

import java.util.List;

import ltdjms.discord.discord.domain.ButtonView;
import ltdjms.discord.discord.domain.EmbedView;
import ltdjms.discord.discord.services.DiscordComponentRenderer;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

/** 向後相容的面板元件轉換入口。 */
public final class PanelComponentRenderer {

  private PanelComponentRenderer() {
    // Utility class
  }

  public static MessageEmbed buildEmbed(EmbedView view) {
    return DiscordComponentRenderer.buildEmbed(view);
  }

  public static List<Button> buildButtons(List<ButtonView> buttonViews) {
    return DiscordComponentRenderer.buildButtons(buttonViews);
  }

  public static List<ActionRow> buildActionRows(List<List<ButtonView>> buttonRows) {
    return DiscordComponentRenderer.buildActionRows(buttonRows);
  }

  public static ActionRow buildActionRow(List<ButtonView> buttonViews) {
    return DiscordComponentRenderer.buildActionRow(buttonViews);
  }

  public static ActionRow buildRow(ItemComponent... components) {
    return DiscordComponentRenderer.buildRow(components);
  }

  public static ActionRow buildRow(List<? extends ItemComponent> components) {
    return DiscordComponentRenderer.buildRow(components);
  }
}
