package fr.laugh.thetower;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import fr.laugh.thetower.compat.Compat;
import fr.laugh.thetower.compat.Sounds;
import fr.laugh.thetower.compat.TTMaterial;
import fr.laugh.thetower.compat.Titles;
import fr.laugh.thetower.game.GameStats;
import fr.laugh.thetower.game.Generator;
import fr.laugh.thetower.game.GeneratorSpot;
import fr.laugh.thetower.game.GeneratorType;
import fr.laugh.thetower.game.KitDefinition;
import fr.laugh.thetower.game.PlayerSnapshot;
import fr.laugh.thetower.lang.Messages;
import fr.laugh.thetower.lobby.LobbyItems;
import fr.laugh.thetower.map.MapTracker;
import fr.laugh.thetower.quest.QuestType;
import fr.laugh.thetower.region.Cuboid;
import fr.laugh.thetower.stats.Elo;
import fr.laugh.thetower.stats.PlayerStats;
import fr.laugh.thetower.task.Countdown;
import fr.laugh.thetower.task.GameCycle;

/**
 * Une arene TheTower : sa configuration, son etat et le deroulement de sa partie.
 *
 * <p>Regle du jeu (celle de BadBlock) : chaque equipe a une base avec une
 * <b>piscine</b>. Entrer dans la piscine d'une equipe adverse marque un point
 * pour son equipe et renvoie a sa base. La premiere equipe a
 * {@link #getPointsToWin()} points gagne. Personne n'est jamais elimine : on
 * reapparait a sa base a chaque mort, avec le kit de depart.
 *
 * <p>Ajout par rapport a BadBlock, ou une partie pouvait durer indefiniment :
 * une duree maximale. A son terme, l'equipe qui mene gagne ; en cas d'egalite,
 * <b>mort subite</b> - le prochain point l'emporte.
 *
 * <p>Les joueurs sont references par leur {@link UUID} et non par des objets
 * {@link Player} : garder une reference sur un Player deconnecte empeche le
 * ramasse-miettes de liberer son entite, ce qui fuit a chaque partie.
 */
public class Arena {

    /** Les quatre equipes possibles, dans l'ordre d'attribution. */
    public static final String[] TEAM_NAMES = { "Rouge", "Bleu", "Jaune", "Vert" };

    /** Fenetre de temps (ms) dans laquelle des kills successifs forment une serie. */
    private static final long STREAK_WINDOW_MS = 8000L;

    /**
     * Un joueur frappe par un adversaire il y a moins de ce delai (ms) est "en
     * combat" : s'il se deconnecte, c'est compte comme une mort.
     */
    private static final long COMBAT_TAG_MS = 10000L;

    /**
     * Marge autour des elements configures pour delimiter la "zone de jeu"
     * (voir {@link #getArea()}) : assez pour couvrir les ponts et le centre
     * de la map entre les tours.
     */
    private static final int AREA_MARGIN = 48;

    private final Main main;

    // ----- Configuration persistante -----

    private final String name;
    private int minPlayers = 2;
    private int maxPlayers = 8;
    private int teamCount = 2;
    private int playersPerTeam = 4;
    private int pointsToWin;
    /** Duree maximale de la partie, en secondes. */
    private int duration;
    private boolean allowBows = true;
    private Location lobby;
    private final Map<String, Location> spawns = new LinkedHashMap<String, Location>();
    private final Map<String, Cuboid> pools = new LinkedHashMap<String, Cuboid>();
    // Zone ou personne ne pose ni ne casse (le spawn de l'equipe) : equivalent
    // de la "spawnzone" de BadBlock. Facultative.
    private final Map<String, Cuboid> spawnZones = new LinkedHashMap<String, Cuboid>();
    // Zone dont seuls les membres de l'equipe ouvrent les coffres : la
    // "teamzone" de BadBlock. Facultative.
    private final Map<String, Cuboid> chestZones = new LinkedHashMap<String, Cuboid>();
    private final List<GeneratorSpot> generators = new ArrayList<GeneratorSpot>();

    // ----- Etat de la partie en cours -----

    private State state = State.WAITING;
    private final List<UUID> players = new ArrayList<UUID>();
    private final Map<UUID, String> teamOf = new HashMap<UUID, String>();
    private final Set<UUID> startVotes = new LinkedHashSet<UUID>();
    private final Map<String, Integer> scores = new LinkedHashMap<String, Integer>();
    private final Map<UUID, GameStats> gameStats = new LinkedHashMap<UUID, GameStats>();
    private final Map<UUID, Long> nextMark = new HashMap<UUID, Long>();
    // Joueurs deconnectes en cours de partie qui peuvent encore revenir : leur
    // equipe (teamOf) est conservee, avec l'echeance de leur retour (ms).
    private final Map<UUID, Long> reconnectDeadlines = new LinkedHashMap<UUID, Long>();
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<UUID, PlayerSnapshot>();
    private final MapTracker mapTracker = new MapTracker();
    private final List<Generator> runningGenerators = new ArrayList<Generator>();
    private BukkitTask countdownTask;
    private BukkitTask cycleTask;
    private int timeLeft;
    private boolean overtime;
    /** Zone de jeu figee au lancement (voir {@link #getArea()}). */
    private Cuboid area;

    public Arena(Main main, String name) {
        this.main = main;
        this.name = name;
        this.pointsToWin = main.getDefaultPointsToWin();
        this.duration = main.getGameDuration();
    }

    // ================= Configuration =================

