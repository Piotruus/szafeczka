package pl.szafa;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pl.szafa.Cosmetic.Kind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Glowa kostiumu jako osobny model (ItemDisplay) przyklejony do glowy gracza. Nie zajmuje slotu helmu.
 *
 * Tryb domyslny (head-as-passenger: true): display jest "zamontowany" na graczu jak pasazer, wiec porusza
 * sie razem z nim po stronie klienta (bez opoznienia przy bieganiu). Wysokosc punktu mocowania
 * MIERZYMY na serwerze (pozycja pasazera minus pozycja gracza), zamiast ja zgadywac, a obrot glowy
 * (skos) robimy transformacja wokol szyi.
 *
 * Tryb awaryjny (head-as-passenger: false lub nieudany montaz): teleportowanie co tick.
 */
public final class HeadManager {

    /** Odleglosc srodka glowy od szyi (bloki). */
    private static final double HEAD_HALF = 0.25;
    /** Srodek glowy lezy odrobine ponizej oczu. */
    private static final double EYE_TO_CENTER = 0.07;

    private static final class Entry {
        final String costumeId;
        final ItemDisplay display;
        int ticks;
        boolean passengerMode;
        float lastYaw = Float.NaN;
        float lastPitch = Float.NaN;
        double lastNeck = Double.NaN;

        Entry(String costumeId, ItemDisplay display) {
            this.costumeId = costumeId;
            this.display = display;
        }
    }

