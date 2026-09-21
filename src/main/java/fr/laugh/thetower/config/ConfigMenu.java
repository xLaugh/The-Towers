package fr.laugh.thetower.config;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import fr.laugh.thetower.Arena;
import fr.laugh.thetower.Main;
import fr.laugh.thetower.compat.TTMaterial;
import fr.laugh.thetower.game.GeneratorType;
import fr.laugh.thetower.lang.Messages;

/**
 * Menu graphique de configuration des arenes : liste, edition d'une arene,
 * reglages d'equipe, generateurs et confirmation de suppression.
 *
 * <p>Meme socle qu'AntWars : inventaires marques par un {@link ConfigHolder}
 * (jamais reconnus a leur titre), icones construites via {@link TTMaterial}
 * (donc valables de la 1.8 a la 1.21), aucun appel a une methode
 * d'InventoryView. Positions et zones ne se reglent pas dans le menu mais en
 * "mode pose" (voir {@link Placements}).
 */
public class ConfigMenu {

    // ---- Tailles ----
    private static final int LIST_SIZE = 54;
    private static final int EDIT_SIZE = 54;
    private static final int TEAM_SIZE = 27;
    private static final int GEN_SIZE = 54;
    private static final int DELETE_SIZE = 27;

    // ---- Slots du menu d'edition ----
    private static final int E_BACK = 0;
    private static final int E_INFO = 4;
    private static final int E_TEAMCOUNT = 19;
    private static final int E_PERTEAM = 20;
    private static final int E_MIN = 21;
    private static final int E_MAX = 22;
    private static final int E_POINTS = 23;
    private static final int E_DURATION = 24;
    private static final int E_BOWS = 25;
    private static final int E_LOBBY = 29;
    private static final int E_GENERATORS = 31;
    private static final int E_REGEN = 33;
    private static final int[] E_TEAMS = { 37, 39, 41, 43 };
    private static final int E_DELETE = 45;
    private static final int E_SAVE = 49;

    // ---- Slots du menu d'equipe ----
    private static final int T_BACK = 0;
    private static final int T_INFO = 4;
    private static final int T_SPAWN = 10;
    private static final int T_POOL = 12;
    private static final int T_SPAWN_ZONE = 14;
    private static final int T_CHEST_ZONE = 16;

    // ---- Slots du menu des generateurs ----
    private static final int G_BACK = 0;
    private static final int G_INFO = 4;
    private static final int G_CLEAR_ALL = 8;
    private static final int G_FIRST = 18;

    // ---- Slots du menu de suppression ----
    private static final int D_INFO = 4;
    private static final int D_CONFIRM = 11;
    private static final int D_CANCEL = 15;

    private final Main main;

    public ConfigMenu(Main main) {
        this.main = main;
    }

    // ================= Ouverture des menus =================

    /** Liste de toutes les arenes. */
    public void openList(Player player) {
        ConfigHolder holder = new ConfigHolder(ConfigHolder.Type.LIST, null, null);
        Inventory inv = Bukkit.createInventory(holder, LIST_SIZE, Messages.tr("config.title.list"));
        holder.setInventory(inv);
        fill(inv);

        List<Arena> arenas = arenas();
        for (int i = 0; i < arenas.size() && i < 45; i++) {
            Arena arena = arenas.get(i);
            boolean ready = arena.isConfigured();
            String name = (ready ? ChatColor.GREEN : ChatColor.RED) + arena.getName();
            List<String> lore = new ArrayList<String>();
            lore.add(Messages.tr("config.list.state", "state", arena.getState().name()));
            lore.add(Messages.tr("config.list.players",
                    "current", arena.getPlayerCount(), "max", arena.getMaxPlayers()));
            lore.add(ready ? Messages.tr("config.list.ready")
                    : Messages.tr("config.list.incomplete", "problem", arena.validate()));
            lore.add(Messages.tr("config.list.click_edit"));
            inv.setItem(i, icon(TTMaterial.BEACON, name, lore));
        }

        inv.setItem(53, icon(TTMaterial.PAPER, Messages.tr("config.list.create_name"),
                one(Messages.tr("config.list.create_lore"))));

        player.openInventory(inv);
    }

