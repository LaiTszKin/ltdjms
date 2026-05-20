import crypto from 'node:crypto';
const CODE_LENGTH = 16;
const CHARACTERS = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
export class RedemptionCodeGenerator {
    generate() {
        const chars = [];
        for (let i = 0; i < CODE_LENGTH; i++) {
            const index = crypto.randomInt(CHARACTERS.length);
            chars.push(CHARACTERS.charAt(index));
        }
        return chars.join('');
    }
    static isValidFormat(code) {
        if (!code || code.length !== CODE_LENGTH)
            return false;
        const upper = code.toUpperCase();
        for (const ch of upper) {
            if (CHARACTERS.indexOf(ch) === -1)
                return false;
        }
        return true;
    }
}
//# sourceMappingURL=redemption-code-generator.js.map