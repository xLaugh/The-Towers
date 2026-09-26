package fr.laugh.thetowers.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import fr.laugh.thetowers.Main;
import fr.laugh.thetowers.config.ConfigHolder;

/**
 * Clics dans les menus de configuration et capture des positions en "mode pose".
 *
 * <p>On reconnait un menu de config a son {@link ConfigHolder} et on distingue
 * haut/bas par {@code getRawSlot() < getInventory().getSize()} (jamais d'appel
 * a InventoryView, devenue une interface en Paper 1.21). Aucun deplacement
 * d'objet n'est jamais permis dans un menu de config : tout clic y est annule.
 */
public class ConfigListeners implements Listener {

    private final Main main;

    public ConfigListeners(Main main) {
        this.main = main;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (top == null || !(top.getHolder() instanceof ConfigHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return; // clic dans le sac du joueur ou hors fenetre : sans effet.
        }
        main.getConfigMenu().handleClick(player, (ConfigHolder) top.getHolder(),
                rawSlot, event.isRightClick());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getInventory();
        if (top != null && top.getHolder() instanceof ConfigHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * Mode pose : le clic droit dans le monde capture une position ou un coin
     * de zone (le bloc vise). Priorite LOWEST : chez Bukkit ce sont les plus
     * basses priorites qui passent EN PREMIER. On consomme ainsi le clic avant
     * tout autre usage (coffre qui s'ouvre, panneau de connexion...).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!main.getPlacements().isPlacing(player)) {
            return;
        }
        if (main.getPlacements().handleInteract(player,
                action == Action.RIGHT_CLICK_BLOCK ? event.getClickedBlock() : null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        main.getPlacements().clear(event.getPlayer());
    }
}
