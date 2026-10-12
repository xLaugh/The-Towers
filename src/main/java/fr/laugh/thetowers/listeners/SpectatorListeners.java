package fr.laugh.thetowers.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import fr.laugh.thetowers.Arena;
import fr.laugh.thetowers.Main;
import fr.laugh.thetowers.compat.TTMaterial;
import fr.laugh.thetowers.game.SpectatorHolder;
import fr.laugh.thetowers.lang.Messages;
import fr.laugh.thetowers.region.Cuboid;

/**
 * Tout ce qu'un spectateur ne doit pas pouvoir faire (toucher aux blocs, aux
 * coffres, deplacer ou jeter ses objets), et l'usage de ses deux objets : la
 * boussole (liste des joueurs) et la barriere (quitter).
 *
 * <p>Il n'est pas en mode "spectateur" de Minecraft mais en aventure : seuls ses
 * clics sont donc a bloquer ici. Degats, faim et ramassage sont deja couverts
 * par les autres listeners (un spectateur ne participe pas) et par
 * {@code Compat.makeSpectator}.
 */
public class SpectatorListeners implements Listener {

    /** Delai entre deux rappels "tu ne peux pas t'eloigner", en ms. */
    private static final long WARN_INTERVAL_MS = 3000L;

    private final Main main;
    private final Map<UUID, Long> lastWarning = new HashMap<UUID, Long>();

    public SpectatorListeners(Main main) {
        this.main = main;
    }

    /** L'arene que ce joueur regarde, ou {@code null} s'il n'est pas spectateur. */
    private Arena spectatorArena(Player player) {
        Arena arena = main.getArenaManager().getArenaOf(player);
        return arena != null && arena.isSpectator(player) ? arena : null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Arena arena = spectatorArena(player);
        if (arena == null) {
            return;
        }
        // Rien n'est jamais actionne par un spectateur : coffre, porte, levier,
        // plaque de pression.
        event.setCancelled(true);
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        if (TTMaterial.COMPASS.is(item.getType())) {
            main.getSpectatorMenu().open(player, arena);
        } else if (TTMaterial.BARRIER.is(item.getType())) {
            arena.leave(player, true);
            player.sendMessage(Main.PREFIX + Messages.tr("command.left_arena", "arena", arena.getName()));
        }
    }

    /**
     * Un spectateur reste dans la zone de jeu de l'arene (voir
     * {@code Arena#getArea}) : il est repousse a la limite. S'il se retrouve
     * deja dehors (teleporte par un autre plugin...), on le ramene au point
     * d'arrivee des spectateurs.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        if (!main.isSpectatorBounded()) {
            return;
        }
        Player player = event.getPlayer();
        Arena arena = spectatorArena(player);
        if (arena == null) {
            return;
        }
        Cuboid area = arena.getArea();
        if (area == null || area.contains(to)) {
            return;
        }
        if (area.contains(from)) {
            event.setCancelled(true);
        } else {
            Location spot = arena.spectatorSpot();
            if (spot != null && spot.getWorld() != null) {
                player.teleport(spot);
            }
        }
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last == null || now - last.longValue() >= WARN_INTERVAL_MS) {
            lastWarning.put(player.getUniqueId(), Long.valueOf(now));
            player.sendMessage(Main.PREFIX + Messages.tr("spectator.out_of_bounds"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastWarning.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (spectatorArena(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    /** Le menu des joueurs reagit au clic ; partout ailleurs, aucun objet ne bouge. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        Inventory top = event.getInventory();
        if (top != null && top.getHolder() instanceof SpectatorHolder) {
            event.setCancelled(true);
            int rawSlot = event.getRawSlot();
            if (rawSlot >= 0 && rawSlot < top.getSize()) {
                main.getSpectatorMenu().handleClick(player, (SpectatorHolder) top.getHolder(), rawSlot);
            }
            return;
        }
        if (spectatorArena(player) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        Inventory top = event.getInventory();
        if ((top != null && top.getHolder() instanceof SpectatorHolder) || spectatorArena(player) != null) {
            event.setCancelled(true);
        }
    }

    /** Aucun conteneur ne s'ouvre pour un spectateur, hormis son propre menu. */
    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Inventory inventory = event.getInventory();
        if (inventory != null && inventory.getHolder() instanceof SpectatorHolder) {
            return;
        }
        if (spectatorArena((Player) event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (spectatorArena(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }
}
