/**
 * Best-effort DM notification service for escort dispatch events.
 * All methods catch errors internally and never throw.
 */
export class DispatchNotificationService {
    gateway;
    constructor(gateway) {
        this.gateway = gateway;
    }
    /** DM 給護航者：有新的派單待確認。 */
    async notifyEscortOrderCreated(order) {
        await this.sendDM(String(order.escortUserId), `📋 您有新的護航訂單 #${order.orderNumber} 待確認，請前往面板查看詳情。`);
    }
    /** DM 給護航者：已被指派新訂單。 */
    async notifyEscortAssigned(order) {
        await this.sendDM(String(order.escortUserId), `📌 您已被指派護航訂單 #${order.orderNumber}，請前往面板確認接單。`);
    }
    /** DM 給管理員與客戶：護航者已確認接單。 */
    async notifyEscortConfirmed(order) {
        const message = `✅ 護航者已確認接單，訂單 #${order.orderNumber} 已進入進行中狀態。`;
        await Promise.all([
            this.sendDM(String(order.assignedByUserId), message),
            this.sendDM(String(order.customerUserId), message),
        ]);
    }
    /** DM 給客戶：要求確認完成。 */
    async notifyCompletionRequested(order) {
        await this.sendDM(String(order.customerUserId), `🔔 護航者已完成服務，訂單 #${order.orderNumber} 請前往面板確認完成。若 24 小時內未確認，系統將自動完成。`);
    }
    /** DM 給護航者與管理員：客戶已確認完成。 */
    async notifyCustomerConfirmed(order) {
        const message = `🎉 客戶已確認完成，訂單 #${order.orderNumber} 已完成。感謝您的服務！`;
        await Promise.all([
            this.sendDM(String(order.escortUserId), message),
            this.sendDM(String(order.assignedByUserId), message),
        ]);
    }
    /** DM 給售後人員：有新的售後案件。 */
    async notifyAfterSalesRequested(order) {
        await this.sendDMToStaffGuild(String(order.guildId), `🔧 訂單 #${order.orderNumber} 已提出售後申請，請前往面板處理。`);
    }
    /** DM 給客戶：售後案件已被接手。 */
    async notifyAfterSalesClaimed(order) {
        await this.sendDM(String(order.customerUserId), `🛠️ 您的售後案件 #${order.orderNumber} 已被售後人員接手處理。`);
    }
    /** DM 給客戶：售後案件已結案。 */
    async notifyAfterSalesClosed(order) {
        await this.sendDM(String(order.customerUserId), `✅ 您的售後案件 #${order.orderNumber} 已結案。若有其他問題請隨時聯繫我們。`);
    }
    // ---- Private Helpers ----
    async sendDM(userId, content) {
        try {
            const client = this.gateway.requireReadyClient();
            const user = await client.users.fetch(userId);
            if (user != null) {
                await user.send({ content });
            }
        }
        catch {
            // Best-effort; swallow errors silently
        }
    }
    /**
     * Sends a notification to the guild's system channel as a fallback
     * when no specific user DM is available (e.g., after-sales staff pool).
     */
    async sendDMToStaffGuild(guildId, content) {
        try {
            const guild = this.gateway.findGuild(guildId);
            if (guild?.systemChannel != null) {
                await guild.systemChannel.send({ content });
            }
        }
        catch {
            // Best-effort; swallow errors silently
        }
    }
}
//# sourceMappingURL=DispatchNotificationService.js.map