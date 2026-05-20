import { describe, it, expect } from 'vitest';
import { FiatOrderStatus, FiatOrderSchema, createPending, isPaid, isExpired, isTerminal, isFulfilled, isBuyerNotified, isRewardGranted, isAdminNotified, hasFulfillmentReward, shouldAutoCreateEscortOrder, } from '../domain/fiat-order.js';
function makeBaseOrder(overrides = {}) {
    return {
        id: null,
        guildId: 123456,
        buyerUserId: 789012,
        productId: 1,
        productName: '測試商品',
        fulfillmentRewardType: null,
        fulfillmentRewardAmount: null,
        fulfillmentAutoCreateEscortOrder: false,
        fulfillmentEscortOptionCode: null,
        orderNumber: 'FD250519120000000001',
        paymentNo: 'CVS1234567890',
        amountTwd: 500,
        status: FiatOrderStatus.PENDING_PAYMENT,
        tradeStatus: null,
        paymentMessage: null,
        paidAt: null,
        expireAt: new Date(Date.now() + 86400000),
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
        reconciliationAttemptCount: 0,
        reconciliationNextAttemptAt: null,
        createdAt: new Date(),
        updatedAt: new Date(),
        ...overrides,
    };
}
describe('FiatOrder', () => {
    it('should create pending order successfully', () => {
        const order = createPending(123456, 789012, 1, '測試商品', null, null, false, null, 'FD250519120000000001', 'CVS1234567890', 500, new Date(Date.now() + 86400000));
        expect(order.status).toBe(FiatOrderStatus.PENDING_PAYMENT);
        expect(order.paidAt).toBeNull();
        expect(order.id).toBeNull();
        expect(order.tradeStatus).toBeNull();
        expect(order.reconciliationAttemptCount).toBe(0);
    });
    it('should fail validation with blank productName', () => {
        expect(() => createPending(1, 2, 3, '', null, null, false, null, 'ORD001', 'PAY001', 100, new Date())).toThrow();
    });
    it('should fail validation with empty orderNumber', () => {
        expect(() => createPending(1, 2, 3, 'Product', null, null, false, null, '', 'PAY001', 100, new Date())).toThrow();
    });
    it('should fail validation with non-positive amount', () => {
        expect(() => createPending(1, 2, 3, 'Product', null, null, false, null, 'ORD001', 'PAY001', 0, new Date())).toThrow();
    });
    it('should fail when reward type and amount are inconsistent', () => {
        expect(() => createPending(1, 2, 3, 'Product', 'CURRENCY', null, false, null, 'ORD001', 'PAY001', 100, new Date())).toThrow();
    });
    it('should fail when reward type null but amount non-null', () => {
        expect(() => createPending(1, 2, 3, 'Product', null, 100, false, null, 'ORD001', 'PAY001', 100, new Date())).toThrow();
    });
    it('should require escortOptionCode when autoCreateEscortOrder is true', () => {
        expect(() => createPending(1, 2, 3, 'Product', null, null, true, null, 'ORD001', 'PAY001', 100, new Date())).toThrow();
    });
    it('should allow autoCreateEscortOrder with valid escortOptionCode', () => {
        const order = createPending(1, 2, 3, 'Product', null, null, true, 'STANDARD', 'ORD001', 'PAY001', 100, new Date());
        expect(order.fulfillmentAutoCreateEscortOrder).toBe(true);
        expect(order.fulfillmentEscortOptionCode).toBe('STANDARD');
    });
    describe('helper functions', () => {
        it('isPaid should detect PAID status', () => {
            const paidOrder = FiatOrderSchema.parse(makeBaseOrder({ status: FiatOrderStatus.PAID, paidAt: new Date() }));
            expect(isPaid(paidOrder)).toBe(true);
            expect(isTerminal(paidOrder)).toBe(true);
        });
        it('isExpired should detect EXPIRED status', () => {
            const expiredOrder = FiatOrderSchema.parse(makeBaseOrder({
                status: FiatOrderStatus.EXPIRED,
                expiredAt: new Date(),
                terminalReason: 'EXPIRED',
                paidAt: null,
            }));
            expect(isExpired(expiredOrder)).toBe(true);
            expect(isTerminal(expiredOrder)).toBe(true);
        });
        it('isBuyerNotified should check buyerNotifiedAt', () => {
            const order = FiatOrderSchema.parse(makeBaseOrder({ buyerNotifiedAt: new Date() }));
            expect(isBuyerNotified(order)).toBe(true);
        });
        it('isRewardGranted should check rewardGrantedAt', () => {
            const order = FiatOrderSchema.parse(makeBaseOrder({ status: FiatOrderStatus.PAID, paidAt: new Date(), rewardGrantedAt: new Date() }));
            expect(isRewardGranted(order)).toBe(true);
        });
        it('isFulfilled should check fulfilledAt', () => {
            const order = FiatOrderSchema.parse(makeBaseOrder({ status: FiatOrderStatus.PAID, paidAt: new Date(), fulfilledAt: new Date() }));
            expect(isFulfilled(order)).toBe(true);
        });
        it('isAdminNotified should check adminNotifiedAt', () => {
            const order = FiatOrderSchema.parse(makeBaseOrder({
                status: FiatOrderStatus.PAID,
                paidAt: new Date(),
                adminNotifiedAt: new Date(),
            }));
            expect(isAdminNotified(order)).toBe(true);
        });
        it('hasFulfillmentReward should check reward type and amount', () => {
            const orderWithReward = FiatOrderSchema.parse(makeBaseOrder({
                fulfillmentRewardType: 'CURRENCY',
                fulfillmentRewardAmount: 100,
            }));
            expect(hasFulfillmentReward(orderWithReward)).toBe(true);
            const orderWithoutReward = FiatOrderSchema.parse(makeBaseOrder());
            expect(hasFulfillmentReward(orderWithoutReward)).toBe(false);
        });
        it('shouldAutoCreateEscortOrder should check flag and option code', () => {
            const order = FiatOrderSchema.parse(makeBaseOrder({
                fulfillmentAutoCreateEscortOrder: true,
                fulfillmentEscortOptionCode: 'STANDARD',
            }));
            expect(shouldAutoCreateEscortOrder(order)).toBe(true);
            const orderNoFlag = FiatOrderSchema.parse(makeBaseOrder());
            expect(shouldAutoCreateEscortOrder(orderNoFlag)).toBe(false);
        });
    });
});
//# sourceMappingURL=fiat-order.test.js.map