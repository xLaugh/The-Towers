package fr.laugh.thetower.game;

import java.util.Iterator;
import java.util.LinkedList;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Generateur vivant pendant une partie : fait apparaitre son item a
 * intervalle regulier a un emplacement fixe.
 *
 * <p>Reprend le comportement de l'ancien TheTower : les items non ramasses ne
 * s'accumulent pas indefiniment. Au-dela de {@code max-on-ground}, les plus
 * anciens sont retires AVANT de faire apparaitre le nouveau - sinon le nouvel
 * item fusionnerait avec l'ancien au tick suivant (fusion vanilla des items
 * proches) et la pile grossirait sans limite.
 *
 * <p>Vitesse annulee a l'apparition : l'item reste pile a l'endroit prevu au
 * lieu de rebondir au hasard, comme sur BadBlock.
 */
public class Generator extends BukkitRunnable {

    private final GeneratorType type;
    private final Location location;
    private final LinkedList<Item> items = new LinkedList<Item>();
    private boolean scheduled;

    public Generator(GeneratorType type, Location location) {
        this.type = type;
        this.location = location;
    }

    public void start(Plugin plugin) {
        runTaskTimer(plugin, type.getIntervalTicks(), type.getIntervalTicks());
        scheduled = true;
    }

    @Override
    public void run() {
        World world = location.getWorld();
        if (world == null
                || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return; // personne a proximite : inutile de charger le chunk.
        }

        prune();
        // Place pour le nouvel item : on retire les plus anciens d'abord.
        while (!items.isEmpty() && countOnGround() + type.createItem().getAmount() > type.getMaxOnGround()) {
            items.removeFirst().remove();
        }

        Item item = world.dropItem(location, type.createItem());
        item.setVelocity(new Vector(0, 0, 0));
        items.add(item);
    }

    /** Arrete le generateur et retire du monde tout ce qu'il a produit. */
    public void stop() {
        if (scheduled) {
            try {
                cancel();
            } catch (IllegalStateException ignored) {
                // Deja annule : rien a faire.
            }
            scheduled = false;
        }
        for (Item item : items) {
            if (item.isValid()) {
                item.remove();
            }
        }
        items.clear();
    }

    /** Oublie les items ramasses, fusionnes ou disparus. */
    private void prune() {
        Iterator<Item> it = items.iterator();
        while (it.hasNext()) {
            if (!it.next().isValid()) {
                it.remove();
            }
        }
    }

    /** Nombre d'items (et non d'entites) actuellement au sol pour ce generateur. */
    private int countOnGround() {
        int count = 0;
        for (Item item : items) {
            count += item.getItemStack().getAmount();
        }
        return count;
    }
}