    /** Edition d'une arene. */
    public void openEdit(Player player, String arenaName) {
        Arena arena = main.getArenaManager().getArena(arenaName);
        if (arena == null) {
            openList(player);
            return;
        }
        ConfigHolder holder = new ConfigHolder(ConfigHolder.Type.EDIT, arena.getName(), null);
        Inventory inv = Bukkit.createInventory(holder, EDIT_SIZE, Messages.tr("config.title.edit"));
        holder.setInventory(inv);
        fill(inv);

        inv.setItem(E_BACK, icon(TTMaterial.ARROW, Messages.tr("config.edit.back"), null));

        String problem = arena.validate();
        inv.setItem(E_INFO, icon(TTMaterial.BEACON,
                Messages.tr("config.edit.info_name", "arena", arena.getName()),
                one(problem == null ? Messages.tr("config.edit.info_ready")
                        : Messages.tr("config.edit.info_incomplete", "problem", problem))));

        List<String> plusMinus = one(Messages.tr("config.edit.plus_minus"));
        inv.setItem(E_TEAMCOUNT, icon(TTMaterial.PAPER,
                Messages.tr("config.edit.teamcount_name", "value", arena.getTeamCount()), plusMinus));
        inv.setItem(E_PERTEAM, icon(TTMaterial.IRON_SWORD,
                Messages.tr("config.edit.perteam_name", "value", arena.getPlayersPerTeam()), plusMinus));
        inv.setItem(E_MIN, icon(TTMaterial.LEATHER_BOOTS,
                Messages.tr("config.edit.min_name", "value", arena.getMinPlayers()), plusMinus));
        inv.setItem(E_MAX, icon(TTMaterial.CHAINMAIL_CHESTPLATE,
                Messages.tr("config.edit.max_name", "value", arena.getMaxPlayers()), plusMinus));
        inv.setItem(E_POINTS, icon(TTMaterial.GOLD_INGOT,
                Messages.tr("config.edit.points_name", "value", arena.getPointsToWin()), plusMinus));
        inv.setItem(E_DURATION, icon(TTMaterial.CLOCK,
                Messages.tr("config.edit.duration_name", "value", arena.getDuration() / 60),
                one(Messages.tr("config.edit.duration_lore"))));
        inv.setItem(E_BOWS, icon(TTMaterial.BOW,
                Messages.tr("config.edit.bows_name", "state", state(arena.isAllowBows())),
                one(Messages.tr("config.edit.toggle_click"))));

        boolean lobbySet = arena.getLobby() != null;
        List<String> lobbyLore = new ArrayList<String>();
        lobbyLore.add(lobbySet ? Messages.tr("config.edit.set") : Messages.tr("config.edit.unset"));
        lobbyLore.add(Messages.tr("config.edit.place_click"));
        inv.setItem(E_LOBBY, icon(lobbySet ? TTMaterial.GREEN_STAINED_GLASS_PANE
                : TTMaterial.RED_STAINED_GLASS_PANE, Messages.tr("config.edit.lobby_name"), lobbyLore));

        inv.setItem(E_GENERATORS, icon(TTMaterial.HOPPER,
                Messages.tr("config.edit.generators_name", "count", arena.getGenerators().size()),
                one(Messages.tr("config.edit.open_click"))));

        List<String> regenLore = new ArrayList<String>();
        regenLore.add(Messages.tr("config.edit.regen_lore"));
        regenLore.add(Messages.tr("config.edit.toggle_click"));
        inv.setItem(E_REGEN, icon(TTMaterial.TNT, Messages.tr("config.edit.regen_name",
                "state", state(main.isMapRegenerationEnabled())), regenLore));

        for (int i = 0; i < E_TEAMS.length; i++) {
            String team = Arena.TEAM_NAMES[i];
            if (arena.isTeamActive(team)) {
                List<String> lore = new ArrayList<String>();
                lore.add(status(arena.getSpawns().containsKey(team), "config.team.spawn_short"));
                lore.add(status(arena.getPools().containsKey(team), "config.team.pool_short"));
                lore.add(status(arena.getSpawnZones().containsKey(team), "config.team.spawn_zone_short"));
                lore.add(status(arena.getChestZones().containsKey(team), "config.team.chest_zone_short"));
                lore.add(Messages.tr("config.edit.team_click"));
                inv.setItem(E_TEAMS[i], icon(Arena.woolOf(team),
                        Arena.colorOf(team) + Messages.tr("config.edit.team_name", "team", team), lore));
            } else {
                inv.setItem(E_TEAMS[i], icon(TTMaterial.GRAY_STAINED_GLASS_PANE,
                        ChatColor.DARK_GRAY + team, one(Messages.tr("config.edit.team_inactive"))));
            }
        }

        inv.setItem(E_DELETE, icon(TTMaterial.BARRIER, Messages.tr("config.edit.delete_name"), null));
        inv.setItem(E_SAVE, icon(TTMaterial.NETHER_STAR, Messages.tr("config.edit.save_name"), null));

        player.openInventory(inv);
    }

