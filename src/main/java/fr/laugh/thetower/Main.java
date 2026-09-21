package fr.laugh.thetower;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import fr.laugh.thetower.api.TheTowerAPI;
import fr.laugh.thetower.compat.MCVersion;
import fr.laugh.thetower.compat.Proxy;
import fr.laugh.thetower.compat.TTMaterial;
import fr.laugh.thetower.config.ConfigMenu;
import fr.laugh.thetower.config.Placements;
import fr.laugh.thetower.discord.DiscordNotifier;
import fr.laugh.thetower.game.GeneratorType;
import fr.laugh.thetower.game.KitManager;
import fr.laugh.thetower.game.KitMenu;
import fr.laugh.thetower.lang.Messages;
import fr.laugh.thetower.listeners.BlocksListeners;
import fr.laugh.thetower.listeners.ConfigListeners;
import fr.laugh.thetower.listeners.DamageListeners;
import fr.laugh.thetower.listeners.GameListeners;
import fr.laugh.thetower.listeners.KitListeners;
import fr.laugh.thetower.listeners.LobbyListeners;
import fr.laugh.thetower.listeners.MapListeners;
import fr.laugh.thetower.listeners.PlayerListeners;
import fr.laugh.thetower.listeners.SignListeners;
import fr.laugh.thetower.quest.QuestManager;
import fr.laugh.thetower.scoreboard.ScoreboardManager;
import fr.laugh.thetower.sign.JoinSignManager;
import fr.laugh.thetower.stats.StatsManager;

/**
 * Point d'entree du plugin TheTower.
 *
 * <p>Reecriture autonome du TheTower de BadBlock sur le modele d'AntWars : plus
 * aucune dependance a la BadblockGameAPI (dont l'implementation n'existe plus),
 * plusieurs arenes par serveur, configuration en jeu, mode BungeeCord.
 *
 * <p>Compatible des serveurs 1.8 a 1.21, en Spigot comme en Paper : le jar est
 * compile en bytecode Java 8 contre l'API 1.8, et tout ce qui a change entre
 * ces versions est isole dans le paquet {@code fr.laugh.thetower.compat}.
 */
public class Main extends JavaPlugin {

    /** Prefixe de tous les messages du plugin. */
    public static final String PREFIX = ChatColor.GOLD + "[TheTower] " + ChatColor.GRAY;

    private ArenaManager arenaManager;
    private Messages messages;
    private StatsManager statsManager;
    private QuestManager questManager;
    private JoinSignManager joinSignManager;
    private ConfigMenu configMenu;
    private Placements placements;
    private ScoreboardManager scoreboardManager;
    private Proxy proxy;
    private DiscordNotifier discordNotifier;
    private KitManager kitManager;
    private KitMenu kitMenu;
    private Map<String, GeneratorType> generatorTypes;
    private Location serverLobby;

