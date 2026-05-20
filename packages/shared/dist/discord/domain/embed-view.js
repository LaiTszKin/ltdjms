/** Button style enum matching discord.js ButtonStyle. */
export var ButtonStyle;
(function (ButtonStyle) {
    ButtonStyle[ButtonStyle["PRIMARY"] = 1] = "PRIMARY";
    ButtonStyle[ButtonStyle["SECONDARY"] = 2] = "SECONDARY";
    ButtonStyle[ButtonStyle["SUCCESS"] = 3] = "SUCCESS";
    ButtonStyle[ButtonStyle["DANGER"] = 4] = "DANGER";
    ButtonStyle[ButtonStyle["LINK"] = 5] = "LINK";
})(ButtonStyle || (ButtonStyle = {}));
/**
 * Creates a ButtonView with length validation on id and label.
 * Discord limits: id max 100 chars, label max 80 chars.
 * Throws if either exceeds the limit.
 */
export function createButtonView(id, label, style = ButtonStyle.PRIMARY, disabled = false) {
    if (id.length > 100) {
        throw new Error(`ButtonView id exceeds 100 character limit: ${id.length} chars`);
    }
    if (label.length > 80) {
        throw new Error(`ButtonView label exceeds 80 character limit: ${label.length} chars`);
    }
    return { id, label, style, disabled };
}
//# sourceMappingURL=embed-view.js.map