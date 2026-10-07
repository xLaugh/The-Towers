package fr.laugh.thetowers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import fr.laugh.thetowers.game.GeneratorSpot;
import fr.laugh.thetowers.region.Cuboid;

/**
 * Chargement, sauvegarde et recherche des arenes ({@code arenes.yml}).
 *
 * <p>Structure d'une arene : reglages simples a la racine, puis une section
 * {@code teams:} (spawn, piscine, zones de chaque equipe) et une liste
 * {@code generators:}. Voir le fichier modele embarque pour un exemple.
 */
public class ArenaManager {

    private static final String ROOT = "arenes";

    private final Main main;
    private final Map<String, Arena> arenas = new LinkedHashMap<String, Arena>();
    private final Set<UUID> stranded = new HashSet<UUID>();
    /**
     * Positions lues dans arenes.yml dont le monde n'etait pas encore charge
     * (monde gere par Multiverse ou autre, charge apres TheTowers). Elles sont
     * gardees telles quelles : reecrites a chaque sauvegarde, et appliquees a
     * l'arene des que leur monde se charge. Sans cela, la premiere sauvegarde
     * (au plus tard l'arret du serveur) les effacait du fichier.
     */
    private final List<PendingLocation> pending = new ArrayList<PendingLocation>();

    private File arenasFile;
    private FileConfiguration arenasConfig;

    public ArenaManager(Main main) {
        this.main = main;
        load();
    }

    // ================= Chargement et sauvegarde =================

    public void load() {
        arenas.clear();
        pending.clear();

        arenasFile = new File(main.getDataFolder(), "arenes.yml");
        if (!arenasFile.exists()) {
            arenasFile.getParentFile().mkdirs();
            try {
                main.saveResource("arenes.yml", false);
            } catch (IllegalArgumentException e) {
                main.getLogger().warning("arenes.yml introuvable dans le jar, creation d'un fichier vide.");
            }
        }
        arenasConfig = YamlConfiguration.loadConfiguration(arenasFile);

        ConfigurationSection root = arenasConfig.getConfigurationSection(ROOT);
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            String name = section.getString("nom", key);
            Arena arena = new Arena(main, name);
            arena.setMinPlayers(section.getInt("minPlayers", 2));
            arena.setMaxPlayers(section.getInt("maxPlayers", 8));
            arena.setTeamCount(section.getInt("teamCount", 2));
            arena.setPlayersPerTeam(section.getInt("playersPerTeam", 4));
            arena.setPointsToWin(section.getInt("points", main.getDefaultPointsToWin()));
            arena.setDuration(section.getInt("duration", main.getGameDuration()));
            arena.setAllowBows(section.getBoolean("allowBows", true));
            arena.setAllowSpectators(section.getBoolean("allowSpectators", false));
            arena.setLobby(readLocation(section.getConfigurationSection("lobby"),
                    name, PendingLocation.LOBBY, null, null));

            ConfigurationSection teams = section.getConfigurationSection("teams");
            if (teams != null) {
                for (String team : Arena.TEAM_NAMES) {
                    ConfigurationSection t = teams.getConfigurationSection(team);
                    if (t == null) {
                        continue;
                    }
                    Location spawn = readLocation(t.getConfigurationSection("spawn"),
                            name, PendingLocation.SPAWN, team, null);
                    if (spawn != null) {
                        arena.setSpawn(team, spawn);
                    }
                    arena.setPool(team, readCuboid(t.getConfigurationSection("pool")));
                    arena.setSpawnZone(team, readCuboid(t.getConfigurationSection("spawnZone")));
                    arena.setChestZone(team, readCuboid(t.getConfigurationSection("chestZone")));
                }
            }

            ConfigurationSection gens = section.getConfigurationSection("generators");
            if (gens != null) {
                for (String index : gens.getKeys(false)) {
                    ConfigurationSection g = gens.getConfigurationSection(index);
                    if (g == null) {
                        continue;
                    }
                    String type = g.getString("type", "");
                    if (type.isEmpty()) {
                        continue;
                    }
                    Location location = readLocation(g, name, PendingLocation.GENERATOR, null, type);
                    if (location != null) {
                        arena.addGenerator(type, location);
                    }
                }
            }

            arenas.put(name.toLowerCase(), arena);
        }

