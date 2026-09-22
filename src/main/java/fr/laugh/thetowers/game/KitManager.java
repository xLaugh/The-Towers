package fr.laugh.thetowers.game;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import fr.laugh.thetowers.Main;
import fr.laugh.thetowers.compat.Compat;
import fr.laugh.thetowers.compat.Enchants;
import fr.laugh.thetowers.compat.TTMaterial;
import fr.laugh.thetowers.compat.Yaml;
import fr.laugh.thetowers.stats.PlayerStats;
import fr.laugh.thetowers.stats.StatsStorage;

/**
 * Les kits : definitions (kits.yml), deblocage et choix de chaque joueur.
 *
 * <p>Principe repris de BadBlock, sans leur monnaie : au depart on n'a que le
 * kit par defaut, et les autres se debloquent <b>en jouant</b> - chacun exige
 * un certain nombre de parties, victoires, kills ou points marques (les stats
 * de carriere du joueur). Un kit peut aussi etre reserve a un grade par une
 * permission, comme le kit VIP de BadBlock. Le joueur choisit son kit au lobby
 * d'attente (item "Kits") ou avec /tt kit ; ce choix est retenu d'une partie a
 * l'autre.
 *
 * <p>Le choix est un confort personnel : stocke en YAML (kit-selections.yml),
 * meme en stockage MySQL, avec la meme discipline de threading que les quetes
 * (cache en memoire, I/O sur un thread dedie, jamais sur le thread principal).
 */
public class KitManager {

    /** Debloque tous les kits (tests, administrateurs). */
    public static final String ALL_KITS_PERMISSION = "thetowers.kits.all";

    private static final String SELECTIONS_ROOT = "selections";

    private final Main main;
    private final Map<String, KitDefinition> kits = new LinkedHashMap<String, KitDefinition>();
    private String defaultKitId;
    private boolean teamArmor;

    private final ExecutorService io;
    private final ConcurrentHashMap<UUID, String> selections = new ConcurrentHashMap<UUID, String>();
    private File selectionsFile;
    private FileConfiguration selectionsConfig;

