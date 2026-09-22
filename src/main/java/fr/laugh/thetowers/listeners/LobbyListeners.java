package fr.laugh.thetowers.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import fr.laugh.thetowers.Arena;
import fr.laugh.thetowers.Main;
import fr.laugh.thetowers.State;
import fr.laugh.thetowers.compat.TTMaterial;

/**
 * Objets de la hotbar dans le lobby d'attente : le papier (voter le demarrage),
 * l'epee (choisir son kit) et la barriere (quitter). Le livre, lui, s'ouvre tout seul au clic droit
 * (comportement vanilla d'un livre signe) : on ne l'intercepte pas.
 *
 * <p>Pendant l'attente, on empeche aussi de jeter ou de deplacer ces objets
 * pour qu'ils restent en place.
 */
public class LobbyListeners implements Listener {

    private final Main main;

    public LobbyListeners(Main main) {
        this.main = main;
    }

    // Pas d'ignoreCancelled ici : en mode aventure, cliquer un bloc (la
    // barriere) avec un item-bloc rend l'evenement "annule" par Bukkit, ce qui
    // le ferait sauter. On veut le traiter quand meme.
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!waiting(event.getPlayer())) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        Arena arena = main.getArenaManager().getArenaOf(event.getPlayer());
        if (TTMaterial.PAPER.is(item.getType())) {
            event.setCancelled(true);
            arena.vote(event.getPlayer());
        } else if (TTMaterial.IRON_SWORD.is(item.getType())) {
            event.setCancelled(true);
            main.getKitMenu().open(event.getPlayer());
        } else if (TTMaterial.BARRIER.is(item.getType())) {
            event.setCancelled(true);
            arena.leave(event.getPlayer(), true);
        }
        // Livre : laisse le vanilla l'ouvrir.
    }

    /** On ne jette pas les objets du lobby. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (waiting(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** On ne deplace pas les objets du lobby dans l'inventaire. */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && waiting((Player) event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    /** Vrai si le joueur est dans une arene en phase d'attente. */
    private boolean waiting(Player player) {
        Arena arena = main.getArenaManager().getArenaOf(player);
        return arena != null
                && (arena.isState(State.WAITING) || arena.isState(State.STARTING));
    }
}