    @Override
    public void onEnable() {
        // A faire en tout premier : les couches de compatibilite en dependent.
        MCVersion.init();
        getLogger().info("Serveur detecte : " + MCVersion.raw()
                + " (1." + MCVersion.minor() + "." + MCVersion.patch() + ")"
                + (MCVersion.isLegacy() ? " - mode legacy 1.8/1.12" : ""));

        List<String> missing = TTMaterial.resolveAll(getLogger());
        if (!missing.isEmpty()) {
            getLogger().severe("Cette version de Minecraft ne fournit pas les materiaux requis : "
                    + missing + ". Le plugin est desactive.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        messages = new Messages(this);
        loadServerLobby();
        loadGameContent();
        proxy = new Proxy(this);
        discordNotifier = new DiscordNotifier(this);

        arenaManager = new ArenaManager(this);
        statsManager = new StatsManager(this);
        questManager = new QuestManager(this);
        joinSignManager = new JoinSignManager(this);
        configMenu = new ConfigMenu(this);
        placements = new Placements(this);
        scoreboardManager = new ScoreboardManager(this);
        // Point d'entree pour les autres plugins (softdepend: [TheTower]).
        TheTowerAPI.init(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListeners(this), this);
        pm.registerEvents(new DamageListeners(this), this);
        pm.registerEvents(new BlocksListeners(this), this);
        pm.registerEvents(new GameListeners(this), this);
        pm.registerEvents(new MapListeners(this), this);
        pm.registerEvents(new LobbyListeners(this), this);
        pm.registerEvents(new SignListeners(this), this);
        pm.registerEvents(new ConfigListeners(this), this);
        pm.registerEvents(new KitListeners(this), this);

        // Rafraichissement des panneaux de connexion (etat + joueurs), chaque seconde.
        new BukkitRunnable() {
            @Override
            public void run() {
                joinSignManager.updateAll();
            }
        }.runTaskTimer(this, 40L, 20L);

        // Rafraichissement des scoreboards de partie, chaque seconde.
        new BukkitRunnable() {
            @Override
            public void run() {
                scoreboardManager.updateAll();
            }
        }.runTaskTimer(this, 20L, 20L);

        PluginCommand command = getCommand("tt");
        if (command == null) {
            getLogger().severe("La commande /tt est absente du plugin.yml. Le plugin est desactive.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        CommandTower executor = new CommandTower(this, arenaManager);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        // Rechargement a chaud (/reload) : les joueurs sont deja connectes, donc
        // PlayerJoinEvent ne se declenchera pas pour eux. On charge leurs stats
        // maintenant, sinon un premier compteur creerait une entree a zero qui
        // ecraserait leurs vraies stats a la sauvegarde.
        for (Player player : Bukkit.getOnlinePlayers()) {
            statsManager.load(player);
            questManager.load(player);
            kitManager.loadSelection(player);
        }

        // Placeholders %thetower_...% : uniquement si PlaceholderAPI est la. Le
        // "new Placeholders" n'est atteint que dans cette branche, donc la JVM ne
        // charge jamais cette classe (ni l'API de PAPI) sur un serveur sans PAPI.
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new fr.laugh.thetower.api.Placeholders(this).register();
            getLogger().info("PlaceholderAPI detecte : placeholders %thetower_...% enregistres.");
        }
    }

    @Override
    public void onDisable() {
        // Sans cela, un rechargement du plugin laisserait des joueurs bloques en
        // pleine partie, et la map non regeneree.
        if (arenaManager != null) {
            arenaManager.resetAll();
            arenaManager.save();
        }
        if (scoreboardManager != null) {
            scoreboardManager.clearAll();
        }
        // Apres la remise a zero des arenes : on sauve le cache des stats et on
        // attend la fin des ecritures avant de rendre la main au serveur.
        if (statsManager != null) {
            statsManager.shutdown();
        }
        if (questManager != null) {
            questManager.shutdown();
        }
        if (kitManager != null) {
            kitManager.shutdown();
        }
    }

    /**
     * (Re)lit le contenu de jeu : kits (kits.yml) et types de generateurs
     * (config.yml). Appelee au demarrage et par /tt reload.
     */
    public void loadGameContent() {
        if (kitManager == null) {
            kitManager = new KitManager(this);
            kitMenu = new KitMenu(this);
        } else {
            kitManager.load();
        }
        generatorTypes = GeneratorType.load(getConfig().getConfigurationSection("generators"), getLogger());
        getLogger().info(generatorTypes.size() + " type(s) de generateur charge(s) : "
                + generatorTypes.keySet() + ".");
    }

    // ================= Acces aux composants =================

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public JoinSignManager getJoinSignManager() {
        return joinSignManager;
    }

    public ConfigMenu getConfigMenu() {
        return configMenu;
    }

    public Placements getPlacements() {
        return placements;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public DiscordNotifier getDiscordNotifier() {
        return discordNotifier;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public KitMenu getKitMenu() {
        return kitMenu;
    }

    /** Types de generateurs de config.yml, par cle en minuscules. */
    public Map<String, GeneratorType> getGeneratorTypes() {
        return generatorTypes;
    }

    /** Vrai si la map doit etre regeneree (terrain remis a neuf) en fin de partie. */
    public boolean isMapRegenerationEnabled() {
        return getConfig().getBoolean("map-regeneration", true);
    }

    /** Vrai si les quetes du jour (/tt quests) sont actives. */
    public boolean isQuestsEnabled() {
        return getConfig().getBoolean("quests.enabled", true);
    }

    /** Vrai si le serveur est en mode arene (auto-join a la connexion). */
    public boolean isArenaModeEnabled() {
        return getConfig().getBoolean("arena-mode.enabled", false);
    }

    /** Arene a rejoindre automatiquement en mode arene (peut etre vide). */
    public String getArenaModeArena() {
        return getConfig().getString("arena-mode.arena", "");
    }

    // ================= Lobby principal =================

    public Location getServerLobby() {
        return serverLobby;
    }

    /**
     * Renvoie un joueur "a la maison" en fin de partie ou sur /tt leave : vers
     * le serveur hub du proxy si le mode BungeeCord est actif, sinon vers le
     * lobby local. Le joueur doit avoir ete remis a zero au prealable.
     */
    public void returnToLobby(Player player) {
        if (proxy != null && proxy.isEnabled()) {
            proxy.sendToFallback(player);
            return;
        }
        sendToLocalLobby(player);
    }

    /**
     * Teleporte au lobby de CE serveur, sans jamais passer par le proxy : utile
     * a la connexion, ou envoyer un message au proxy est premature.
     */
    public void sendToLocalLobby(Player player) {
        if (serverLobby != null && serverLobby.getWorld() != null) {
            player.teleport(serverLobby);
            return;
        }
        // Repli : aucun lobby serveur defini (/tt setlobby jamais fait) ou son
        // monde n'est pas charge. On envoie au point d'apparition du monde
        // principal, faute de mieux, plutot que de laisser le joueur dans l'arene.
        List<World> worlds = Bukkit.getWorlds();
        if (!worlds.isEmpty()) {
            player.teleport(worlds.get(0).getSpawnLocation());
        }
    }

    public void setServerLobby(Location location) {
        this.serverLobby = location;
        if (location == null || location.getWorld() == null) {
            return;
        }
        FileConfiguration config = getConfig();
        config.set("server-lobby.world", location.getWorld().getName());
        config.set("server-lobby.x", location.getX());
        config.set("server-lobby.y", location.getY());
        config.set("server-lobby.z", location.getZ());
        config.set("server-lobby.yaw", location.getYaw());
        config.set("server-lobby.pitch", location.getPitch());
        saveConfig();
    }

    public void loadServerLobby() {
        serverLobby = null;
        ConfigurationSection section = getConfig().getConfigurationSection("server-lobby");
        if (section == null) {
            return;
        }
        String worldName = section.getString("world");
        if (worldName == null || worldName.isEmpty()) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning("Le monde du lobby principal (" + worldName
                    + ") n'est pas charge. Redefinis-le avec /tt setlobby.");
            return;
        }
        serverLobby = new Location(world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }

    // ================= Reglages de partie =================

    /** Duree du compte a rebours avant le lancement, en secondes. */
    public int getCountdown() {
        return Math.max(3, getConfig().getInt("countdown", 30));
    }

    /**
     * Duree maximale par defaut d'une partie, en secondes. Valeur initiale des
     * nouvelles arenes ; chacune peut ensuite la changer (menu ou /tt setduration).
     */
    public int getGameDuration() {
        return Math.max(60, getConfig().getInt("game-duration", 2400));
    }

    /** Points a atteindre par defaut pour gagner (10 sur BadBlock). */
    public int getDefaultPointsToWin() {
        return Math.max(1, getConfig().getInt("points-to-win", 10));
    }

    /** Delai minimal entre deux points d'un meme joueur, en millisecondes. */
    public long getMarkCooldownMillis() {
        return Math.max(0L, getConfig().getLong("mark-cooldown", 3L)) * 1000L;
    }

    /** Secondes entre la fin de partie et le renvoi au lobby. */
    public int getEndDelay() {
        return Math.max(1, getConfig().getInt("end-delay", 10));
    }

    /** Vrai si un joueur deconnecte en partie garde sa place pour revenir. */
    public boolean isReconnectionEnabled() {
        return getConfig().getBoolean("reconnection.enabled", true);
    }

    /** Delai pour revenir apres une deconnexion, en ms ; 0 = jusqu'a la fin de la partie. */
    public long getReconnectTimeoutMillis() {
        return Math.max(0L, getConfig().getLong("reconnection.timeout", 180L)) * 1000L;
    }
}
