/**
 * A single option inside a select menu.
 */
export interface SelectMenuOption {
  readonly label: string;
  readonly value: string;
  readonly description?: string;
  readonly emoji?: string;
  readonly default?: boolean;
}

/**
 * A single select menu definition (one dropdown in a component row).
 */
export interface SelectMenuDefinition {
  readonly customId: string;
  readonly placeholder: string;
  readonly options: SelectMenuOption[];
}

/**
 * Splits a large list of select menu options into multiple menu definitions,
 * each containing at most `maxPerMenu` options.
 *
 * Discord enforces a maximum of 25 options per string select menu.
 * Use this utility when you need to present more than 25 options by
 * splitting them across multiple menus (e.g., with pagination buttons).
 *
 * @param options  - the full list of select menu options
 * @param maxPerMenu - maximum options per menu (default 25, Discord's limit)
 * @returns an array of SelectMenuDefinition, one per page
 */
export function splitOptions(
  options: SelectMenuOption[],
  maxPerMenu: number = 25,
): SelectMenuDefinition[] {
  const menus: SelectMenuDefinition[] = [];
  let pageIndex = 0;

  for (let i = 0; i < options.length; i += maxPerMenu) {
    const slice = options.slice(i, i + maxPerMenu);
    menus.push({
      customId: `select_page_${pageIndex}`,
      placeholder: `Select an option (page ${pageIndex + 1})`,
      options: slice,
    });
    pageIndex++;
  }

  return menus;
}