    /** Reglages d'une equipe : spawn, piscine, zone de spawn, zone des coffres. */
    public void openTeam(Player player, String arenaName, String team) {
        Arena arena = main.getArenaManager().getArena(arenaName);
        if (arena == null) {
            openList(player);
            return;
        }
        ConfigHolder holder = new ConfigHolder(ConfigHolder.Type.TEAM, arena.getName(), team);
        Inventory inv = Bukkit.createInventory(holder, TEAM_SIZE, Messages.tr("config.title.team"));
        holder.setInventory(inv);
        fill(inv);

        inv.setItem(T_BACK, icon(TTMaterial.ARROW, Messages.tr("config.team.back"), null));
        inv.setItem(T_INFO, icon(Arena.woolOf(team),
                Arena.colorOf(team) + Messages.tr("config.edit.team_name", "team", team), null));

        List<String> spawnLore = new ArrayList<String>();
        spawnLore.add(status(arena.getSpawns().containsKey(team), "config.team.spawn_short"));
        spawnLore.add(Messages.tr("config.edit.place_click"));
        inv.setItem(T_SPAWN, icon(TTMaterial.NETHER_STAR, Messages.tr("config.team.spawn_name"), spawnLore));

        inv.setItem(T_POOL, icon(TTMaterial.WATER_BUCKET, Messages.tr("config.team.pool_name"),
                zoneLore(arena.getPools().containsKey(team), "config.team.pool_short",
                        "config.team.pool_hint", true)));
        inv.setItem(T_SPAWN_ZONE, icon(TTMaterial.IRON_BARS, Messages.tr("config.team.spawn_zone_name"),
                zoneLore(arena.getSpawnZones().containsKey(team), "config.team.spawn_zone_short",
                        "config.team.spawn_zone_hint", false)));
        inv.setItem(T_CHEST_ZONE, icon(TTMaterial.CHEST, Messages.tr("config.team.chest_zone_name"),
                zoneLore(arena.getChestZones().containsKey(team), "config.team.chest_zone_short",
                        "config.team.chest_zone_hint", false)));

        player.openInventory(inv);
    }