        main.getLogger().info(arenas.size() + " arene(s) chargee(s).");
        if (!pending.isEmpty()) {
            Set<String> worlds = new HashSet<String>();
            for (PendingLocation p : pending) {
                worlds.add(p.world);
            }
            main.getLogger().warning("Monde(s) pas encore charge(s) : " + worlds + ". " + pending.size()
                    + " position(s) conservee(s), reprises des que le monde sera charge.");
        }
    }

    /**
     * Applique les positions en attente d'un monde qui vient de se charger.
     * Une position redefinie entre-temps par l'admin reste prioritaire.
     */
    public void resolvePending(World world) {
        int resolved = 0;
        for (Iterator<PendingLocation> it = pending.iterator(); it.hasNext();) {
            PendingLocation p = it.next();
            if (!p.world.equals(world.getName())) {
                continue;
            }
            it.remove();
            Arena arena = getArena(p.arena);
            if (arena == null) {
                continue;
            }
            Location location = p.toLocation(world);
            if (PendingLocation.LOBBY.equals(p.kind)) {
                if (arena.getLobby() == null) {
                    arena.setLobby(location);
                }
            } else if (PendingLocation.SPAWN.equals(p.kind)) {
                if (!arena.getSpawns().containsKey(p.team)) {
                    arena.setSpawn(p.team, location);
                }
            } else {
                arena.addGenerator(p.type, location);
            }
            resolved++;
        }
        if (resolved > 0) {
            main.getLogger().info(resolved + " position(s) reprise(s) dans le monde " + world.getName() + ".");
        }
    }

    public void save() {
        if (arenasConfig == null || arenasFile == null) {
            return;
        }
        // On repart d'une section vierge pour que la suppression d'une arene,
        // d'une zone ou d'un generateur soit reellement repercutee sur le disque.
        arenasConfig.set(ROOT, null);

        for (Arena arena : arenas.values()) {
            String path = ROOT + "." + arena.getName();
            arenasConfig.set(path + ".nom", arena.getName());
            arenasConfig.set(path + ".minPlayers", arena.getMinPlayers());
            arenasConfig.set(path + ".maxPlayers", arena.getMaxPlayers());
            arenasConfig.set(path + ".teamCount", arena.getTeamCount());
            arenasConfig.set(path + ".playersPerTeam", arena.getPlayersPerTeam());
            arenasConfig.set(path + ".points", arena.getPointsToWin());
            arenasConfig.set(path + ".duration", arena.getDuration());
            arenasConfig.set(path + ".allowBows", arena.isAllowBows());
            arenasConfig.set(path + ".allowSpectators", arena.isAllowSpectators());
            writeLocation(path + ".lobby", arena.getLobby());

            for (String team : Arena.TEAM_NAMES) {
                String teamPath = path + ".teams." + team;
                writeLocation(teamPath + ".spawn", arena.getSpawns().get(team));
                writeCuboid(teamPath + ".pool", arena.getPools().get(team));
                writeCuboid(teamPath + ".spawnZone", arena.getSpawnZones().get(team));
                writeCuboid(teamPath + ".chestZone", arena.getChestZones().get(team));
            }

            int index = 0;
            for (GeneratorSpot spot : arena.getGenerators()) {
                String genPath = path + ".generators." + index;
                arenasConfig.set(genPath + ".type", spot.getType());
                writeLocation(genPath, spot.getLocation());
                index++;
            }

            // Positions dont le monde n'est pas charge : recopiees telles quelles.
            for (PendingLocation p : pending) {
                if (!p.arena.equalsIgnoreCase(arena.getName())) {
                    continue;
                }
                if (PendingLocation.LOBBY.equals(p.kind)) {
                    if (arena.getLobby() == null) {
                        p.write(arenasConfig, path + ".lobby");
                    }
                } else if (PendingLocation.SPAWN.equals(p.kind)) {
                    if (!arena.getSpawns().containsKey(p.team)) {
                        p.write(arenasConfig, path + ".teams." + p.team + ".spawn");
                    }
                } else {
                    String genPath = path + ".generators." + index;
                    arenasConfig.set(genPath + ".type", p.type);
                    p.write(arenasConfig, genPath);
                    index++;
                }
            }
        }

        try {
            arenasConfig.save(arenasFile);
        } catch (IOException e) {
            main.getLogger().severe("Impossible d'ecrire arenes.yml : " + e.getMessage());
        }
    }

    // ================= Acces =================

    public Arena getArena(String name) {
        return name == null ? null : arenas.get(name.toLowerCase());
    }

    public Collection<Arena> getArenas() {
        return arenas.values();
    }

    public List<String> getArenaNames() {
        List<String> names = new ArrayList<String>();
        for (Arena arena : arenas.values()) {
            names.add(arena.getName());
        }
        return names;
    }

    /** Arene dans laquelle se trouve un joueur, ou {@code null}. */
    public Arena getArenaOf(Player player) {
        for (Arena arena : arenas.values()) {
            if (arena.contains(player)) {
                return arena;
            }
        }
        return null;
    }

    /**
     * Arene en cours de partie dont la zone de jeu contient cette position, ou
     * {@code null}. Sert a rattacher a une partie ce qui arrive sans joueur
     * (explosion, feu, sable qui tombe) pour la regeneration de la map.
     */
    public Arena getRunningArenaAt(Location location) {
        for (Arena arena : arenas.values()) {
            if (!arena.isRunning()) {
                continue;
            }
            Cuboid area = arena.getArea();
            if (area != null && area.contains(location)) {
                return arena;
            }
        }
        return null;
    }

    /** Arene en cours ou ce joueur deconnecte peut reprendre sa place, ou {@code null}. */
    public Arena getRejoinableArena(UUID id) {
        for (Arena arena : arenas.values()) {
            if (arena.canRejoin(id)) {
                return arena;
            }
        }
        return null;
    }

    /**
     * Signale un joueur deconnecte qui n'a pas pu revenir a temps : il se
     * reconnectera au milieu d'une map de jeu, il faudra le renvoyer au lobby.
     * Memoire seulement (perdue au redemarrage, sans gravite).
     */
    public void markStranded(UUID id) {
        stranded.add(id);
    }

    /** Vrai (une seule fois) si ce joueur doit etre renvoye au lobby a sa connexion. */
    public boolean consumeStranded(UUID id) {
        return stranded.remove(id);
    }

    /**
     * Nom d'arene autorise : lettres, chiffres, {@code _} et {@code -}, 16
     * caracteres maximum, pour tenir sur un panneau et dans un scoreboard.
     */
    public static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,16}");
    }

    public boolean createArena(String name) {
        if (getArena(name) != null) {
            return false;
        }
        // Defaut : 2 equipes de 4.
        Arena arena = new Arena(main, name);
        arenas.put(name.toLowerCase(), arena);
        save();
        return true;
    }

    public boolean deleteArena(String name) {
        Arena arena = getArena(name);
        if (arena == null) {
            return false;
        }
        arena.reset();
        arenas.remove(name.toLowerCase());
        for (Iterator<PendingLocation> it = pending.iterator(); it.hasNext();) {
            if (it.next().arena.equalsIgnoreCase(name)) {
                it.remove();
            }
        }
        save();
        return true;
    }

    /** Sort un joueur de l'arene ou il se trouve, s'il y en a une. */
    public void leaveArena(Player player, boolean teleportToLobby) {
        Arena arena = getArenaOf(player);
        if (arena != null) {
            arena.leave(player, teleportToLobby);
        }
    }

    /** Remet toutes les arenes a zero, typiquement a l'arret du plugin. */
    public void resetAll() {
        for (Arena arena : arenas.values()) {
            arena.reset();
        }
    }

    // ================= Serialisation =================

    /**
     * Lit une position. Si son monde n'est pas (encore) charge, elle est mise
     * en attente dans {@link #pending} et la methode renvoie {@code null}.
     */
    private Location readLocation(ConfigurationSection section, String arena, String kind,
            String team, String type) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }
        PendingLocation raw = new PendingLocation(arena, kind, team, type, worldName,
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            pending.add(raw);
            return null;
        }
        return raw.toLocation(world);
    }

    private void writeLocation(String path, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        arenasConfig.set(path + ".world", location.getWorld().getName());
        arenasConfig.set(path + ".x", location.getX());
        arenasConfig.set(path + ".y", location.getY());
        arenasConfig.set(path + ".z", location.getZ());
        arenasConfig.set(path + ".yaw", location.getYaw());
        arenasConfig.set(path + ".pitch", location.getPitch());
    }

    /**
     * Une zone ne retient que le NOM de son monde : contrairement a une
     * position, on peut donc la relire meme si le monde n'est pas (encore)
     * charge ; elle redeviendra active des qu'il le sera.
     */
    private Cuboid readCuboid(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }
        return new Cuboid(worldName,
                section.getInt("x1"), section.getInt("y1"), section.getInt("z1"),
                section.getInt("x2"), section.getInt("y2"), section.getInt("z2"));
    }

    private void writeCuboid(String path, Cuboid cuboid) {
        if (cuboid == null) {
            return;
        }
        arenasConfig.set(path + ".world", cuboid.getWorldName());
        arenasConfig.set(path + ".x1", cuboid.getMinX());
        arenasConfig.set(path + ".y1", cuboid.getMinY());
        arenasConfig.set(path + ".z1", cuboid.getMinZ());
        arenasConfig.set(path + ".x2", cuboid.getMaxX());
        arenasConfig.set(path + ".y2", cuboid.getMaxY());
        arenasConfig.set(path + ".z2", cuboid.getMaxZ());
    }

    /** Position brute (nom du monde + coordonnees) en attente de son monde. */
    private static final class PendingLocation {
        static final String LOBBY = "lobby";
        static final String SPAWN = "spawn";
        static final String GENERATOR = "generator";

        final String arena;
        final String kind;
        final String team;
        final String type;
        final String world;
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;

        PendingLocation(String arena, String kind, String team, String type, String world,
                double x, double y, double z, float yaw, float pitch) {
            this.arena = arena;
            this.kind = kind;
            this.team = team;
            this.type = type;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        Location toLocation(World w) {
            return new Location(w, x, y, z, yaw, pitch);
        }

        void write(FileConfiguration config, String path) {
            config.set(path + ".world", world);
            config.set(path + ".x", x);
            config.set(path + ".y", y);
            config.set(path + ".z", z);
            config.set(path + ".yaw", yaw);
            config.set(path + ".pitch", pitch);
        }
    }
}
