package fr.laugh.thetower.game;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marqueur du menu de choix des kits. Comme pour le menu de config, on
 * reconnait l'inventaire a son holder (jamais a son titre) ; le holder retient
 * aussi quel kit se trouve dans quelle case.
 */
public class KitHolder implements InventoryHolder {

    private final Map<Integer, String> kitBySlot = new HashMap<Integer, String>();
    private Inventory inventory;

    void bind(int slot, String kitId) {
        kitBySlot.put(Integer.valueOf(slot), kitId);
    }

    /** Identifiant du kit dans cette case, ou {@code null}. */
    public String kitAt(int slot) {
        return kitBySlot.get(Integer.valueOf(slot));
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
