package fr.laugh.thetowers.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import fr.laugh.thetowers.Arena;
import fr.laugh.thetowers.Main;
import fr.laugh.thetowers.lang.Messages;
import fr.laugh.thetowers.sign.JoinSign;

/**
 * Panneaux de connexion : creation ({@code [thetowers]} + nom d'arene ou
 * {@code random}), retrait (a la casse), et clic droit pour rejoindre.
 *
 * <p>Un panneau de connexion est enregistre (position suivie) et son contenu est
 * reecrit en affichage live, donc on l'identifie par sa position, pas par son
 * texte.
 */
public class SignListeners implements Listener {

    private static final String SIGN_TAG = "[thetowers]";
    /** Ancien tag, d'avant le renommage du plugin : toujours accepte a la creation. */
    private static final String OLD_SIGN_TAG = "[thetower]";
    private static final String ADMIN = "thetowers.admin";

    private final Main main;

    public SignListeners(Main main) {
        this.main = main;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        String header = ChatColor.stripColor(event.getLine(0)).trim();
        if (!SIGN_TAG.equalsIgnoreCase(header) && !OLD_SIGN_TAG.equalsIgnoreCase(header)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(ADMIN)) {
            player.sendMessage(Main.PREFIX + Messages.tr("command.no_permission", "permission", ADMIN));
            return;
        }

        String arg = ChatColor.stripColor(event.getLine(1)).trim();
        String target;
        if ("random".equalsIgnoreCase(arg)) {
            target = "";
            player.sendMessage(Main.PREFIX + Messages.tr("sign.created_random"));
        } else {
            Arena arena = main.getArenaManager().getArena(arg);
            if (arena == null) {
                player.sendMessage(Main.PREFIX + Messages.tr("command.arena_not_found", "name", arg));
                return;
            }
            target = arena.getName();
            player.sendMessage(Main.PREFIX + Messages.tr("sign.created", "arena", arena.getName()));
        }

        Location loc = event.getBlock().getLocation();
        main.getJoinSignManager().register(loc, target);

        // Affiche immediatement le panneau formate (sinon il resterait "[thetowers]"
        // jusqu'au prochain rafraichissement).
        String[] lines = main.getJoinSignManager().format(main.getJoinSignManager().get(loc));
        for (int i = 0; i < 4; i++) {
            event.setLine(i, lines[i]);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (main.getJoinSignManager().get(loc) != null) {
            main.getJoinSignManager().unregister(loc);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        // Un admin en mode pose qui vise un panneau place un coin, il ne rejoint pas.
        if (main.getPlacements().isPlacing(event.getPlayer())) {
            return;
        }
        JoinSign sign = main.getJoinSignManager().get(event.getClickedBlock().getLocation());
        if (sign == null) {
            return;
        }
        event.setCancelled(true);
        main.getJoinSignManager().join(event.getPlayer(), sign);
    }
}
