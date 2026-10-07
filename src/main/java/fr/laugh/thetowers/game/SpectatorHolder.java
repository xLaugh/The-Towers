package fr.laugh.thetowers.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marqueur du menu des joueurs d'un spectateur. Comme pour les autres menus,
 * l'inventaire se reconnait a son holder (jamais a son titre) ; le holder
 * retient aussi quel joueur se trouve dans quelle case.
 */
public class SpectatorHolder implements InventoryHolder {

    private final Map<Integer, UUID> playerBySlot = new HashMap<Integer, UUID>();
    private Inventory inventory;

    void bind(int slot, UUID playerId) {
        playerBySlot.put(Integer.valueOf(slot), playerId);
    }

    /** Joueur dans cette case, ou {@code null}. */
    public UUID playerAt(int slot) {
        return playerBySlot.get(Integer.valueOf(slot));
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
