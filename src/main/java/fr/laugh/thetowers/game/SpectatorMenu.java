package fr.laugh.thetowers.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import fr.laugh.thetowers.Arena;
import fr.laugh.thetowers.Main;
import fr.laugh.thetowers.lang.Messages;

/**
 * Menu d'un spectateur : un bloc de laine a la couleur de l'equipe par joueur
 * en jeu, un clic teleporte le spectateur sur lui. Le menu d'origine de
 * Minecraft n'existe pas en 1.8, d'ou ce menu valable sur toutes les versions.
 */
public class SpectatorMenu {

    private static final int MAX_SLOTS = 54;

    private final Main main;

    public SpectatorMenu(Main main) {
        this.main = main;
    }

    public void open(Player spectator, Arena arena) {
        List<Player> targets = new ArrayList<Player>();
        for (Player player : arena.onlinePlayers()) {
            if (arena.isParticipant(player)) {
                targets.add(player);
            }
        }
        int rows = Math.max(1, Math.min(MAX_SLOTS / 9, (targets.size() + 8) / 9));

        SpectatorHolder holder = new SpectatorHolder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Messages.tr("spectator.menu_title"));
        holder.setInventory(inv);

        for (int i = 0; i < targets.size() && i < rows * 9; i++) {
            Player target = targets.get(i);
            String team = arena.getTeam(target);
            ItemStack icon = Arena.woolOf(team).item();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Arena.colorOf(team) + target.getName());
                meta.setLore(Arrays.asList(Arena.colorOf(team) + Arena.displayName(team),
                        Messages.tr("spectator.menu_lore")));
                icon.setItemMeta(meta);
            }
            holder.bind(i, target.getUniqueId());
            inv.setItem(i, icon);
        }
        spectator.openInventory(inv);
    }

    /** Clic dans le menu : teleporte le spectateur sur le joueur choisi. */
    public void handleClick(Player spectator, SpectatorHolder holder, int slot) {
        Arena arena = main.getArenaManager().getArenaOf(spectator);
        if (arena == null || !arena.isSpectator(spectator)) {
            return;
        }
        UUID id = holder.playerAt(slot);
        if (id == null) {
            return;
        }
        spectator.closeInventory();
        Player target = Bukkit.getPlayer(id);
        if (target == null || !arena.isParticipant(target)) {
            spectator.sendMessage(Main.PREFIX + Messages.tr("spectator.player_gone"));
            return;
        }
        spectator.teleport(target.getLocation());
        spectator.sendMessage(Main.PREFIX + Messages.tr("spectator.teleported",
                "color", Arena.colorOf(arena.getTeam(target)), "player", target.getName()));
    }
}
