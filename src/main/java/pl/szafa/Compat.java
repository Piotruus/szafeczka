package pl.szafa;

import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

/** Drobne zabezpieczenia na roznice miedzy wersjami API (nowsze serwery moga usuwac stare metody). */
public final class Compat {

    private Compat() {
    }

    /** Ukrywa dodatkowe linie tooltipa (np. "When on Body", atrybuty). */
    public static void hideExtras(ItemMeta m) {
        try {
            m.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
        } catch (IllegalArgumentException | NoSuchFieldError | NoSuchMethodError ignored) {
            // brak tej flagi w nowszych wersjach - pomijamy
        }
        try {
            m.addItemFlags(ItemFlag.valueOf("HIDE_ATTRIBUTES"));
        } catch (IllegalArgumentException | NoSuchFieldError | NoSuchMethodError ignored) {
            // jw.
        }
    }

    /** Calkowicie ukrywa tooltip przedmiotu. */
    public static void hideTooltip(ItemMeta m) {
        try {
            m.setHideTooltip(true);
        } catch (NoSuchMethodError ignored) {
            // jw.
        }
    }
}
