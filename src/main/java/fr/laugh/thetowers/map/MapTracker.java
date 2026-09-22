package fr.laugh.thetowers.map;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Regeneration de la map par suivi des changements : on memorise l'etat
 * d'origine de chaque bloc modifie pendant la partie, puis on le rejoue en fin
 * de manche pour remettre le terrain a neuf.
 *
 * <p>Repris d'AntWars : un instantane {@link BlockState} par bloc, restaure via
 * {@link BlockState#update(boolean, boolean)}. Zero dependance, aucune API
 * posterieure a la 1.8.
 *
 * <p><b>Contenu des conteneurs.</b> Specifique a The Towers, ou les bases ont
 * des coffres : un instantane de bloc ne restaure PAS ce qu'il y a dedans (le
 * contenu vit dans l'entite du bloc, pas dans son etat). On memorise donc aussi
 * le contenu d'origine de chaque conteneur ouvert pendant la partie, restaure
 * apres les blocs. L'ancien TheTower s'en passait parce que BadBlock recopiait
 * une map neuve a chaque partie, ce qu'un serveur multi-arenes ne peut pas faire.
 *
 * <p><b>Premiere ecriture gagnante</b> : si un bloc change plusieurs fois, seul
 * son tout premier etat compte - c'est le vrai etat d'origine.
 */
public class MapTracker {

    private final Map<String, BlockState> originals = new LinkedHashMap<String, BlockState>();
    private final Map<String, ItemStack[]> contents = new LinkedHashMap<String, ItemStack[]>();
    private final Map<String, Location> contentLocations = new LinkedHashMap<String, Location>();

    /** Memorise l'etat actuel d'un bloc (a appeler AVANT sa modification). */
    public void record(Block block) {
        if (block != null) {
            record(block.getState());
        }
    }

    /**
     * Memorise un etat de bloc deja capture, par exemple
     * {@code BlockPlaceEvent.getBlockReplacedState()} (l'etat d'avant la pose).
     */
    public void record(BlockState state) {
        if (state == null || state.getWorld() == null) {
            return;
        }
        String key = key(state.getWorld().getName(), state.getX(), state.getY(), state.getZ());
        if (!originals.containsKey(key)) {
            originals.put(key, state);
        }
    }

    /**
     * Memorise le contenu d'origine d'un conteneur (coffre, four, entonnoir...)
     * a sa premiere ouverture de la partie.
     *
     * <p>Pour un coffre on lit {@link Chest#getBlockInventory()} (la moitie
     * propre a ce bloc, 27 cases), present de la 1.8 a la 1.21 : un grand coffre
     * est ainsi traite comme deux coffres independants, ce qui evite toute
     * ambiguite sur "quelle moitie porte l'inventaire" selon la version.
     */
    public void recordContents(BlockState state) {
        if (state == null || state.getWorld() == null) {
            return;
        }
        Inventory inventory = inventoryOf(state);
        if (inventory == null) {
            return;
        }
        String key = key(state.getWorld().getName(), state.getX(), state.getY(), state.getZ());
        if (contents.containsKey(key)) {
            return;
        }
        contents.put(key, copy(inventory.getContents()));
        contentLocations.put(key, state.getLocation());
    }

    /** Vrai si ce bloc est deja suivi (chainage de l'ecoulement des liquides). */
    public boolean contains(Block block) {
        return block != null && originals.containsKey(
                key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    }

    public int size() {
        return originals.size();
    }

    public void clear() {
        originals.clear();
        contents.clear();
        contentLocations.clear();
    }

    /**
     * Remet tous les blocs suivis a leur etat d'origine, puis le contenu des
     * conteneurs.
     *
     * <p>Physique desactivee ({@code update(true, false)}) : aucun effet en
     * cascade pendant la restauration. Restauration synchrone : l'arene est
     * vide a ce moment, un bref pic est acceptable.
     */
    public void restore() {
        for (BlockState state : originals.values()) {
            if (state != null && state.getWorld() != null) {
                state.update(true, false);
            }
        }
        for (Map.Entry<String, ItemStack[]> entry : contents.entrySet()) {
            Location location = contentLocations.get(entry.getKey());
            if (location == null || location.getWorld() == null) {
                continue;
            }
            Inventory inventory = inventoryOf(location.getBlock().getState());
            ItemStack[] saved = entry.getValue();
            // Taille differente = ce n'est plus le meme conteneur (bloc change
            // a la main entre-temps) : on ne force rien.
            if (inventory != null && inventory.getSize() == saved.length) {
                inventory.setContents(copy(saved));
            }
        }
        clear();
    }

    private static Inventory inventoryOf(BlockState state) {
        if (state instanceof Chest) {
            return ((Chest) state).getBlockInventory();
        }
        if (state instanceof InventoryHolder) {
            return ((InventoryHolder) state).getInventory();
        }
        return null;
    }

    private static ItemStack[] copy(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] == null ? null : items[i].clone();
        }
        return copy;
    }

    private static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
