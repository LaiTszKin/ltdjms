import { DomainError } from '@ltdjms/shared';
/**
 * Maps LangChain/API exceptions to DomainError with user-friendly categories.
 * Matches Java LangChainExceptionMapper.
 */
export declare class LangChainExceptionMapper {
    /**
     * Maps an error to a DomainError based on cause analysis.
     */
    map(error: unknown): DomainError;
    private isAuthError;
    private isRateLimitError;
    private isTimeoutError;
    private isServerError;
    private isEmptyResponse;
    private isInvalidResponse;
}
