package fr.laugh.thetower.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import fr.laugh.thetower.Arena;
import fr.laugh.thetower.Main;
import fr.laugh.thetower.compat.TTMaterial;
import fr.laugh.thetower.lang.Messages;

/**
 * Regles propres a The Tower : marquer en entrant dans une piscine adverse,
 * coffres reserves a leur equipe, arcs eventuellement interdits.
 */
public class GameListeners implements Listener {

    private final Main main;

    public GameListeners(Main main) {
        this.main = main;
    }

    /**
     * Le coeur du jeu. On ne regarde que les changements de BLOC : un
     * mouvement de tete ou un deplacement d'un dixieme de bloc ne peut pas faire
     * entrer dans une piscine, inutile de parcourir les equipes pour si peu
     * (l'evenement se declenche des dizaines de fois par seconde et par joueur).
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
        Player player = event.getPlayer();
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena != null && arena.isParticipant(player)) {
            arena.tryMark(player, to);
        }
    }

    /**
     * Coffres d'equipe : dans la zone des coffres d'une equipe, seuls ses
     * membres ouvrent les conteneurs (la "teamzone" de BadBlock).
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null || !arena.isParticipant(player)) {
            return;
        }
        Block block = event.getClickedBlock();
        String owner = arena.chestZoneOwner(block);
        if (owner == null || owner.equals(arena.getTeam(player))) {
            return;
        }
        // Zone d'une autre equipe : on ne bloque que les conteneurs (coffres,
        // fours...), pas les portes ni les boutons.
        if (block.getState() instanceof InventoryHolder) {
            event.setCancelled(true);
            player.sendMessage(Main.PREFIX + Messages.tr("game.chest_locked",
                    "color", Arena.colorOf(owner), "team", owner));
        }
    }

    /** Arene sans arcs : pas de fabrication d'arc ni de fleches (comme sur BadBlock). */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null || recipe.getResult() == null) {
            return;
        }
        Material result = recipe.getResult().getType();
        if (!TTMaterial.BOW.is(result) && !TTMaterial.ARROW.is(result)) {
            return;
        }
        for (HumanEntity viewer : event.getViewers()) {
            if (!(viewer instanceof Player)) {
                continue;
            }
            Arena arena = main.getArenaManager().getArenaOf((Player) viewer);
            if (arena != null && arena.isRunning() && !arena.isAllowBows()) {
                event.getInventory().setResult(new ItemStack(Material.AIR));
                return;
            }
        }
    }
}
