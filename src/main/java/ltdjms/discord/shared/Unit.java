package ltdjms.discord.shared;

/**
 * A unit type that represents the absence of a meaningful value.
 * Used with Result when the success case doesn't need to carry data.
 * Similar to Void but actually instantiable.
 */
public final class Unit {

    /** The singleton Unit instance. */
    public static final Unit INSTANCE = new Unit();

    private Unit() {
        // Singleton
    }

    @Override
    public String toString() {
        return "Unit";
    }
}