    /** Generateurs : un bouton par type de config.yml. */
    public void openGenerators(Player player, String arenaName) {
        Arena arena = main.getArenaManager().getArena(arenaName);
        if (arena == null) {
            openList(player);
            return;
        }
        ConfigHolder holder = new ConfigHolder(ConfigHolder.Type.GENERATORS, arena.getName(), null);
        Inventory inv = Bukkit.createInventory(holder, GEN_SIZE, Messages.tr("config.title.generators"));
        holder.setInventory(inv);
        fill(inv);

        inv.setItem(G_BACK, icon(TTMaterial.ARROW, Messages.tr("config.edit.back_arena"), null));
        inv.setItem(G_INFO, icon(TTMaterial.HOPPER,
                Messages.tr("config.edit.generators_name", "count", arena.getGenerators().size()),
                one(Messages.tr("config.generators.info"))));
        inv.setItem(G_CLEAR_ALL, icon(TTMaterial.BARRIER, Messages.tr("config.generators.clear_all"), null));

        List<GeneratorType> types = generatorTypes();
        for (int i = 0; i < types.size() && G_FIRST + i < GEN_SIZE; i++) {
            GeneratorType type = types.get(i);
            List<String> lore = new ArrayList<String>();
            lore.add(Messages.tr("config.generators.count", "count", arena.countGenerators(type.getKey())));
            lore.add(Messages.tr("config.generators.interval",
                    "seconds", String.format(java.util.Locale.US, "%.1f", type.getIntervalTicks() / 20d)));
            lore.add(Messages.tr("config.generators.add"));
            lore.add(Messages.tr("config.generators.clear"));
            inv.setItem(G_FIRST + i, icon(type.createItem(),
                    Messages.tr("config.generators.type_name", "type", type.getKey()), lore));
        }

        player.openInventory(inv);
    }

    /** Confirmation de suppression. */
    public void openDelete(Player player, String arenaName) {
        Arena arena = main.getArenaManager().getArena(arenaName);
        if (arena == null) {
            openList(player);
            return;
        }
        ConfigHolder holder = new ConfigHolder(ConfigHolder.Type.DELETE, arena.getName(), null);
        Inventory inv = Bukkit.createInventory(holder, DELETE_SIZE, Messages.tr("config.title.delete"));
        holder.setInventory(inv);
        fill(inv);

        inv.setItem(D_INFO, icon(TTMaterial.BARRIER,
                Messages.tr("config.delete.info_name", "arena", arena.getName()),
                one(Messages.tr("config.delete.info_lore"))));
        inv.setItem(D_CONFIRM, icon(TTMaterial.GREEN_STAINED_GLASS_PANE,
                Messages.tr("config.delete.confirm"), null));
        inv.setItem(D_CANCEL, icon(TTMaterial.RED_STAINED_GLASS_PANE,
                Messages.tr("config.delete.cancel"), null));

        player.openInventory(inv);
    }

    // ================= Clics =================

    /**
     * Route un clic sur un menu de config. {@code rightClick} distingue le clic
     * droit (decrementer un nombre, effacer une zone ou des generateurs).
     */
    public void handleClick(Player player, ConfigHolder holder, int slot, boolean rightClick) {
        switch (holder.getType()) {
            case LIST:
                clickList(player, slot);
                break;
            case EDIT:
                clickEdit(player, holder.getArena(), slot, rightClick);
                break;
            case TEAM:
                clickTeam(player, holder.getArena(), holder.getTeam(), slot, rightClick);
                break;
            case GENERATORS:
                clickGenerators(player, holder.getArena(), slot, rightClick);
                break;
            case DELETE:
                clickDelete(player, holder.getArena(), slot);
                break;
            default:
                break;
        }
    }

    private void clickList(Player player, int slot) {
        List<Arena> arenas = arenas();
        if (slot >= 0 && slot < arenas.size() && slot < 45) {
            openEdit(player, arenas.get(slot).getName());
        }
    }

