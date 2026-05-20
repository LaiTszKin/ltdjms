import { DomainError } from '@ltdjms/shared';
/**
 * Maps LangChain/API exceptions to DomainError with user-friendly categories.
 * Matches Java LangChainExceptionMapper.
 */
export class LangChainExceptionMapper {
    /**
     * Maps an error to a DomainError based on cause analysis.
     */
    map(error) {
        if (!error) {
            return DomainError.aiResponseEmpty('AI returned an empty response');
        }
        const message = error instanceof Error ? error.message : String(error);
        const cause = error instanceof Error && error.cause
            ? error.cause
            : error;
        // Check for specific error patterns
        if (this.isAuthError(error, message)) {
            return DomainError.aiServiceAuthFailed('AI service authentication failed. Please check your API key.');
        }
        if (this.isRateLimitError(error, message)) {
            return DomainError.aiServiceRateLimited('AI service is currently busy. Please try again later.');
        }
        if (this.isTimeoutError(error, message)) {
            return DomainError.aiServiceTimeout('AI service request timed out. Please try again.');
        }
        if (this.isServerError(error, message)) {
            return DomainError.aiServiceUnavailable('AI service is currently unavailable. Please try again later.');
        }
        if (this.isEmptyResponse(error, message)) {
            return DomainError.aiResponseEmpty('AI did not generate a response.');
        }
        if (this.isInvalidResponse(error, message)) {
            return DomainError.aiResponseInvalid('AI response format was invalid.');
        }
        return DomainError.unexpectedFailure(`An unexpected error occurred: ${message}`, error instanceof Error ? error : undefined);
    }
    isAuthError(error, message) {
        if (error && typeof error === 'object') {
            const err = error;
            if (err.status === 401 ||
                err.status === 403 ||
                err.statusCode === 401 ||
                err.statusCode === 403) {
                return true;
            }
        }
        return /401|403|unauthorized|unauthorised|invalid.*api.*key|authentication failed|auth.*failed/i.test(message);
    }
    isRateLimitError(error, message) {
        if (error && typeof error === 'object') {
            const err = error;
            if (err.status === 429 || err.statusCode === 429) {
                return true;
            }
        }
        return /429|rate.+(limit|limited)|too many requests/i.test(message);
    }
    isTimeoutError(error, message) {
        if (error && typeof error === 'object') {
            const err = error;
            if (err.code === 'ETIMEDOUT' ||
                err.code === 'ECONNABORTED' ||
                err.code === 'UND_ERR_CONNECT_TIMEOUT' ||
                err.code === 'UND_ERR_HEADERS_TIMEOUT') {
                return true;
            }
        }
        return /timeout|timed.?out|etimedout|econnaborted/i.test(message);
    }
    isServerError(error, message) {
        if (error && typeof error === 'object') {
            const err = error;
            if (typeof err.status === 'number' && err.status >= 500) {
                return true;
            }
            if (typeof err.statusCode === 'number' && err.statusCode >= 500) {
                return true;
            }
        }
        return /5\d{2}|server error|service unavailable|bad gateway/i.test(message);
    }
    isEmptyResponse(error, message) {
        return /empty|null|undefined|no content|no response|empty response/i.test(message);
    }
    isInvalidResponse(error, message) {
        if (error && typeof error === 'object') {
            const err = error;
            if (err.name === 'SyntaxError' ||
                err.name === 'JSONError' ||
                err.name === 'ParseError') {
                return true;
            }
        }
        return /syntaxerror|json.*parse|unexpected token|invalid.*response|parse.*error|malformed/i.test(message);
    }
}
//# sourceMappingURL=LangChainExceptionMapper.js.map