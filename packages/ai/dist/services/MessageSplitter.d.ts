/**
 * Splits long messages into chunks at paragraph or sentence boundaries.
 * Matches Java MessageSplitter.
 */
export declare class MessageSplitter {
    private readonly maxLength;
    constructor(maxLength?: number);
    /**
     * Splits content into chunks at:
     * 1. Paragraph boundaries (\n\n)
     * 2. Sentence boundaries (。！？)
     * 3. Hard split at maxLength
     */
    split(content: string): string[];
    /**
     * Finds the best split index within maxLength.
     * Prefers paragraph boundaries > sentence boundaries > hard split.
     */
    private findSplitIndex;
}
