package fr.laugh.thetower.listeners;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.InventoryHolder;

import fr.laugh.thetower.Arena;
import fr.laugh.thetower.Main;

/**
 * Alimente le suivi de {@link fr.laugh.thetower.map.MapTracker} : enregistre
 * l'etat d'ORIGINE de chaque bloc modifie pendant une partie, pour tout
 * restaurer en fin de manche.
 *
 * <p>Priorite {@code MONITOR} + {@code ignoreCancelled} : on n'observe que les
 * changements qui aboutissent reellement, et les blocs sont encore a leur etat
 * d'origine a ce stade (leur modification n'intervient qu'apres l'evenement).
 *
 * <p>Deux sources de changements :
 * <ul>
 *   <li>un joueur (casse, pose, seau, ouverture de coffre) : son arene est
 *       celle dont il est participant ;</li>
 *   <li>rien d'identifiable (explosion, feu, sable qui tombe, eau qui coule) :
 *       on rattache le changement a l'arene en cours dont la zone de jeu
 *       contient le bloc (voir {@code Arena#getArea()}).</li>
 * </ul>
 */
public class MapListeners implements Listener {

    private final Main main;

    public MapListeners(Main main) {
        this.main = main;
    }

    // ================= Changements attribues a un joueur =================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Arena arena = trackingArena(event.getPlayer());
        if (arena != null) {
            arena.getMapTracker().record(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Arena arena = trackingArena(event.getPlayer());
        if (arena != null) {
            // L'etat d'avant la pose : le restaurer revient a retirer le bloc pose.
            arena.getMapTracker().record(event.getBlockReplacedState());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Arena arena = trackingArena(event.getPlayer());
        if (arena != null) {
            arena.getMapTracker().record(event.getBlockClicked().getRelative(event.getBlockFace()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Arena arena = trackingArena(event.getPlayer());
        if (arena != null) {
            arena.getMapTracker().record(event.getBlockClicked());
        }
    }

    /**
     * Contenu des conteneurs : memorise a la premiere ouverture de la partie,
     * restaure en fin de manche. Un grand coffre est enregistre moitie par
     * moitie (voir {@code MapTracker#recordContents}).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Arena arena = trackingArena((Player) event.getPlayer());
        if (arena == null) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof DoubleChest) {
            DoubleChest chest = (DoubleChest) holder;
            recordHalf(arena, chest.getLeftSide());
            recordHalf(arena, chest.getRightSide());
        } else if (holder instanceof BlockState) {
            arena.getMapTracker().recordContents((BlockState) holder);
        }
    }

    private void recordHalf(Arena arena, InventoryHolder half) {
        if (half instanceof BlockState) {
            arena.getMapTracker().recordContents((BlockState) half);
        }
    }

    // ================= Changements sans joueur identifiable =================

    /** Blocs emportes par une explosion (deja filtres par BlocksListeners). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Arena arena = arenaAt(event.getLocation().getBlock());
        if (arena == null) {
            return;
        }
        for (Block block : event.blockList()) {
            arena.getMapTracker().record(block);
        }
    }

    /** Sable ou gravier qui tombe (depart et arrivee), Enderman... */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Arena arena = arenaAt(event.getBlock());
        if (arena != null) {
            arena.getMapTracker().record(event.getBlock());
        }
    }

    /** Feu allume (briquet, lave, foudre) : le bloc d'air qui recoit le feu. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Arena arena = arenaAt(event.getBlock());
        if (arena != null) {
            arena.getMapTracker().record(event.getBlock());
        }
    }

    /** Bloc consume par le feu. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        Arena arena = arenaAt(event.getBlock());
        if (arena != null) {
            arena.getMapTracker().record(event.getBlock());
        }
    }

    /** Feu qui se propage (et herbe, champignons... tout ce qui s'etend). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        Arena arena = arenaAt(event.getBlock());
        if (arena != null) {
            arena.getMapTracker().record(event.getBlock());
        }
    }

    /**
     * Ecoulement de l'eau ou de la lave versee au seau : chaque bloc dans lequel
     * un liquide DEJA suivi coule est ajoute au suivi (chainage). Comme la
     * source est deja enregistree, on n'a meme pas besoin de la zone de jeu.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent event) {
        if (!main.isMapRegenerationEnabled()) {
            return;
        }
        Block source = event.getBlock();
        for (Arena arena : main.getArenaManager().getArenas()) {
            if (arena.isRunning() && arena.getMapTracker().contains(source)) {
                arena.getMapTracker().record(event.getToBlock());
                return;
            }
        }
    }

    // ================= Utilitaires =================

    /** Arene a suivre pour ce joueur, ou {@code null} (hors partie / regen off). */
    private Arena trackingArena(Player player) {
        if (!main.isMapRegenerationEnabled()) {
            return null;
        }
        Arena arena = main.getArenaManager().getArenaOf(player);
        return arena != null && arena.isParticipant(player) ? arena : null;
    }

    /** Arene en cours dont la zone de jeu contient ce bloc (regen active), ou {@code null}. */
    private Arena arenaAt(Block block) {
        if (!main.isMapRegenerationEnabled() || block == null) {
            return null;
        }
        return main.getArenaManager().getRunningArenaAt(block.getLocation());
    }
}