    public KitManager(Main main) {
        this.main = main;
        this.io = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "TheTowers-Kits");
                thread.setDaemon(true);
                return thread;
            }
        });
        io.submit(new Runnable() {
            @Override
            public void run() {
                selectionsFile = new File(KitManager.this.main.getDataFolder(), "kit-selections.yml");
                selectionsConfig = YamlConfiguration.loadConfiguration(selectionsFile);
            }
        });
        load();
    }

    // ================= Definitions (kits.yml) =================

    /** (Re)lit kits.yml. Appelee au demarrage et par /tt reload. */
    public void load() {
        kits.clear();
        teamArmor = main.getConfig().getBoolean("kit.team-armor", true);

        File file = new File(main.getDataFolder(), "kits.yml");
        if (!file.exists()) {
            try {
                main.saveResource("kits.yml", false);
            } catch (IllegalArgumentException e) {
                main.getLogger().warning("kits.yml introuvable dans le jar.");
            }
        }
        YamlConfiguration yml = Yaml.load(file);
        ConfigurationSection section = yml.getConfigurationSection("kits");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection kitSection = section.getConfigurationSection(key);
                if (kitSection != null) {
                    KitDefinition kit = parse(key.toLowerCase(), kitSection);
                    kits.put(kit.getId(), kit);
                }
            }
        }

        if (kits.isEmpty()) {
            // Fichier absent ou vide : un kit minimal pour que le jeu reste jouable.
            main.getLogger().warning("Aucun kit dans kits.yml : kit de secours utilise.");
            List<ItemStack> items = new ArrayList<ItemStack>();
            items.add(TTMaterial.BAKED_POTATO.item(16));
            kits.put("defaut", new KitDefinition("defaut", "Defaut", TTMaterial.LEATHER_CHESTPLATE.item(),
                    new ArrayList<String>(), new LinkedHashMap<String, Integer>(), null, items));
        }
        defaultKitId = yml.getString("default-kit", "defaut").toLowerCase();
        if (!kits.containsKey(defaultKitId)) {
            String first = kits.keySet().iterator().next();
            main.getLogger().warning("Kit par defaut '" + defaultKitId + "' introuvable, '" + first
                    + "' utilise a la place.");
            defaultKitId = first;
        }
        main.getLogger().info(kits.size() + " kit(s) charge(s) : " + kits.keySet() + ".");
    }

    private KitDefinition parse(String id, ConfigurationSection s) {
        ItemStack icon = TTMaterial.itemFromName(s.getString("icon", "CHEST"), 1);
        if (icon == null) {
            main.getLogger().warning("Kit " + id + " : icone inconnue, coffre utilise.");
            icon = TTMaterial.CHEST.item();
        }

        List<String> description = new ArrayList<String>();
        for (String line : s.getStringList("description")) {
            description.add(Compat.color(line));
        }

        Map<String, Integer> requirements = new LinkedHashMap<String, Integer>();
        ConfigurationSection requires = s.getConfigurationSection("requires");
        if (requires != null) {
            for (String column : requires.getKeys(false)) {
                String key = column.toLowerCase();
                if (StatsStorage.isColumn(key)) {
                    requirements.put(key, Integer.valueOf(Math.max(0, requires.getInt(column))));
                } else {
                    main.getLogger().warning("Kit " + id + " : condition inconnue '" + column
                            + "' ignoree (valeurs possibles : games, wins, losses, kills, deaths, points, rating).");
                }
            }
        }

        String permission = s.getString("permission", "");
        List<ItemStack> items = new ArrayList<ItemStack>();
        for (Map<?, ?> entry : s.getMapList("items")) {
            ItemStack item = parseItem(id, entry);
            if (item != null) {
                items.add(item);
            }
        }

        return new KitDefinition(id, Compat.color(s.getString("name", id)), icon, description,
                requirements, permission == null || permission.trim().isEmpty() ? null : permission.trim(), items);
    }

    private ItemStack parseItem(String kitId, Map<?, ?> entry) {
        Object material = entry.get("material");
        Object amount = entry.get("amount");
        int count = amount instanceof Number ? ((Number) amount).intValue() : 1;
        ItemStack item = TTMaterial.itemFromName(material == null ? null : material.toString(), count);
        if (item == null) {
            main.getLogger().warning("Kit " + kitId + " : item ignore, materiau inconnu (" + material + ").");
            return null;
        }
        Object enchantments = entry.get("enchantments");
        if (enchantments instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) enchantments).entrySet()) {
                int level = e.getValue() instanceof Number ? ((Number) e.getValue()).intValue() : 1;
                if (!Enchants.apply(item, String.valueOf(e.getKey()), level)) {
                    main.getLogger().warning("Kit " + kitId + " : enchantement inconnu (" + e.getKey() + ").");
                }
            }
        }
        return item;
    }

    public List<KitDefinition> getKits() {
        return new ArrayList<KitDefinition>(kits.values());
    }

    /** Kit par son identifiant (insensible a la casse), ou {@code null}. */
    public KitDefinition getKit(String id) {
        return id == null ? null : kits.get(id.toLowerCase());
    }

    public KitDefinition getDefaultKit() {
        return kits.get(defaultKitId);
    }

    // ================= Deblocage =================

    /** Vrai si ce joueur peut utiliser ce kit (kit par defaut, grade, stats suffisantes). */
    public boolean isUnlocked(Player player, KitDefinition kit) {
        if (kit.getId().equals(defaultKitId) || player.hasPermission(ALL_KITS_PERMISSION)) {
            return true;
        }
        if (kit.getPermission() != null && !player.hasPermission(kit.getPermission())) {
            return false;
        }
        for (Map.Entry<String, Integer> requirement : kit.getRequirements().entrySet()) {
            if (progress(player, requirement.getKey()) < requirement.getValue().intValue()) {
                return false;
            }
        }
        return true;
    }

    /** Valeur actuelle d'une statistique du joueur (0 tant qu'elles ne sont pas chargees). */
    public int progress(Player player, String column) {
        PlayerStats stats = main.getStatsManager().getCached(player.getUniqueId());
        return stats == null ? 0 : stats.value(column);
    }

    /** Identifiants des kits actuellement debloques par ce joueur. */
    public Set<String> unlockedIds(Player player) {
        Set<String> ids = new LinkedHashSet<String>();
        for (KitDefinition kit : kits.values()) {
            if (isUnlocked(player, kit)) {
                ids.add(kit.getId());
            }
        }
        return ids;
    }

    // ================= Choix du joueur =================

    /**
     * Kit que le joueur utilisera : son choix s'il y a toujours droit (un grade
     * perdu ou un kit retire de kits.yml le ramene au kit par defaut).
     */
    public KitDefinition getSelected(Player player) {
        KitDefinition kit = getKit(selections.get(player.getUniqueId()));
        if (kit != null && isUnlocked(player, kit)) {
            return kit;
        }
        return getDefaultKit();
    }

    /** Retient le choix du joueur. Faux si le kit n'est pas debloque. */
    public boolean select(Player player, KitDefinition kit) {
        if (!isUnlocked(player, kit)) {
            return false;
        }
        final UUID id = player.getUniqueId();
        final String kitId = kit.getId();
        selections.put(id, kitId);
        io.submit(new Runnable() {
            @Override
            public void run() {
                if (selectionsConfig == null) {
                    return;
                }
                selectionsConfig.set(SELECTIONS_ROOT + "." + id, kitId);
                try {
                    selectionsConfig.save(selectionsFile);
                } catch (IOException e) {
                    main.getLogger().severe("Impossible d'ecrire kit-selections.yml : " + e.getMessage());
                }
            }
        });
        return true;
    }

    /** Charge le choix d'un joueur a sa connexion (asynchrone). */
    public void loadSelection(Player player) {
        final UUID id = player.getUniqueId();
        io.submit(new Runnable() {
            @Override
            public void run() {
                String kitId = selectionsConfig == null ? null
                        : selectionsConfig.getString(SELECTIONS_ROOT + "." + id);
                if (kitId != null) {
                    selections.put(id, kitId);
                }
            }
        });
    }

    /** Oublie un joueur a sa deconnexion (son choix est deja sur le disque). */
    public void unloadSelection(Player player) {
        selections.remove(player.getUniqueId());
    }

    /** Attend la fin des ecritures en cours. Appele a la desactivation du plugin. */
    public void shutdown() {
        io.shutdown();
        try {
            io.awaitTermination(10L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ================= Distribution =================

    /**
     * Equipe le joueur (inventaire deja vide) : armure en cuir teinte aux
     * couleurs de l'equipe, puis le contenu de son kit. Une piece d'armure du
     * kit est portee directement, a la place de la piece en cuir.
     */
    public void give(Player player, Color teamColor, boolean allowBows) {
        PlayerInventory inventory = player.getInventory();
        if (teamArmor) {
            inventory.setHelmet(dyed(TTMaterial.LEATHER_HELMET, teamColor));
            inventory.setChestplate(dyed(TTMaterial.LEATHER_CHESTPLATE, teamColor));
            inventory.setLeggings(dyed(TTMaterial.LEATHER_LEGGINGS, teamColor));
            inventory.setBoots(dyed(TTMaterial.LEATHER_BOOTS, teamColor));
        }
        for (ItemStack item : getSelected(player).itemsFor(allowBows)) {
            if (!equipArmor(inventory, item)) {
                inventory.addItem(item);
            }
        }
    }

    /**
     * Porte une piece d'armure. Reconnue a la FIN du nom de materiau, stable
     * sur toutes les versions (IRON_CHESTPLATE, CHAINMAIL_BOOTS...), plutot
     * qu'avec une liste de constantes qui aurait change en 1.13.
     */
    private static boolean equipArmor(PlayerInventory inventory, ItemStack item) {
        String name = item.getType().name();
        if (name.endsWith("_HELMET")) {
            inventory.setHelmet(item);
        } else if (name.endsWith("_CHESTPLATE")) {
            inventory.setChestplate(item);
        } else if (name.endsWith("_LEGGINGS")) {
            inventory.setLeggings(item);
        } else if (name.endsWith("_BOOTS")) {
            inventory.setBoots(item);
        } else {
            return false;
        }
        return true;
    }

    private static ItemStack dyed(TTMaterial piece, Color color) {
        ItemStack item = piece.item();
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta) {
            ((LeatherArmorMeta) meta).setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }
}