    private final SzafaPlugin plugin;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public HeadManager(SzafaPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Cosmetic c = plugin.activeOf(p, Kind.COSTUME);
            Entry en = entries.get(p.getUniqueId());

            boolean show = c != null && c.headModel != null && !p.isDead()
                    && p.getGameMode() != GameMode.SPECTATOR;
            if (!show) {
                if (en != null) {
                    remove(p);
                }
                continue;
            }
            if (en != null && (!en.costumeId.equals(c.id) || !en.display.isValid())) {
                remove(p);
                en = null;
            }
            if (en == null) {
                en = new Entry(c.id, spawn(c, p.getLocation()));
                entries.put(p.getUniqueId(), en);
            }
            en.ticks++;

            boolean mounted = plugin.headAsPassenger() && ensureMounted(p, en.display);
            if (mounted != en.passengerMode) {
                // zmiana trybu: wymus ponowne ustawienie transformacji i obrotu
                en.passengerMode = mounted;
                en.lastYaw = Float.NaN;
                en.lastPitch = Float.NaN;
                en.lastNeck = Double.NaN;
            }
            if (mounted) {
                updatePassenger(p, en);
            } else {
                updateTeleport(p, en);
            }
        }
        entries.entrySet().removeIf(e -> {
            if (Bukkit.getPlayer(e.getKey()) == null) {
                e.getValue().display.remove();
                return true;
            }
            return false;
        });
    }

    /** Pasazer znika po teleportach / smierci / zmianie swiata - montujemy ponownie. Zwraca true, gdy zamontowany. */
    private boolean ensureMounted(Player p, ItemDisplay d) {
        Entity vehicle = d.getVehicle();
        if (vehicle != null && vehicle.equals(p)) {
            return true;
        }
        if (!d.getWorld().equals(p.getWorld()) || d.getLocation().distanceSquared(p.getLocation()) > 4.0) {
            d.teleport(p.getLocation());
        }
        // gracz moze miec tylko jednego pasazera - usuwamy stare, osierocone modele z tego pluginu
        for (Entity passenger : new ArrayList<>(p.getPassengers())) {
            if (!passenger.equals(d) && passenger.getScoreboardTags().contains(SzafaPlugin.DISPLAY_TAG)) {
                passenger.remove();
            }
        }
        return p.addPassenger(d);
    }

    // ------------------------------------------------------------------ tryb pasazera

    private void updatePassenger(Player p, Entry en) {
        Location eye = p.getEyeLocation();
        float yaw = eye.getYaw() + plugin.costumeHeadYawOffset();
        float pitch = eye.getPitch();

        // wysokosc punktu mocowania pasazera nad stopami gracza - mierzona (pozycja pasazera ustala sie po 1-2 tickach)
        double attachY = p.getHeight();
        if (en.ticks >= 3) {
            double measured = en.display.getLocation().getY() - p.getLocation().getY();
            if (measured > 0.3 && measured < 2.6) {
                attachY = measured;
            }
        }
        double neckOff = (p.getEyeHeight() - EYE_TO_CENTER - HEAD_HALF) - attachY;

        if (Float.isNaN(en.lastYaw) || Math.abs(wrap(yaw - en.lastYaw)) > 0.3f) {
            en.display.setRotation(yaw, 0f);
            en.lastYaw = yaw;
        }
        if (Float.isNaN(en.lastPitch) || Math.abs(pitch - en.lastPitch) > 0.4f
                || Double.isNaN(en.lastNeck) || Math.abs(neckOff - en.lastNeck) > 0.01) {
            double th = Math.toRadians(pitch);
            Vector3f translation = new Vector3f(
                    0f,
                    (float) (neckOff + HEAD_HALF * Math.cos(th)),
                    (float) (HEAD_HALF * Math.sin(th)));
            Quaternionf rotation = new Quaternionf().rotationX((float) th);
            en.display.setInterpolationDelay(0);
            en.display.setInterpolationDuration(2);
            en.display.setTransformation(new Transformation(
                    translation, rotation, new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf()));
            en.lastPitch = pitch;
            en.lastNeck = neckOff;
        }
    }

    // ------------------------------------------------------------------ tryb awaryjny

    private void updateTeleport(Player p, Entry en) {
        if (Double.isNaN(en.lastNeck) || en.lastNeck != 0.0) {
            en.display.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), new Quaternionf(),
                    new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf()));
            en.lastNeck = 0.0;
        }
        Location eye = p.getEyeLocation();
        Location l = eye.clone().add(0, -EYE_TO_CENTER, 0);
        l.setYaw(eye.getYaw() + plugin.costumeHeadYawOffset());
        l.setPitch(eye.getPitch());
        en.display.teleport(l);
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a > 180f) {
            a -= 360f;
        } else if (a < -180f) {
            a += 360f;
        }
        return a;
    }

    private ItemDisplay spawn(Cosmetic c, Location loc) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(c.headModel);
        stack.setItemMeta(meta);

        return loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            // model glowy ma 18 jednostek (9 px): skala 0.5 => 0.5625 bloku, tyle co glowa gracza z nakladka
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), new Quaternionf(),
                    new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf()));
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(2);
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.addScoreboardTag(SzafaPlugin.DISPLAY_TAG);
        });
    }

    /** UUID wszystkich modeli glow aktualnie zarzadzanych przez ten manager. */
    public Set<UUID> trackedIds() {
        Set<UUID> ids = new HashSet<>();
        for (Entry en : entries.values()) {
            ids.add(en.display.getUniqueId());
        }
        return ids;
    }

    /** Informacje diagnostyczne dla /szafaadmin debug. */
    public List<String> debug(Player p) {
        List<String> out = new ArrayList<>();
        Entry en = entries.get(p.getUniqueId());
        if (en == null) {
            out.add("model glowy: brak");
            return out;
        }
        Entity vehicle = en.display.getVehicle();
        out.add("model glowy: " + en.costumeId + " | valid=" + en.display.isValid()
                + " | tryb=" + (en.passengerMode ? "pasazer" : "teleport"));
        out.add("zamontowany na graczu: " + (vehicle != null && vehicle.equals(p))
                + " | pasazerow gracza: " + p.getPassengers().size());
        out.add(String.format(Locale.ROOT, "punkt mocowania (zmierzony): %.3f | wysokosc gracza: %.3f | oczy: %.3f",
                en.display.getLocation().getY() - p.getLocation().getY(), p.getHeight(), p.getEyeHeight()));
        return out;
    }

    public void remove(Player p) {
        Entry en = entries.remove(p.getUniqueId());
        if (en != null) {
            en.display.remove();
        }
    }

    public void removeAll() {
        for (Entry en : entries.values()) {
            en.display.remove();
        }
        entries.clear();
    }
}
