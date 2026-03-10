package ltdjms.discord.discord.services;

import java.util.List;

import ltdjms.discord.discord.domain.ButtonView;
import ltdjms.discord.discord.domain.DiscordEmbedBuilder;
import ltdjms.discord.discord.domain.EmbedView;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

/** 將共用 Discord 視圖模型轉換為 JDA 元件。 */
public final class DiscordComponentRenderer {

  private DiscordComponentRenderer() {
    // Utility class
  }

  public static MessageEmbed buildEmbed(EmbedView view) {
    DiscordEmbedBuilder builder = new JdaDiscordEmbedBuilder();
    builder.setTitle(view.title()).setDescription(view.description()).setColor(view.color());

    if (view.fields() != null) {
      for (EmbedView.FieldView field : view.fields()) {
        builder.addField(field.name(), field.value(), field.inline());
      }
    }

    if (view.footer() != null) {
      builder.setFooter(view.footer());
    }

    return builder.build();
  }

  public static List<Button> buildButtons(List<ButtonView> buttonViews) {
    return buttonViews.stream().map(ButtonView::toJdaButton).toList();
  }

  public static List<ActionRow> buildActionRows(List<List<ButtonView>> buttonRows) {
    return buttonRows.stream().map(DiscordComponentRenderer::buildActionRow).toList();
  }

  public static ActionRow buildActionRow(List<ButtonView> buttonViews) {
    return ActionRow.of(buildButtons(buttonViews));
  }

  public static ActionRow buildRow(ItemComponent... components) {
    return ActionRow.of(components);
  }

  public static ActionRow buildRow(List<? extends ItemComponent> components) {
    return ActionRow.of(components);
  }
}