    public String getName() {
        return name;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public int getTeamCount() {
        return teamCount;
    }

    public void setTeamCount(int teamCount) {
        this.teamCount = teamCount;
    }

    public int getPlayersPerTeam() {
        return playersPerTeam;
    }

    public void setPlayersPerTeam(int playersPerTeam) {
        this.playersPerTeam = playersPerTeam;
    }

    /** Points a atteindre pour gagner (10 sur BadBlock). */
    public int getPointsToWin() {
        return pointsToWin;
    }

    public void setPointsToWin(int pointsToWin) {
        this.pointsToWin = Math.max(1, pointsToWin);
    }

    /** Duree maximale d'une partie, en secondes. */
    public int getDuration() {
        return duration;
    }

    public void setDuration(int seconds) {
        this.duration = Math.max(60, seconds);
    }

    /** Faux si l'arene interdit arcs et fleches (ni kit, ni fabrication). */
    public boolean isAllowBows() {
        return allowBows;
    }

    public void setAllowBows(boolean allowBows) {
        this.allowBows = allowBows;
    }

    public Location getLobby() {
        return lobby;
    }

    public void setLobby(Location lobby) {
        this.lobby = lobby;
    }

    public Map<String, Location> getSpawns() {
        return Collections.unmodifiableMap(spawns);
    }

    public void setSpawn(String team, Location location) {
        spawns.put(team, location);
    }

    public Map<String, Cuboid> getPools() {
        return Collections.unmodifiableMap(pools);
    }

    public void setPool(String team, Cuboid pool) {
        putOrRemove(pools, team, pool);
    }

    public Map<String, Cuboid> getSpawnZones() {
        return Collections.unmodifiableMap(spawnZones);
    }

    public void setSpawnZone(String team, Cuboid zone) {
        putOrRemove(spawnZones, team, zone);
    }

    public Map<String, Cuboid> getChestZones() {
        return Collections.unmodifiableMap(chestZones);
    }

    public void setChestZone(String team, Cuboid zone) {
        putOrRemove(chestZones, team, zone);
    }

    public List<GeneratorSpot> getGenerators() {
        return Collections.unmodifiableList(generators);
    }

    public void addGenerator(String type, Location location) {
        generators.add(new GeneratorSpot(type, location));
    }

    /**
     * Retire les generateurs d'un type, ou tous si {@code type} vaut {@code null}.
     *
     * @return le nombre de generateurs retires
     */
    public int clearGenerators(String type) {
        int removed = 0;
        Iterator<GeneratorSpot> it = generators.iterator();
        while (it.hasNext()) {
            GeneratorSpot spot = it.next();
            if (type == null || spot.getType().equalsIgnoreCase(type)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Nombre de generateurs d'un type donne. */
    public int countGenerators(String type) {
        int count = 0;
        for (GeneratorSpot spot : generators) {
            if (spot.getType().equalsIgnoreCase(type)) {
                count++;
            }
        }
        return count;
    }

    /** Vrai si cette equipe fait partie des {@code teamCount} premieres. */
    public boolean isTeamActive(String teamName) {
        return activeTeams().contains(teamName);
    }

    /** Les equipes utilisees par le format courant, dans l'ordre. */
    public List<String> activeTeams() {
        int count = Math.max(0, Math.min(teamCount, TEAM_NAMES.length));
        return new ArrayList<String>(Arrays.asList(TEAM_NAMES).subList(0, count));
    }

    /**
     * Verifie que l'arene est jouable.
     *
     * @return la raison du refus, ou {@code null} si l'arene est valide
     */
    public String validate() {
        if (lobby == null) {
            return Messages.tr("arena.validate.no_lobby", "arena", name);
        }
        if (teamCount < 2 || teamCount > 4) {
            return Messages.tr("arena.validate.teams_range");
        }
        if (playersPerTeam <= 0) {
            return Messages.tr("arena.validate.per_team_positive");
        }
        for (String team : activeTeams()) {
            if (!spawns.containsKey(team)) {
                return Messages.tr("arena.validate.missing_spawn", "team", team, "arena", name);
            }
            if (!pools.containsKey(team)) {
                return Messages.tr("arena.validate.missing_pool", "team", team, "arena", name);
            }
        }
        if (minPlayers < 2) {
            return Messages.tr("arena.validate.min_players");
        }
        if (maxPlayers < minPlayers) {
            return Messages.tr("arena.validate.max_below_min");
        }
        if (maxPlayers > teamCount * playersPerTeam) {
            return Messages.tr("arena.validate.max_exceeds",
                    "max", maxPlayers, "teams", teamCount, "per", playersPerTeam,
                    "capacity", (teamCount * playersPerTeam));
        }
        return null;
    }

    public boolean isConfigured() {
        return validate() == null;
    }

    // ================= Zones =================

    /** Vrai si l'on ne peut ni poser ni casser ici : zone de spawn ou piscine. */
    public boolean isProtected(Block block) {
        for (String team : activeTeams()) {
            Cuboid zone = spawnZones.get(team);
            if (zone != null && zone.contains(block)) {
                return true;
            }
            Cuboid pool = pools.get(team);
            if (pool != null && pool.contains(block)) {
                return true;
            }
        }
        return false;
    }

    /** Equipe proprietaire de la zone de coffres contenant ce bloc, ou {@code null}. */
    public String chestZoneOwner(Block block) {
        for (String team : activeTeams()) {
            Cuboid zone = chestZones.get(team);
            if (zone != null && zone.contains(block)) {
                return team;
            }
        }
        return null;
    }

    /**
     * Zone de jeu : la plus petite boite contenant spawns, piscines, zones et
     * generateurs, agrandie d'une marge. Sert a rattacher a cette arene ce qui
     * arrive sans joueur identifiable (explosions, feu, chutes de sable) et a
     * nettoyer les items au sol entre deux parties.
     *
     * <p>Figee au lancement ; hors partie, recalculee a la demande.
     *
     * @return la zone, ou {@code null} si rien n'est encore configure
     */
    public Cuboid getArea() {
        return area != null ? area : computeArea();
    }

    private Cuboid computeArea() {
        Cuboid box = null;
        for (Location spawn : spawns.values()) {
            box = extend(box, Cuboid.of(spawn, spawn));
        }
        for (Cuboid pool : pools.values()) {
            box = extend(box, pool);
        }
        for (Cuboid zone : spawnZones.values()) {
            box = extend(box, zone);
        }
        for (Cuboid zone : chestZones.values()) {
            box = extend(box, zone);
        }
        for (GeneratorSpot spot : generators) {
            Location loc = spot.getLocation();
            box = extend(box, Cuboid.of(loc, loc));
        }
        return box == null ? null : box.expand(AREA_MARGIN, AREA_MARGIN);
    }

    private static Cuboid extend(Cuboid box, Cuboid other) {
        if (other == null) {
            return box;
        }
        return box == null ? other : box.union(other);
    }

    // ================= Etat et joueurs =================

    public State getState() {
        return state;
    }

    public boolean isState(State other) {
        return this.state == other;
    }

    /** Vrai pendant la partie proprement dite. */
    public boolean isRunning() {
        return state == State.PLAYING;
    }

    /** Vrai si un joueur peut encore rejoindre : en attente, configuree, non pleine. */
    public boolean isJoinable() {
        return (state == State.WAITING || state == State.STARTING)
                && isConfigured() && !isFull();
    }

    public List<UUID> getPlayerIds() {
        return Collections.unmodifiableList(players);
    }

    public boolean contains(Player player) {
        return players.contains(player.getUniqueId());
    }

    /** Vrai si le joueur joue dans cette partie en cours (il a une equipe). */
    public boolean isParticipant(Player player) {
        return isRunning() && teamOf.containsKey(player.getUniqueId());
    }

    public int getPlayerCount() {
        return players.size();
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public String getTeam(Player player) {
        return teamOf.get(player.getUniqueId());
    }

    /** Score actuel d'une equipe (0 hors partie). */
    public int getScore(String team) {
        Integer score = scores.get(team);
        return score == null ? 0 : score.intValue();
    }

    /** Compteurs de la partie en cours pour ce joueur, ou {@code null}. */
    public GameStats getGameStats(Player player) {
        return gameStats.get(player.getUniqueId());
    }

    /** Suivi des blocs modifies, pour la regeneration de la map. */
    public MapTracker getMapTracker() {
        return mapTracker;
    }

    /** Secondes restantes avant la fin du temps reglementaire. */
    public int getTimeLeft() {
        return timeLeft;
    }

    /** Vrai en mort subite : temps ecoule sur une egalite, le prochain point gagne. */
    public boolean isOvertime() {
        return overtime;
    }

    /** Joueurs de l'arene actuellement connectes. */
    public List<Player> onlinePlayers() {
        List<Player> online = new ArrayList<Player>();
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        return online;
    }

    /** Envoie un message aux seuls joueurs de cette arene. */
    public void broadcast(String message) {
        String prefixed = Main.PREFIX + message;
        for (Player player : onlinePlayers()) {
            player.sendMessage(prefixed);
        }
    }

    // ================= Entree et sortie =================

    /**
     * Fait entrer un joueur dans l'arene.
     *
     * @return la raison du refus, ou {@code null} en cas de succes
     */
    public String join(Player player) {
        if (players.contains(player.getUniqueId())) {
            return Messages.tr("arena.join.already_in");
        }
        if (state != State.WAITING && state != State.STARTING) {
            return Messages.tr("arena.join.in_progress");
        }
        if (isFull()) {
            return Messages.tr("arena.join.full", "current", players.size(), "max", maxPlayers);
        }
        String problem = validate();
        if (problem != null) {
            return Messages.tr("arena.join.misconfigured", "problem", problem);
        }

        players.add(player.getUniqueId());
        Compat.resetPlayer(player, GameMode.ADVENTURE);
        player.teleport(lobby);
        LobbyItems.give(player);

        broadcast(Messages.tr("arena.broadcast.joined", "player", player.getName(),
                "current", players.size(), "max", maxPlayers));

        if (state == State.WAITING && players.size() >= minPlayers) {
            startCountdown(main.getCountdown());
        }
        return null;
    }

    /** Fait sortir un joueur, volontairement ou par deconnexion. */
    public void leave(Player player, boolean teleportToLobby) {
        UUID id = player.getUniqueId();
        if (!players.remove(id)) {
            return;
        }
        startVotes.remove(id);
        nextMark.remove(id);
        String team = teamOf.remove(id);

        if (teleportToLobby && player.isOnline()) {
            Compat.resetPlayer(player, GameMode.ADVENTURE);
            main.getScoreboardManager().clear(player);
            main.returnToLobby(player);
        }

        broadcast(Messages.tr("arena.broadcast.left", "player", player.getName(),
                "current", players.size(), "max", maxPlayers));

        if (state == State.STARTING && players.size() < minPlayers) {
            cancelCountdown();
            state = State.WAITING;
            broadcast(Messages.tr("arena.countdown_cancelled"));
        } else if (isRunning()) {
            // Derniere personne d'une equipe partie : l'equipe abandonne (BadBlock
            // annoncait deja "l'equipe X a perdu" dans ce cas).
            if (team != null && !teamsInPlay().contains(team)) {
                broadcast(Messages.tr("arena.team_forfeit", "color", colorOf(team), "team", team));
            }
            checkWin();
        } else {
            checkVoteStart();
        }
    }

    // ================= Reconnexion en cours de partie =================

    /**
     * Un participant se deconnecte (crash, coupure) pendant la partie : il garde
     * sa place dans son equipe et pourra revenir avant l'echeance
     * ({@code reconnection.timeout}). A appeler pendant PlayerQuitEvent, tant que
     * le joueur est encore la (inventaire et stats accessibles).
     *
     * <p>Anti-abus : s'il a ete frappe par un adversaire dans les 10 dernieres
     * secondes, la deconnexion compte comme une mort (objets au sol, kill pour
     * l'adversaire) et il reviendra avec le kit de depart. Sinon son
     * equipement est mis de cote et lui sera rendu tel quel.
     */
    public void disconnect(Player player) {
        UUID id = player.getUniqueId();
        if (!isParticipant(player)) {
            leave(player, false);
            return;
        }
        long now = System.currentTimeMillis();
        GameStats stats = getGameStats(player);
        UUID attackerId = stats == null ? null : stats.recentAttacker(now, COMBAT_TAG_MS);
        if (attackerId != null) {
            broadcast(Messages.tr("arena.broadcast.combat_log", "player", player.getName()));
            dropInventory(player);
            handleDeath(player, Bukkit.getPlayer(attackerId));
        } else {
            snapshots.put(id, PlayerSnapshot.capture(player));
        }
        // Vide le vrai inventaire : si le joueur ne revient jamais, rien de la
        // partie ne doit le suivre ailleurs sur le serveur.
        Compat.resetPlayer(player, GameMode.ADVENTURE);

        players.remove(id);
        startVotes.remove(id);
        nextMark.remove(id);
        long timeout = main.getReconnectTimeoutMillis();
        reconnectDeadlines.put(id, Long.valueOf(timeout <= 0 ? Long.MAX_VALUE : now + timeout));

        broadcast(timeout <= 0
                ? Messages.tr("arena.broadcast.disconnected_nolimit", "player", player.getName())
                : Messages.tr("arena.broadcast.disconnected", "player", player.getName(),
                        "time", GameCycle.clock((int) (timeout / 1000L))));
        // Plus personne de connecte : inutile de faire tourner la partie a vide.
        checkWin();
    }

    /** Vrai si ce joueur deconnecte peut encore reprendre sa place dans cette partie. */
    public boolean canRejoin(UUID id) {
        Long deadline = reconnectDeadlines.get(id);
        return isRunning() && deadline != null && deadline.longValue() > System.currentTimeMillis();
    }

    /**
     * Le joueur revient : il reprend sa place dans son equipe, a sa base, avec
     * l'equipement qu'il avait en partant (ou le kit s'il etait parti en combat).
     */
    public void rejoin(Player player) {
        UUID id = player.getUniqueId();
        if (!canRejoin(id)) {
            return;
        }
        reconnectDeadlines.remove(id);
        players.add(id);

        PlayerSnapshot snapshot = snapshots.remove(id);
        if (snapshot == null) {
            spawnPlayer(player);
        } else {
            String team = getTeam(player);
            Compat.resetPlayer(player, GameMode.SURVIVAL);
            Location spawn = team == null ? null : spawns.get(team);
            if (spawn != null && spawn.getWorld() != null) {
                player.teleport(spawn);
            }
            snapshot.restore(player);
        }

        String team = getTeam(player);
        player.sendMessage(Main.PREFIX + Messages.tr("arena.rejoined_self",
                "color", colorOf(team), "team", team));
        broadcast(Messages.tr("arena.broadcast.rejoined", "color", colorOf(team), "player", player.getName()));
    }

    /**
     * Appele chaque seconde par {@link GameCycle} : les deconnectes dont le delai
     * est ecoule perdent definitivement leur place ; une equipe qui n'a plus
     * personne abandonne.
     */
    public void checkDisconnected() {
        if (reconnectDeadlines.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean expired = false;
        Iterator<Map.Entry<UUID, Long>> it = reconnectDeadlines.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (entry.getValue().longValue() > now) {
                continue;
            }
            UUID id = entry.getKey();
            it.remove();
            snapshots.remove(id);
            main.getArenaManager().markStranded(id);
            String team = teamOf.remove(id);
            GameStats stats = gameStats.get(id);
            broadcast(Messages.tr("arena.broadcast.reconnect_expired",
                    "player", stats == null ? "?" : stats.getName()));
            if (team != null && !teamsInPlay().contains(team)) {
                broadcast(Messages.tr("arena.team_forfeit", "color", colorOf(team), "team", team));
            }
            expired = true;
        }
        if (expired) {
            checkWin();
        }
    }

    /** Fait tomber l'inventaire d'un joueur comme a une mort (hors armure du kit). */
    private void dropInventory(Player player) {
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            return;
        }
        List<ItemStack> items = new ArrayList<ItemStack>();
        ItemStack[] contents = player.getInventory().getContents();
        items.addAll(Arrays.asList(contents));
        // 1.8 : getContents() = les 36 cases, l'armure est a part. Depuis la 1.9
        // il inclut deja armure et seconde main (41 cases) : les rajouter ferait
        // tomber l'armure en double.
        if (contents.length <= 36) {
            items.addAll(Arrays.asList(player.getInventory().getArmorContents()));
        }
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR
                    || TTMaterial.isLeatherArmor(item.getType())) {
                continue;
            }
            location.getWorld().dropItemNaturally(location, item);
        }
    }

    // ================= Compte a rebours et vote =================

    private void startCountdown(int seconds) {
        state = State.STARTING;
        cancelCountdown();
        countdownTask = new Countdown(this, seconds).runTaskTimer(main, 0L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void cancelCycle() {
        if (cycleTask != null) {
            cycleTask.cancel();
            cycleTask = null;
        }
    }

    /** Un joueur clique le papier pour voter le lancement anticipe. */
    public void vote(Player player) {
        if (state != State.WAITING && state != State.STARTING) {
            return;
        }
        if (!players.contains(player.getUniqueId())) {
            return;
        }
        if (!startVotes.add(player.getUniqueId())) {
            player.sendMessage(Main.PREFIX + Messages.tr("arena.already_voted"));
            return;
        }
        broadcast(Messages.tr("arena.broadcast.vote", "player", player.getName(),
                "votes", startVotes.size(), "needed", votesNeeded()));
        checkVoteStart();
    }

    /** Nombre de votes requis : majorite stricte des joueurs connectes. */
    private int votesNeeded() {
        return onlinePlayers().size() / 2 + 1;
    }

    private void checkVoteStart() {
        if (state != State.WAITING && state != State.STARTING) {
            return;
        }
        if (onlinePlayers().size() < minPlayers) {
            return;
        }
        if (startVotes.size() >= votesNeeded()) {
            broadcast(Messages.tr("arena.vote_reached"));
            // 3 secondes pour laisser jouer le 3-2-1 a l'ecran.
            startCountdown(3);
        }
    }

    // ================= Deroulement de la partie =================

    /**
     * Lance reellement la partie : repartition en equipes, teleportation aux
     * bases, kits, generateurs et cycle de jeu.
     */
    public void start() {
        cancelCountdown();
        state = State.PLAYING;
        mapTracker.clear();
        startVotes.clear();
        scores.clear();
        gameStats.clear();
        nextMark.clear();
        reconnectDeadlines.clear();
        snapshots.clear();
        overtime = false;
        timeLeft = duration;
        area = computeArea();
        // Items oublies par une partie interrompue brutalement (crash) : on
        // repart d'une map propre.
        clearLooseEntities();

        List<String> teams = activeTeams();
        for (String team : teams) {
            scores.put(team, Integer.valueOf(0));
        }

        List<Player> participants = onlinePlayers();
        teamOf.clear();

        // Repartition equilibree : les joueurs sont distribues un par un sur les
        // equipes, dans l'ordre d'arrivee.
        for (int i = 0; i < participants.size(); i++) {
            Player player = participants.get(i);
            String team = teams.get(i % teams.size());
            teamOf.put(player.getUniqueId(), team);
            GameStats stats = new GameStats(player.getName());
            stats.setKitsAtStart(main.getKitManager().unlockedIds(player));
            gameStats.put(player.getUniqueId(), stats);
            main.getStatsManager().addGame(player);

            spawnPlayer(player);
            Titles.send(player, Messages.tr("title.game_start"),
                    Messages.tr("title.game_start_sub", "goal", pointsToWin));
            Sounds.gameStart(player);
            player.sendMessage(Main.PREFIX + Messages.tr("arena.your_team",
                    "color", colorOf(team), "team", team));
        }

        broadcast(Messages.tr("arena.game_begin", "goal", pointsToWin, "minutes", duration / 60));

        startGenerators();
        cancelCycle();
        cycleTask = new GameCycle(this).runTaskTimer(main, 20L, 20L);
    }

    /**
     * (Re)place un participant a la base de son equipe avec le kit de depart :
     * au lancement et a chaque reapparition.
     */
    public void spawnPlayer(Player player) {
        String team = getTeam(player);
        Compat.resetPlayer(player, GameMode.SURVIVAL);
        Location spawn = team == null ? null : spawns.get(team);
        if (spawn != null && spawn.getWorld() != null) {
            player.teleport(spawn);
        }
        main.getKitManager().give(player, armorColorOf(team), allowBows);
    }

    /** Point de reapparition d'un joueur : sa base en partie, le lobby sinon. */
    public Location respawnLocation(Player player) {
        String team = getTeam(player);
        Location spawn = team == null ? null : spawns.get(team);
        if (isRunning() && spawn != null && spawn.getWorld() != null) {
            return spawn;
        }
        return lobby;
    }

    private void startGenerators() {
        stopGenerators();
        Map<String, GeneratorType> types = main.getGeneratorTypes();
        for (GeneratorSpot spot : generators) {
            GeneratorType type = types.get(spot.getType());
            if (type == null) {
                main.getLogger().warning("Arene " + name + " : type de generateur inconnu '"
                        + spot.getType() + "' (absent de config.yml), ignore.");
                continue;
            }
            Generator generator = new Generator(type, spot.getLocation());
            generator.start(main);
            runningGenerators.add(generator);
        }
    }

    private void stopGenerators() {
        for (Generator generator : runningGenerators) {
            generator.stop();
        }
        runningGenerators.clear();
    }

    /**
     * Appele a chaque deplacement d'un participant (changement de bloc) : s'il
     * vient d'entrer dans la piscine d'une equipe adverse, son equipe marque.
     */
    public void tryMark(Player player, Location to) {
        if (!isRunning()) {
            return;
        }
        String team = getTeam(player);
        if (team == null) {
            return;
        }
        for (String other : activeTeams()) {
            if (other.equals(team)) {
                continue;
            }
            Cuboid pool = pools.get(other);
            if (pool != null && pool.contains(to)) {
                mark(player, team);
                return;
            }
        }
    }

    private void mark(Player player, String team) {
        // Anti double-declenchement : le joueur est teleporte des qu'il marque,
        // mais un second mouvement peut arriver avant que la teleportation ne
        // soit effective cote serveur.
        long now = System.currentTimeMillis();
        Long next = nextMark.get(player.getUniqueId());
        if (next != null && next.longValue() > now) {
            return;
        }
        nextMark.put(player.getUniqueId(), Long.valueOf(now + main.getMarkCooldownMillis()));

        int score = getScore(team) + 1;
        scores.put(team, Integer.valueOf(score));

        GameStats stats = getGameStats(player);
        if (stats != null) {
            stats.addPoint();
        }
        main.getStatsManager().addPoint(player);
        main.getQuestManager().addProgress(player, QuestType.SCORE_POINTS, 1);

        // Retour a la base, soigne, comme sur BadBlock. On descend d'abord de
        // toute monture : un joueur en selle ne peut pas etre teleporte.
        player.leaveVehicle();
        Location spawn = spawns.get(team);
        if (spawn != null && spawn.getWorld() != null) {
            player.teleport(spawn);
        }
        Compat.healFully(player);
        player.setFireTicks(0);
        player.setFallDistance(0f);

        String title = Messages.tr("title.mark", "color", colorOf(team), "score", score, "goal", pointsToWin);
        String subtitle = Messages.tr("title.mark_sub", "color", colorOf(team), "player", player.getName());
        for (Player viewer : onlinePlayers()) {
            Titles.send(viewer, title, subtitle);
            if (team.equals(getTeam(viewer))) {
                Sounds.markAlly(viewer);
            } else {
                Sounds.markEnemy(viewer);
            }
        }
        broadcast(Messages.tr("game.mark", "color", colorOf(team), "player", player.getName(),
                "team", team, "score", score, "goal", pointsToWin));

        if (score >= pointsToWin) {
            end(team);
        } else if (overtime && team.equals(uniqueLeader())) {
            // Mort subite : ce point vient de rompre l'egalite.
            end(team);
        }
    }

    /**
     * Mort d'un participant : compteurs, message a l'arene (et pas a tout le
     * serveur), series de kills.
     *
     * @param killer le joueur qui a porte le coup fatal, ou {@code null}
     */
    public void handleDeath(Player victim, Player killer) {
        long now = System.currentTimeMillis();
        GameStats victimStats = getGameStats(victim);
        int victimStreak = victimStats == null ? 0 : victimStats.streak(now, STREAK_WINDOW_MS);
        if (victimStats != null) {
            victimStats.addDeath();
            victimStats.resetStreak();
            victimStats.clearAttacker();
        }
        main.getStatsManager().addDeath(victim);

        String victimTeam = getTeam(victim);
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId()) || !isParticipant(killer)) {
            broadcast(Messages.tr("game.died", "color", colorOf(victimTeam), "player", victim.getName()));
            return;
        }

        String killerTeam = getTeam(killer);
        broadcast(Messages.tr("game.killed_by",
                "color", colorOf(victimTeam), "player", victim.getName(),
                "kcolor", colorOf(killerTeam), "killer", killer.getName()));
        main.getStatsManager().addKill(killer);
        main.getQuestManager().addProgress(killer, QuestType.GET_KILLS, 1);

        if (victimStreak >= 3) {
            broadcast(Messages.tr("game.streak.stopped", "killer", killer.getName(),
                    "player", victim.getName(), "count", victimStreak));
        }

        GameStats killerStats = getGameStats(killer);
        if (killerStats == null) {
            return;
        }
        int streak = killerStats.registerKill(now, STREAK_WINDOW_MS);
        if (streak >= 2) {
            String key = streak == 2 ? "game.streak.double"
                    : streak == 3 ? "game.streak.triple"
                    : streak == 4 ? "game.streak.quadruple"
                    : "game.streak.many";
            broadcast(Messages.tr(key, "player", killer.getName(), "count", streak));
            for (Player viewer : onlinePlayers()) {
                Sounds.killStreak(viewer, streak);
            }
        }
    }

    /**
     * Un participant en frappe un autre : degats cumules pour le resume de fin,
     * et marquage "en combat" de la victime (anti deconnexion en combat).
     */
    public void recordHit(Player attacker, Player victim, double amount) {
        GameStats attackerStats = getGameStats(attacker);
        if (attackerStats != null) {
            attackerStats.addDamage(amount);
        }
        GameStats victimStats = getGameStats(victim);
        if (victimStats != null && !attacker.getUniqueId().equals(victim.getUniqueId())) {
            victimStats.tagAttacker(attacker.getUniqueId(), System.currentTimeMillis());
        }
    }

    /** Une seconde de moins ; renvoie le temps restant. Appele par {@link GameCycle}. */
    public int tickTimer() {
        if (timeLeft > 0) {
            timeLeft--;
        }
        return timeLeft;
    }

    /**
     * Fin du temps reglementaire : l'equipe qui mene gagne, sinon mort subite.
     */
    public void timeUp() {
        if (!isRunning() || overtime) {
            return;
        }
        String leader = uniqueLeader();
        if (leader != null) {
            broadcast(Messages.tr("game.time_up_winner", "color", colorOf(leader), "team", leader));
            end(leader);
            return;
        }
        overtime = true;
        broadcast(Messages.tr("game.overtime"));
        for (Player player : onlinePlayers()) {
            Titles.send(player, Messages.tr("title.overtime"), Messages.tr("title.overtime_sub"));
        }
    }

    /** Equipe seule en tete parmi celles encore en jeu, ou {@code null} (egalite). */
    private String uniqueLeader() {
        String leader = null;
        int best = -1;
        boolean tie = false;
        for (String team : teamsInPlay()) {
            int score = getScore(team);
            if (score > best) {
                best = score;
                leader = team;
                tie = false;
            } else if (score == best) {
                tie = true;
            }
        }
        return tie ? null : leader;
    }

    /** Equipes qui ont encore au moins un joueur dans la partie. */
    private Set<String> teamsInPlay() {
        return new LinkedHashSet<String>(teamOf.values());
    }

    /**
     * Termine la partie des qu'il ne reste qu'une equipe, ou aucune. Une equipe
     * dont les joueurs sont deconnectes mais peuvent encore revenir reste en
     * jeu ; en revanche, si plus AUCUN joueur n'est connecte, la partie s'arrete
     * sans vainqueur (elle tournerait a vide, voire sans fin en mort subite).
     */
    public void checkWin() {
        if (!isRunning()) {
            return;
        }
        if (players.isEmpty()) {
            end(null);
            return;
        }
        Set<String> remaining = teamsInPlay();
        if (remaining.size() == 1) {
            end(remaining.iterator().next());
        } else if (remaining.isEmpty()) {
            end(null);
        }
    }

    /** Cloture la partie et programme la remise a zero de l'arene. */
    public void end(String winningTeam) {
        if (state == State.FINISH) {
            return;
        }
        state = State.FINISH;
        cancelCountdown();
        cancelCycle();
        stopGenerators();

        if (winningTeam == null) {
            broadcast(Messages.tr("arena.end_no_winner"));
        } else {
            broadcast(Messages.tr("arena.end_winner", "color", colorOf(winningTeam), "team", winningTeam));
            spawnVictoryFireworks(winningTeam);
        }

        List<Player> participants = onlinePlayers();
        for (Player player : participants) {
            String team = getTeam(player);
            String title;
            if (winningTeam == null) {
                title = Messages.tr("title.draw");
            } else if (winningTeam.equals(team)) {
                title = Messages.tr("title.victory");
            } else {
                title = Messages.tr("title.defeat");
            }
            String subtitle = winningTeam == null ? ""
                    : Messages.tr("title.winner_sub", "color", colorOf(winningTeam), "team", winningTeam);
            Titles.send(player, title, subtitle);
            Sounds.gameEnd(player);
        }

        // ELO : calcule sur les notes d'AVANT la partie. Vide (donc sans effet)
        // si la partie a ete interrompue sans vainqueur.
        Map<String, Integer> ratingDeltas = winningTeam == null
                ? Collections.<String, Integer>emptyMap()
                : Elo.deltas(winningTeam, teamRatings(participants));

        for (Player player : participants) {
            String team = getTeam(player);
            if (winningTeam != null && team != null) {
                if (winningTeam.equals(team)) {
                    main.getStatsManager().addWin(player);
                    main.getQuestManager().addProgress(player, QuestType.WIN_GAMES, 1);
                } else {
                    main.getStatsManager().addLoss(player);
                }
                Integer delta = ratingDeltas.get(team);
                if (delta != null) {
                    main.getStatsManager().addRating(player, delta.intValue());
                }
            }
            main.getStatsManager().save(player.getUniqueId());
            announceUnlockedKits(player);
        }

        sendSummary();
        main.getDiscordNotifier().announceEnd(this, winningTeam, participants.size());

        new BukkitRunnable() {
            @Override
            public void run() {
                reset();
            }
        }.runTaskLater(main, 20L * main.getEndDelay());
    }

    /**
     * Kits debloques pendant la partie (parties jouees, victoire, kills, points
     * marques...) : on previent le joueur, qui pourra les choisir des la
     * prochaine. Compare a la liste figee au lancement.
     */
    private void announceUnlockedKits(Player player) {
        GameStats stats = getGameStats(player);
        if (stats == null) {
            return;
        }
        for (KitDefinition kit : main.getKitManager().getKits()) {
            if (!stats.getKitsAtStart().contains(kit.getId())
                    && main.getKitManager().isUnlocked(player, kit)) {
                player.sendMessage(Main.PREFIX + Messages.tr("kits.unlocked", "kit", kit.getDisplayName()));
            }
        }
    }

    /**
     * Resume de fin de partie, repris de BadBlock : meilleur marqueur, plus de
     * kills, plus de degats infliges.
     */
    private void sendSummary() {
        Collection<GameStats> all = gameStats.values();
        if (all.isEmpty()) {
            return;
        }
        GameStats topPoints = null;
        GameStats topKills = null;
        GameStats topDamage = null;
        for (GameStats stats : all) {
            if (topPoints == null || stats.getPoints() > topPoints.getPoints()) {
                topPoints = stats;
            }
            if (topKills == null || stats.getKills() > topKills.getKills()) {
                topKills = stats;
            }
            if (topDamage == null || stats.getDamage() > topDamage.getDamage()) {
                topDamage = stats;
            }
        }
        broadcast(Messages.tr("summary.header"));
        broadcast(topPoints.getPoints() > 0
                ? Messages.tr("summary.points", "player", topPoints.getName(), "value", topPoints.getPoints())
                : Messages.tr("summary.points_none"));
        broadcast(topKills.getKills() > 0
                ? Messages.tr("summary.kills", "player", topKills.getName(), "value", topKills.getKills())
                : Messages.tr("summary.kills_none"));
        broadcast(topDamage.getDamage() > 0
                ? Messages.tr("summary.damage", "player", topDamage.getName(),
                        "value", String.format(java.util.Locale.US, "%.1f", topDamage.getDamage() / 2d))
                : Messages.tr("summary.damage_none"));
    }

    /** Note moyenne de chaque equipe encore representee, pour le calcul ELO. */
    private Map<String, Integer> teamRatings(List<Player> participants) {
        Map<String, List<Integer>> byTeam = new LinkedHashMap<String, List<Integer>>();
        for (Player player : participants) {
            String team = getTeam(player);
            if (team == null) {
                continue;
            }
            PlayerStats stats = main.getStatsManager().getCached(player.getUniqueId());
            int rating = stats == null ? 1000 : stats.getRating();
            List<Integer> ratings = byTeam.get(team);
            if (ratings == null) {
                ratings = new ArrayList<Integer>();
                byTeam.put(team, ratings);
            }
            ratings.add(Integer.valueOf(rating));
        }
        Map<String, Integer> averages = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, List<Integer>> entry : byTeam.entrySet()) {
            int sum = 0;
            for (Integer rating : entry.getValue()) {
                sum += rating.intValue();
            }
            averages.put(entry.getKey(), Integer.valueOf(sum / entry.getValue().size()));
        }
        return averages;
    }

    /**
     * Petit feu d'artifice sur chaque vainqueur, purement cosmetique. L'API
     * Firework/FireworkMeta n'a pas change depuis bien avant la 1.8.
     */
    private void spawnVictoryFireworks(String winningTeam) {
        Color color = armorColorOf(winningTeam);
        for (Player player : onlinePlayers()) {
            if (!winningTeam.equals(getTeam(player)) || player.getWorld() == null) {
                continue;
            }
            Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .withColor(color)
                    .with(FireworkEffect.Type.BURST)
                    .withFlicker()
                    .build());
            meta.setPower(1);
            firework.setFireworkMeta(meta);
        }
    }

