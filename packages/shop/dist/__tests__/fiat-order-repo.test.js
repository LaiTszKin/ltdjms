import { describe, it, expect, vi } from 'vitest';
import { DrizzleFiatOrderRepository } from '../persistence/drizzle-fiat-order-repository.js';
describe('DrizzleFiatOrderRepository', () => {
    function createMockDb() {
        return {
            insert: vi.fn().mockReturnValue({
                values: vi.fn().mockReturnValue({
                    returning: vi.fn().mockResolvedValue([]),
                }),
            }),
            select: vi.fn().mockReturnValue({
                from: vi.fn().mockReturnValue({
                    where: vi.fn().mockReturnValue({
                        limit: vi.fn().mockResolvedValue([]),
                        orderBy: vi.fn().mockReturnValue({
                            limit: vi.fn().mockResolvedValue([]),
                        }),
                    }),
                    orderBy: vi.fn().mockReturnValue({
                        limit: vi.fn().mockResolvedValue([]),
                    }),
                }),
            }),
            update: vi.fn().mockReturnValue({
                set: vi.fn().mockReturnValue({
                    where: vi.fn().mockReturnValue({
                        returning: vi.fn().mockResolvedValue([]),
                    }),
                }),
            }),
            delete: vi.fn().mockReturnValue({
                where: vi.fn().mockResolvedValue({ rowCount: 0 }),
            }),
            execute: vi.fn().mockResolvedValue([]),
        };
    }
    it('should create repository successfully', () => {
        const db = createMockDb();
        const repo = new DrizzleFiatOrderRepository(db);
        expect(repo).toBeInstanceOf(DrizzleFiatOrderRepository);
    });
    it('should handle empty find results', async () => {
        const db = createMockDb();
        const repo = new DrizzleFiatOrderRepository(db);
        const result = await repo.findByOrderNumber('NONEXISTENT');
        expect(result).toBeNull();
    });
    it('should handle save returning empty', async () => {
        const db = createMockDb();
        const repo = new DrizzleFiatOrderRepository(db);
        const order = {
            guildId: 1,
            buyerUserId: 2,
            productId: 3,
            productName: 'Test',
            orderNumber: 'ORD001',
            paymentNo: 'PAY001',
            amountTwd: 100,
            status: 'PENDING_PAYMENT',
            expireAt: new Date(),
            createdAt: new Date(),
            updatedAt: new Date(),
            reconciliationAttemptCount: 0,
            fulfillmentAutoCreateEscortOrder: false,
            fulfillmentRewardType: null,
            fulfillmentRewardAmount: null,
            fulfillmentEscortOptionCode: null,
            tradeStatus: null,
            paymentMessage: null,
            paidAt: null,
            expiredAt: null,
            terminalReason: null,
            buyerNotifiedAt: null,
            rewardGrantedAt: null,
            fulfilledAt: null,
            adminNotifiedAt: null,
            lastCallbackPayload: null,
            fulfillmentProcessingAt: null,
            adminNotificationProcessingAt: null,
            reconciliationProcessingAt: null,
            reconciliationNextAttemptAt: null,
        };
        await expect(repo.save(order)).rejects.toThrow('Failed to save fiat order');
    });
    it('should handle claim fulfillment processing', async () => {
        const db = createMockDb();
        const repo = new DrizzleFiatOrderRepository(db);
        // Mock update returning rowCount = 0 (not claimed)
        vi.mocked(db.update).mockReturnValue({
            set: vi.fn().mockReturnValue({
                where: vi.fn().mockResolvedValue({ rowCount: 0 }),
            }),
        });
        const result = await repo.claimFulfillmentProcessing('ORD001', new Date());
        expect(result).toBe(false);
    });
});
//# sourceMappingURL=fiat-order-repo.test.js.map