    private void clickEdit(Player player, String arenaName, int slot, boolean rightClick) {
        Arena arena = main.getArenaManager().getArena(arenaName);
        if (arena == null) {
            openList(player);
            return;
        }
        int delta = rightClick ? -1 : 1;
        switch (slot) {
            case E_BACK:
                openList(player);
                return;
            case E_TEAMCOUNT:
                arena.setTeamCount(clamp(arena.getTeamCount() + delta, 2, 4));
                normalize(arena);
                break;
            case E_PERTEAM:
                arena.setPlayersPerTeam(clamp(arena.getPlayersPerTeam() + delta, 1, 16));
                normalize(arena);
                break;
            case E_MIN:
                arena.setMinPlayers(clamp(arena.getMinPlayers() + delta, 2, arena.getMaxPlayers()));
                break;
            case E_MAX:
                arena.setMaxPlayers(clamp(arena.getMaxPlayers() + delta, 2,
                        arena.getTeamCount() * arena.getPlayersPerTeam()));
                normalize(arena);
                break;
            case E_POINTS:
                arena.setPointsToWin(clamp(arena.getPointsToWin() + delta, 1, 100));
                break;
            case E_DURATION:
                // Reglage par pas de 5 minutes (stocke en secondes), borne 5 a 120.
                arena.setDuration(clamp(arena.getDuration() / 60 + delta * 5, 5, 120) * 60);
                break;
            case E_BOWS:
                arena.setAllowBows(!arena.isAllowBows());
                break;
            case E_LOBBY:
                main.getPlacements().start(player, new PlacementSession(arenaName,
                        PlacementSession.Kind.LOBBY, null, null));
                return;
            case E_GENERATORS:
                openGenerators(player, arenaName);
                return;
            case E_REGEN:
                main.getConfig().set("map-regeneration", !main.isMapRegenerationEnabled());
                main.saveConfig();
                break;
            case E_DELETE:
                openDelete(player, arenaName);
                return;
            case E_SAVE:
                save(player, arena);
                openEdit(player, arenaName);
                return;
            default:
                int teamIndex = indexOf(E_TEAMS, slot);
                if (teamIndex >= 0) {
                    String team = Arena.TEAM_NAMES[teamIndex];
                    if (arena.isTeamActive(team)) {
                        openTeam(player, arenaName, team);
                    }
                }
                return;
        }
        main.getArenaManager().save();
        openEdit(player, arenaName);
    }

    private void clickTeam(Player player, String arenaName, String team, int slot, boolean rightClick) {
        Arena arena = main.getArenaManager().getArena(arenaName);
        if (arena == null) {
            openList(player);
            return;
        }
        PlacementSession.Kind kind;
        switch (slot) {
            case T_BACK:
                openEdit(player, arenaName);
                return;
            case T_SPAWN:
                kind = PlacementSession.Kind.SPAWN;
                break;
            case T_POOL:
                kind = PlacementSession.Kind.POOL;
                break;
            case T_SPAWN_ZONE:
                kind = PlacementSession.Kind.SPAWN_ZONE;
                break;
            case T_CHEST_ZONE:
                kind = PlacementSession.Kind.CHEST_ZONE;
                break;
            default:
                return;
        }
        // Clic droit sur une zone facultative : on l'efface. La piscine, elle,
        // est obligatoire : on ne l'efface pas par erreur d'un clic.
        if (rightClick && (kind == PlacementSession.Kind.SPAWN_ZONE || kind == PlacementSession.Kind.CHEST_ZONE)) {
            if (kind == PlacementSession.Kind.SPAWN_ZONE) {
                arena.setSpawnZone(team, null);
            } else {
                arena.setChestZone(team, null);
            }
            main.getArenaManager().save();
            player.sendMessage(Main.PREFIX + Messages.tr("config.team.zone_cleared"));
            openTeam(player, arenaName, team);
            return;
        }
        main.getPlacements().start(player, new PlacementSession(arenaName, kind, team, null));
    }