    /**
     * Renvoie tout le monde au lobby du serveur et rend l'arene disponible.
     * Egalement appelee a l'arret du plugin.
     */
    public void reset() {
        cancelCountdown();
        cancelCycle();
        stopGenerators();

        for (Player player : onlinePlayers()) {
            Compat.resetPlayer(player, GameMode.ADVENTURE);
            main.getScoreboardManager().clear(player);
            main.returnToLobby(player);
        }

        // Les deconnectes qui n'ont pas pu revenir se reconnecteront au milieu
        // de la map : on les signale pour les renvoyer au lobby a leur retour.
        // Celui qui s'est reconnecte pendant l'ecran de fin (trop tard pour
        // reprendre, deja la) est sorti de la map tout de suite.
        for (UUID id : reconnectDeadlines.keySet()) {
            Player online = Bukkit.getPlayer(id);
            if (online == null) {
                main.getArenaManager().markStranded(id);
            } else if (main.getArenaManager().getArenaOf(online) == null) {
                Compat.resetPlayer(online, GameMode.ADVENTURE);
                main.sendToLocalLobby(online);
            }
        }
        reconnectDeadlines.clear();
        snapshots.clear();

        players.clear();
        teamOf.clear();
        startVotes.clear();
        scores.clear();
        gameStats.clear();
        nextMark.clear();
        overtime = false;
        timeLeft = 0;

        // Regeneration de la map (blocs puis contenu des coffres), puis menage
        // des items laisses au sol par les morts et les generateurs. Seulement
        // si une partie a eu lieu (zone figee) : un /tt reload ne doit pas
        // vider la map d'une arene au repos, ou un admin construit peut-etre.
        mapTracker.restore();
        if (area != null) {
            clearLooseEntities();
        }
        area = null;
        state = State.WAITING;
    }

