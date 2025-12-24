package ltdjms.discord.shop.services;

import ltdjms.discord.product.domain.Product;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;
import java.util.List;

/**
 * Builds shop page embed and action components.
 */
public class ShopView {

    private static final Color EMBED_COLOR = new Color(0x5865F2);
    private static final int PAGE_SIZE = 5;
    private static final String DIVIDER = "────────────────────────────────────";

    public static final String BUTTON_PREV_PAGE = "shop_prev_";
    public static final String BUTTON_NEXT_PAGE = "shop_next_";

    private ShopView() {
        // Utility class
    }

    /**
     * Builds an empty shop embed when there are no products.
     */
    public static MessageEmbed buildEmptyShopEmbed() {
        return new EmbedBuilder()
                .setTitle("🏪 商店")
                .setColor(EMBED_COLOR)
                .setDescription("目前沒有可購買的商品")
                .build();
    }

    /**
     * Builds a shop embed for the given page of products.
     */
    public static MessageEmbed buildShopEmbed(List<Product> products, int currentPage, int totalPages, long guildId) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("🏪 商店")
                .setColor(EMBED_COLOR);

        StringBuilder sb = new StringBuilder();
        int startNumber = (currentPage - 1) * PAGE_SIZE + 1;
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            int number = startNumber + i;

            if (i > 0) {
                sb.append("\n").append(DIVIDER).append("\n");
            }

            sb.append("**").append(number).append(". ").append(product.name()).append("**");

            if (product.description() != null && !product.description().isBlank()) {
                sb.append("\n商品描述：").append(product.description());
            }

            if (product.hasReward()) {
                sb.append("\n獎勵：").append(product.formatReward());
            }

            sb.append("\n");
        }

        builder.setDescription(sb.toString());

        // Pagination indicator
        if (totalPages > 1) {
            builder.setFooter("第 " + currentPage + " / " + totalPages + " 頁");
        } else {
            builder.setFooter("共 " + products.size() + " 個商品");
        }

        return builder.build();
    }

    /**
     * Builds action rows for shop page navigation.
     */
    public static List<ActionRow> buildShopComponents(int currentPage, int totalPages) {
        Button prevButton;
        Button nextButton;

        if (currentPage == 1) {
            prevButton = Button.secondary(BUTTON_PREV_PAGE + (currentPage - 1), "⬅️ 上一頁")
                    .asDisabled();
        } else {
            prevButton = Button.secondary(BUTTON_PREV_PAGE + (currentPage - 1), "⬅️ 上一頁");
        }

        if (currentPage >= totalPages) {
            nextButton = Button.secondary(BUTTON_NEXT_PAGE + (currentPage + 1), "下一頁 ➡️")
                    .asDisabled();
        } else {
            nextButton = Button.secondary(BUTTON_NEXT_PAGE + (currentPage + 1), "下一頁 ➡️");
        }

        return List.of(ActionRow.of(prevButton, nextButton));
    }

    /**
     * Returns the configured page size.
     */
    public static int getPageSize() {
        return PAGE_SIZE;
    }
}
