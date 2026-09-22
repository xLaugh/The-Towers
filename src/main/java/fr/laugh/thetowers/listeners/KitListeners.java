package fr.laugh.thetowers.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import fr.laugh.thetowers.Main;
import fr.laugh.thetowers.game.KitHolder;

/**
 * Clics dans le menu des kits.
 *
 * <p>Priorite HIGH et <b>sans</b> {@code ignoreCancelled} : au lobby
 * d'attente, {@link LobbyListeners} annule deja tout clic d'inventaire (pour
 * figer la hotbar) ; ce listener doit quand meme traiter le choix du kit.
 */
public class KitListeners implements Listener {

    private final Main main;

    public KitListeners(Main main) {
        this.main = main;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (top == null || !(top.getHolder() instanceof KitHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }
        main.getKitMenu().handleClick((Player) event.getWhoClicked(), (KitHolder) top.getHolder(), rawSlot);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getInventory();
        if (top != null && top.getHolder() instanceof KitHolder) {
            event.setCancelled(true);
        }
    }
}