    private void clickGenerators(Player player, String arenaName, int slot, boolean rightClick) {
        Arena arena = main.getArenaManager().getArena(arenaName);
        if (arena == null) {
            openList(player);
            return;
        }
        if (slot == G_BACK) {
            openEdit(player, arenaName);
            return;
        }
        if (slot == G_CLEAR_ALL) {
            int removed = arena.clearGenerators(null);
            main.getArenaManager().save();
            player.sendMessage(Main.PREFIX + Messages.tr("command.generators_cleared", "count", removed));
            openGenerators(player, arenaName);
            return;
        }
        List<GeneratorType> types = generatorTypes();
        int index = slot - G_FIRST;
        if (index < 0 || index >= types.size()) {
            return;
        }
        GeneratorType type = types.get(index);
        if (rightClick) {
            int removed = arena.clearGenerators(type.getKey());
            main.getArenaManager().save();
            player.sendMessage(Main.PREFIX + Messages.tr("command.generators_cleared", "count", removed));
            openGenerators(player, arenaName);
            return;
        }
        main.getPlacements().start(player, new PlacementSession(arenaName,
                PlacementSession.Kind.GENERATOR, null, type.getKey()));
    }

    private void clickDelete(Player player, String arenaName, int slot) {
        if (slot == D_CONFIRM) {
            main.getArenaManager().deleteArena(arenaName);
            player.sendMessage(Main.PREFIX + Messages.tr("command.arena_deleted", "name", arenaName));
            openList(player);
        } else if (slot == D_CANCEL) {
            openEdit(player, arenaName);
        }
    }

    private void save(Player player, Arena arena) {
        String problem = arena.validate();
        if (problem != null) {
            player.sendMessage(Main.PREFIX + Messages.tr("command.arena_incomplete", "problem", problem));
        } else {
            main.getArenaManager().save();
            player.sendMessage(Main.PREFIX + Messages.tr("command.arena_saved", "arena", arena.getName()));
        }
    }

    // ================= Utilitaires =================

    private List<Arena> arenas() {
        return new ArrayList<Arena>(main.getArenaManager().getArenas());
    }

    private List<GeneratorType> generatorTypes() {
        return new ArrayList<GeneratorType>(main.getGeneratorTypes().values());
    }

    /** Ramene min/max dans des bornes coherentes apres un changement de format. */
    private void normalize(Arena arena) {
        int capacity = arena.getTeamCount() * arena.getPlayersPerTeam();
        if (arena.getMaxPlayers() > capacity) {
            arena.setMaxPlayers(capacity);
        }
        if (arena.getMaxPlayers() < 2) {
            arena.setMaxPlayers(2);
        }
        if (arena.getMinPlayers() > arena.getMaxPlayers()) {
            arena.setMinPlayers(arena.getMaxPlayers());
        }
        if (arena.getMinPlayers() < 2) {
            arena.setMinPlayers(2);
        }
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /** Ligne "coche Piscine" / "croix Piscine" selon qu'un element est defini. */
    private String status(boolean defined, String labelKey) {
        return Messages.tr(defined ? "config.status.ok" : "config.status.missing",
                "what", Messages.tr(labelKey));
    }

    private List<String> zoneLore(boolean defined, String labelKey, String hintKey, boolean mandatory) {
        List<String> lore = new ArrayList<String>();
        lore.add(status(defined, labelKey));
        lore.add(Messages.tr(hintKey));
        lore.add(Messages.tr("config.team.zone_click"));
        if (!mandatory) {
            lore.add(Messages.tr("config.team.zone_clear_click"));
        }
        return lore;
    }

    private String state(boolean on) {
        return on ? Messages.tr("config.state.on") : Messages.tr("config.state.off");
    }

    private List<String> one(String line) {
        List<String> list = new ArrayList<String>();
        list.add(line);
        return list;
    }

    /** Remplit tout l'inventaire de panneaux gris (les cases vides restent grises). */
    private void fill(Inventory inv) {
        ItemStack pane = TTMaterial.GRAY_STAINED_GLASS_PANE.item();
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            // Un espace comme nom : la case parait vide mais reste decorative.
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, pane.clone());
        }
    }

    private ItemStack icon(TTMaterial material, String name, List<String> lore) {
        return icon(material.item(), name, lore);
    }

    private ItemStack icon(ItemStack item, String name, List<String> lore) {
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
