package ltdjms.discord.panel.commands;

import ltdjms.discord.panel.services.AdminPanelService;
import ltdjms.discord.panel.services.AdminPanelSessionManager;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdminPanelButtonHandlerTest {

    private final AdminPanelButtonHandler handler = new AdminPanelButtonHandler(
            mock(AdminPanelService.class),
            new AdminPanelSessionManager()
    );

    @Test
    @DisplayName("返回主選單時商品按鈕應維持「商品與兌換碼管理」文字")
    void mainPanelButtonsShouldKeepRedeemLabel() {
        List<ActionRow> rows = handler.buildMainPanelComponents("💰");

        ActionRow secondRow = rows.get(1);
        Button productButton = (Button) secondRow.getComponents().get(1);

        assertThat(productButton.getLabel()).isEqualTo("📦 商品與兌換碼管理");
    }

    @Test
    @DisplayName("返回主選單嵌入欄位應包含商品與兌換碼管理")
    void mainPanelEmbedShouldIncludeRedeemField() {
        MessageEmbed embed = handler.buildMainPanelEmbed("💰");

        assertThat(embed.getFields())
                .extracting(MessageEmbed.Field::getName)
                .contains("📦 商品與兌換碼管理");
    }
}
