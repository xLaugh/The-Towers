package fr.laugh.thetower.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import fr.laugh.thetower.Arena;
import fr.laugh.thetower.Main;
import fr.laugh.thetower.compat.Enchants;
import fr.laugh.thetower.compat.TTMaterial;
import fr.laugh.thetower.lang.Messages;

/**
 * Menu de choix des kits : une icone par kit, avec sa description, son contenu
 * et son etat pour ce joueur (choisi, disponible, ou ce qu'il reste a faire
 * pour le debloquer, avec la progression).
 *
 * <p>Les kits sont ranges dans une grille de 7 colonnes entouree d'une bordure
 * (jusqu'a 28 kits). Aucun appel a une methode d'InventoryView.
 */
public class KitMenu {

    private static final int COLUMNS = 7;
    private static final int MAX_ROWS = 6;

    private final Main main;

    public KitMenu(Main main) {
        this.main = main;
    }

    public void open(Player player) {
        KitManager manager = main.getKitManager();
        List<KitDefinition> kits = manager.getKits();
        int rows = Math.min(MAX_ROWS, 2 + (kits.size() + COLUMNS - 1) / COLUMNS);
        int capacity = (rows - 2) * COLUMNS;

        KitHolder holder = new KitHolder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Messages.tr("kits.title"));
        holder.setInventory(inv);
        fill(inv);

        KitDefinition selected = manager.getSelected(player);
        for (int i = 0; i < kits.size() && i < capacity; i++) {
            KitDefinition kit = kits.get(i);
            int slot = 9 * (1 + i / COLUMNS) + 1 + i % COLUMNS;
            holder.bind(slot, kit.getId());
            inv.setItem(slot, icon(player, kit, kit.getId().equals(selected.getId())));
        }
        player.openInventory(inv);
    }

    /** Clic dans le menu : choisit le kit s'il est debloque. */
    public void handleClick(Player player, KitHolder holder, int slot) {
        KitDefinition kit = main.getKitManager().getKit(holder.kitAt(slot));
        if (kit == null) {
            return;
        }
        choose(player, kit);
        player.closeInventory();
    }

    /** Choisit un kit (menu ou /tt kit <kit>) et previent le joueur. */
    public void choose(Player player, KitDefinition kit) {
        if (!main.getKitManager().select(player, kit)) {
            player.sendMessage(Main.PREFIX + Messages.tr("kits.locked"));
            return;
        }
        Arena arena = main.getArenaManager().getArenaOf(player);
        boolean inGame = arena != null && arena.isParticipant(player);
        player.sendMessage(Main.PREFIX + Messages.tr(inGame ? "kits.selected_next_life" : "kits.selected",
                "kit", kit.getDisplayName()));
    }

    private ItemStack icon(Player player, KitDefinition kit, boolean selected) {
        KitManager manager = main.getKitManager();
        ItemStack item = kit.createIcon();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(kit.getDisplayName());

        List<String> lore = new ArrayList<String>(kit.getDescription());
        lore.add("");
        lore.add(Messages.tr("kits.contents"));
        for (ItemStack content : kit.getItems()) {
            lore.add(Messages.tr("kits.content_line", "amount", content.getAmount(),
                    "item", pretty(content), "ench",
                    content.getEnchantments().isEmpty() ? "" : Messages.tr("kits.enchanted")));
        }
        lore.add("");

        if (selected) {
            lore.add(Messages.tr("kits.status.selected"));
            Enchants.glow(meta);
        } else if (manager.isUnlocked(player, kit)) {
            lore.add(Messages.tr("kits.status.click"));
        } else if (kit.getPermission() != null && !player.hasPermission(kit.getPermission())) {
            lore.add(Messages.tr("kits.status.permission"));
        } else {
            lore.add(Messages.tr("kits.status.locked"));
            for (Map.Entry<String, Integer> requirement : kit.getRequirements().entrySet()) {
                int current = manager.progress(player, requirement.getKey());
                int needed = requirement.getValue().intValue();
                lore.add(Messages.tr("kits.requirement",
                        "color", current >= needed ? ChatColor.GREEN : ChatColor.RED,
                        "label", Messages.tr("kits.stat." + requirement.getKey()),
                        "current", Math.min(current, needed), "needed", needed));
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** "BAKED_POTATO" -> "Baked potato" : lisible sans table de traduction des items. */
    private static String pretty(ItemStack item) {
        String name = item.getType().name().toLowerCase().replace('_', ' ');
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static void fill(Inventory inv) {
        ItemStack pane = TTMaterial.GRAY_STAINED_GLASS_PANE.item();
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, pane.clone());
        }
    }
}