    /** Retire items, orbes d'XP et fleches trainant dans la zone de jeu. */
    private void clearLooseEntities() {
        Cuboid zone = getArea();
        World world = zone == null ? null : zone.getWorld();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getEntities()) {
            if ((entity instanceof Item || entity instanceof ExperienceOrb || entity instanceof Arrow)
                    && zone.contains(entity.getLocation())) {
                entity.remove();
            }
        }
    }

    // ================= Couleurs et noms d'equipe =================

    /** Couleur de chat associee a une equipe. */
    public static ChatColor colorOf(String teamName) {
        if ("Rouge".equalsIgnoreCase(teamName)) {
            return ChatColor.RED;
        }
        if ("Bleu".equalsIgnoreCase(teamName)) {
            return ChatColor.BLUE;
        }
        if ("Jaune".equalsIgnoreCase(teamName)) {
            return ChatColor.YELLOW;
        }
        if ("Vert".equalsIgnoreCase(teamName)) {
            return ChatColor.GREEN;
        }
        return ChatColor.WHITE;
    }

    /** Couleur de teinture (armure en cuir, feux d'artifice) d'une equipe. */
    public static Color armorColorOf(String teamName) {
        if ("Rouge".equalsIgnoreCase(teamName)) {
            return Color.RED;
        }
        if ("Bleu".equalsIgnoreCase(teamName)) {
            return Color.BLUE;
        }
        if ("Jaune".equalsIgnoreCase(teamName)) {
            return Color.YELLOW;
        }
        if ("Vert".equalsIgnoreCase(teamName)) {
            return Color.GREEN;
        }
        return Color.WHITE;
    }

    /** Laine coloree d'une equipe, pour les icones des menus. */
    public static TTMaterial woolOf(String teamName) {
        if ("Bleu".equalsIgnoreCase(teamName)) {
            return TTMaterial.BLUE_WOOL;
        }
        if ("Jaune".equalsIgnoreCase(teamName)) {
            return TTMaterial.YELLOW_WOOL;
        }
        if ("Vert".equalsIgnoreCase(teamName)) {
            return TTMaterial.LIME_WOOL;
        }
        return TTMaterial.RED_WOOL;
    }

    /** Nom d'equipe canonique correspondant a une saisie utilisateur. */
    public static String matchTeamName(String input) {
        for (String teamName : TEAM_NAMES) {
            if (teamName.equalsIgnoreCase(input)) {
                return teamName;
            }
        }
        return null;
    }

    private static void putOrRemove(Map<String, Cuboid> map, String team, Cuboid zone) {
        if (zone == null) {
            map.remove(team);
        } else {
            map.put(team, zone);
        }
    }
}
