import { MockDiscordInteraction } from '@ltdjms/shared';

/**
 * Extended mock for shop handler parity tests (UT-306–308).
 */
export class ShopTestInteraction extends MockDiscordInteraction {
  private selectedValues: string[] = [];
  private textInputValues = new Map<string, string>();
  private readonly _editedComponents: unknown[][] = [];
  private readonly _replyComponents: unknown[][] = [];
  private readonly _interactionId: string;
  private _showModalCalled = false;

  constructor(
    guildId: string,
    userId: string,
    options?: {
      channelId?: string;
      ephemeral?: boolean;
      customId?: string;
      isAdmin?: boolean;
      interactionType?: 'button' | 'modalSubmit' | 'chatInput';
      interactionId?: string;
      selectedValues?: string[];
      textInputValues?: Record<string, string>;
    },
  ) {
    super(
      guildId,
      userId,
      options?.channelId,
      options?.ephemeral ?? false,
      options?.customId ?? '',
      options?.isAdmin ?? false,
      options?.interactionType ?? 'button',
    );
    this._interactionId = options?.interactionId ?? 'interaction-1234567890';
    this.selectedValues = options?.selectedValues ?? [];
    if (options?.textInputValues) {
      for (const [key, value] of Object.entries(options.textInputValues)) {
        this.textInputValues.set(key, value);
      }
    }
  }

  override getSelectedValues(): string[] {
    return [...this.selectedValues];
  }

  override getTextInputValue(customId: string): string {
    return this.textInputValues.get(customId) ?? '';
  }

  override async replyWithComponents(
    embed: unknown,
    components: unknown[],
  ): Promise<{ channelId: string; id: string } | null> {
    await super.replyEmbed(embed);
    this._replyComponents.push(components);
    return { channelId: this.getChannelId(), id: this._interactionId };
  }

  override async editWithComponents(embed: unknown, components: unknown[]): Promise<void> {
    await super.editEmbed(embed);
    this._editedComponents.push(components);
  }

  override async showModal(_modal: unknown): Promise<void> {
    this._showModalCalled = true;
  }

  override getHook(): unknown {
    return {
      id: this._interactionId,
      reply: async (options: Record<string, unknown>) => {
        if (typeof options.content === 'string') {
          await this.reply(options.content);
        }
        if (Array.isArray(options.components)) {
          this._replyComponents.push(options.components);
        }
        if (Array.isArray(options.embeds) && options.embeds.length > 0) {
          await this.replyEmbed(options.embeds[0]);
        }
      },
    };
  }

  getEditedComponents(): unknown[][] {
    return this._editedComponents.map((row) => [...row]);
  }

  getReplyComponents(): unknown[][] {
    return this._replyComponents.map((row) => [...row]);
  }

  wasShowModalCalled(): boolean {
    return this._showModalCalled;
  }
}

export function createTestProduct(
  overrides: Partial<import('../../domain/product-types.js').Product> = {},
): import('../../domain/product-types.js').Product {
  const now = new Date();
  return {
    id: 1,
    guildId: 123456789,
    name: '測試商品',
    description: null,
    rewardType: null,
    rewardAmount: null,
    currencyPrice: 100,
    fiatPriceTwd: null,
    autoCreateEscortOrder: false,
    escortOptionCode: null,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}
