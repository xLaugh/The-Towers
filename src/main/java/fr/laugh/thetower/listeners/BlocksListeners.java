package fr.laugh.thetower.listeners;

import java.util.Iterator;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import fr.laugh.thetower.Arena;
import fr.laugh.thetower.Main;
import fr.laugh.thetower.compat.TTMaterial;
import fr.laugh.thetower.lang.Messages;

/**
 * Regles de construction pendant une partie, reprises de BadBlock : la map se
 * casse et se construit librement (elle est regeneree en fin de partie), SAUF
 * <ul>
 *   <li>dans les zones de spawn et les piscines (sinon on boucherait sa propre
 *       piscine, ou on remplirait celle d'en face de lave) ;</li>
 *   <li>les coffres, ni cassables ni posables ;</li>
 *   <li>les pistons, interdits (ils deplaceraient des blocs proteges).</li>
 * </ul>
 *
 * <p>Si le joueur ne participe a aucune partie, le plugin ne s'en mele pas :
 * batisseurs et administrateurs restent libres sur le reste du serveur.
 */
public class BlocksListeners implements Listener {

    private final Main main;

    public BlocksListeners(Main main) {
        this.main = main;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null) {
            return;
        }
        if (!arena.isParticipant(player)) {
            event.setCancelled(true);
            return;
        }
        Block block = event.getBlock();
        if (TTMaterial.isChest(block.getType())) {
            deny(event, player, "game.chest_place_denied");
        } else if (TTMaterial.isPiston(block.getType())) {
            deny(event, player, "game.piston_denied");
        } else if (arena.isProtected(block)) {
            deny(event, player, "game.protected_zone");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null) {
            return;
        }
        if (!arena.isParticipant(player)) {
            event.setCancelled(true);
            return;
        }
        Block block = event.getBlock();
        if (TTMaterial.isChest(block.getType())) {
            deny(event, player, "game.chest_break_denied");
        } else if (arena.isProtected(block)) {
            deny(event, player, "game.protected_zone");
        }
    }

    /** Pas de lave ni d'eau versee dans une zone protegee ou une piscine. */
    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null) {
            return;
        }
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (!arena.isParticipant(player)) {
            event.setCancelled(true);
        } else if (arena.isProtected(target)) {
            deny(event, player, "game.protected_zone");
        }
    }

    /** Pas de vidange de la piscine d'une equipe au seau. */
    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null) {
            return;
        }
        if (!arena.isParticipant(player)) {
            event.setCancelled(true);
        } else if (arena.isProtected(event.getBlockClicked())) {
            deny(event, player, "game.protected_zone");
        }
    }

    /**
     * Une explosion (TNT fabriquee par un joueur...) epargne les zones
     * protegees et les coffres. Priorite haute : on retire ces blocs de la
     * liste AVANT que {@link MapListeners} n'enregistre le reste en MONITOR.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Arena arena = main.getArenaManager().getRunningArenaAt(event.getLocation());
        if (arena == null) {
            return;
        }
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (TTMaterial.isChest(block.getType()) || arena.isProtected(block)) {
                it.remove();
            }
        }
    }

    private void deny(Cancellable event, Player player, String messageKey) {
        event.setCancelled(true);
        player.sendMessage(Main.PREFIX + Messages.tr(messageKey));
    }
}
