package fr.laugh.thetower;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import fr.laugh.thetower.config.PlacementSession;
import fr.laugh.thetower.game.GeneratorSpot;
import fr.laugh.thetower.game.KitDefinition;
import fr.laugh.thetower.lang.Messages;
import fr.laugh.thetower.quest.QuestProgress;
import fr.laugh.thetower.quest.QuestType;
import fr.laugh.thetower.region.Cuboid;
import fr.laugh.thetower.stats.PlayerStats;
import fr.laugh.thetower.stats.StatsStorage;

/**
 * Commande {@code /tt} et sa completion automatique.
 *
 * <p>Tout ce que fait le menu de configuration ({@code /tt} sans argument) est
 * aussi faisable en commande. Les zones (piscine, zone de spawn, zone des
 * coffres) passent par le meme "mode pose" que le menu : deux clics droits sur
 * les coins opposes.
 *
 * <p>Les permissions sont verifiees sous-commande par sous-commande : un joueur
 * doit pouvoir faire /tt join sans etre administrateur.
 */
public class CommandTower implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "thetower.admin";
    private static final String JOIN = "thetower.join";

    private static final List<String> PLAYER_SUBCOMMANDS =
            Arrays.asList("join", "leave", "list", "stats", "top", "quests", "kit", "help");

    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList(
            "edit", "cancel", "create", "delete", "setlobby", "setarenalobby", "setspawn",
            "setpool", "setspawnzone", "setchestzone", "clearzone", "addgenerator",
            "cleargenerators", "setteams", "setplayersperteam", "setminplayers", "setmaxplayers",
            "setpoints", "setduration", "bows", "save", "info", "start", "stop", "reload");

    /** Sous-commandes qui prennent une equipe en 3e argument. */
    private static final List<String> TEAM_SUBCOMMANDS =
            Arrays.asList("setspawn", "setpool", "setspawnzone", "setchestzone", "clearzone");

    private final Main main;
    private final ArenaManager arenaManager;

    public CommandTower(Main main, ArenaManager arenaManager) {
        this.main = main;
        this.arenaManager = arenaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Sans argument : un admin en jeu ouvre le menu de configuration,
            // tout le monde d'autre (ou la console) recoit l'aide.
            if (sender instanceof Player && sender.hasPermission(ADMIN)) {
                main.getConfigMenu().openList((Player) sender);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);

        if ("help".equals(subcommand)) {
            sendHelp(sender);
            return true;
        }
        if ("list".equals(subcommand)) {
            listArenas(sender);
            return true;
        }
        if ("stats".equals(subcommand)) {
            String target = args.length >= 2 ? args[1]
                    : (sender instanceof Player ? sender.getName() : null);
            if (target == null) {
                send(sender, usage("stats " + Messages.tr("command.args.player")));
            } else {
                showStats(sender, target);
            }
            return true;
        }
        if ("top".equals(subcommand)) {
            showTop(sender, args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "wins");
            return true;
        }
        if ("reload".equals(subcommand)) {
            if (!checkPermission(sender, ADMIN)) {
                return true;
            }
            main.reloadConfig();
            main.getMessages().load();
            main.loadServerLobby();
            main.loadGameContent();
            arenaManager.resetAll();
            arenaManager.load();
            send(sender, Messages.tr("command.reloaded"));
            return true;
        }

        // La suite agit depuis une position ou sur un joueur : pas de console.
        if (!(sender instanceof Player)) {
            send(sender, Messages.tr("command.ingame_only"));
            return true;
        }
        Player player = (Player) sender;

        if ("join".equals(subcommand)) {
            if (!checkPermission(player, JOIN)) {
                return true;
            }
            if (args.length != 2) {
                send(player, usage("join " + Messages.tr("command.args.arena")));
                return true;
            }
            joinArena(player, args[1]);
            return true;
        }
        if ("leave".equals(subcommand)) {
            Arena current = arenaManager.getArenaOf(player);
            if (current == null) {
                send(player, Messages.tr("command.not_in_arena"));
            } else {
                current.leave(player, true);
                send(player, Messages.tr("command.left_arena", "arena", current.getName()));
            }
            return true;
        }
        if ("quests".equals(subcommand)) {
            showQuests(player);
            return true;
        }
        if ("kit".equals(subcommand)) {
            // Sans argument : menu de choix ; avec un nom : choix direct.
            if (args.length < 2) {
                main.getKitMenu().open(player);
                return true;
            }
            KitDefinition kit = main.getKitManager().getKit(args[1]);
            if (kit == null) {
                send(player, Messages.tr("kits.unknown", "name", args[1]));
            } else {
                main.getKitMenu().choose(player, kit);
            }
            return true;
        }

        // A partir d'ici, tout est administratif.
        if (!checkPermission(player, ADMIN)) {
            return true;
        }

        if ("edit".equals(subcommand)) {
            if (args.length >= 2) {
                Arena target = arenaManager.getArena(args[1]);
                if (target == null) {
                    send(player, Messages.tr("command.arena_not_found", "name", args[1]));
                } else {
                    main.getConfigMenu().openEdit(player, target.getName());
                }
            } else {
                main.getConfigMenu().openList(player);
            }
            return true;
        }
        if ("cancel".equals(subcommand)) {
            if (main.getPlacements().isPlacing(player)) {
                main.getPlacements().cancel(player);
            } else {
                send(player, Messages.tr("config.place.not_placing"));
            }
            return true;
        }
        if ("setlobby".equals(subcommand)) {
            main.setServerLobby(player.getLocation());
            send(player, Messages.tr("command.server_lobby_set"));
            return true;
        }
        if ("create".equals(subcommand)) {
            if (args.length != 2) {
                send(player, usage("create " + Messages.tr("command.args.arena")));
            } else if (!arenaManager.createArena(args[1])) {
                send(player, Messages.tr("command.arena_exists"));
            } else {
                send(player, Messages.tr("command.arena_created", "name", args[1]));
            }
            return true;
        }
        if ("delete".equals(subcommand)) {
            if (args.length != 2) {
                send(player, usage("delete " + Messages.tr("command.args.arena")));
            } else if (!arenaManager.deleteArena(args[1])) {
                send(player, Messages.tr("command.arena_none_named"));
            } else {
                send(player, Messages.tr("command.arena_deleted", "name", args[1]));
            }
            return true;
        }

        // Les sous-commandes restantes prennent toutes un nom d'arene.
        if (args.length < 2) {
            send(player, usage(subcommand + " " + Messages.tr("command.args.arena_etc")));
            return true;
        }
        Arena arena = arenaManager.getArena(args[1]);
        if (arena == null) {
            send(player, Messages.tr("command.arena_not_found", "name", args[1]));
            return true;
        }

        if ("setarenalobby".equals(subcommand)) {
            arena.setLobby(player.getLocation());
            arenaManager.save();
            send(player, Messages.tr("command.arena_lobby_set", "arena", arena.getName()));
            return true;
        }

        if (TEAM_SUBCOMMANDS.contains(subcommand)) {
            handleTeamSetting(player, arena, subcommand, args);
            return true;
        }

        if ("addgenerator".equals(subcommand)) {
            if (args.length != 3) {
                send(player, usage("addgenerator " + Messages.tr("command.args.arena_type")));
                return true;
            }
            String type = args[2].toLowerCase(Locale.ROOT);
            if (!main.getGeneratorTypes().containsKey(type)) {
                send(player, Messages.tr("command.unknown_generator",
                        "types", main.getGeneratorTypes().keySet().toString()));
                return true;
            }
            arena.addGenerator(type, player.getLocation());
            arenaManager.save();
            send(player, Messages.tr("command.generator_added", "type", type, "arena", arena.getName()));
            return true;
        }
        if ("cleargenerators".equals(subcommand)) {
            int removed = arena.clearGenerators(args.length >= 3 ? args[2] : null);
            arenaManager.save();
            send(player, Messages.tr("command.generators_cleared", "count", removed));
            return true;
        }

        if ("bows".equals(subcommand)) {
            if (args.length != 3 || !("on".equalsIgnoreCase(args[2]) || "off".equalsIgnoreCase(args[2]))) {
                send(player, usage("bows " + Messages.tr("command.args.arena") + " <on|off>"));
                return true;
            }
            arena.setAllowBows("on".equalsIgnoreCase(args[2]));
            arenaManager.save();
            send(player, Messages.tr(arena.isAllowBows() ? "command.bows_on" : "command.bows_off"));
            return true;
        }

        if ("setminplayers".equals(subcommand) || "setmaxplayers".equals(subcommand)
                || "setteams".equals(subcommand) || "setplayersperteam".equals(subcommand)
                || "setpoints".equals(subcommand) || "setduration".equals(subcommand)) {
            if (args.length != 3) {
                send(player, usage(subcommand + " " + Messages.tr("command.args.arena_number")));
                return true;
            }
            Integer value = parseInt(player, args[2]);
            if (value != null) {
                applyNumericSetting(player, arena, subcommand, value.intValue());
            }
            return true;
        }

        if ("save".equals(subcommand)) {
            String problem = arena.validate();
            if (problem != null) {
                send(player, Messages.tr("command.arena_incomplete", "problem", problem));
                return true;
            }
            arenaManager.save();
            send(player, Messages.tr("command.arena_saved", "arena", arena.getName()));
            return true;
        }
        if ("info".equals(subcommand)) {
            sendInfo(player, arena);
            return true;
        }
        if ("start".equals(subcommand)) {
            startArena(player, arena);
            return true;
        }
        if ("stop".equals(subcommand)) {
            if (!arena.isRunning() && !arena.isState(State.STARTING)) {
                send(player, Messages.tr("command.no_game_running"));
            } else if (arena.isState(State.STARTING)) {
                // Pas encore de partie : on vide simplement l'arene.
                arena.reset();
                send(player, Messages.tr("command.game_stopped"));
            } else {
                arena.end(null);
                send(player, Messages.tr("command.game_stopped"));
            }
            return true;
        }

        send(player, Messages.tr("command.unknown_subcommand"));
        return true;
    }

    // ================= Reglages d'equipe =================

    private void handleTeamSetting(Player player, Arena arena, String subcommand, String[] args) {
        boolean clear = "clearzone".equals(subcommand);
        int expected = clear ? 4 : 3;
        if (args.length != expected) {
            send(player, usage(subcommand + " " + Messages.tr(clear
                    ? "command.args.arena_team_zone" : "command.args.arena_team")));
            return;
        }
        String team = Arena.matchTeamName(args[2]);
        if (team == null) {
            send(player, Messages.tr("command.unknown_team", "teams", Arrays.toString(Arena.TEAM_NAMES)));
            return;
        }

        if ("setspawn".equals(subcommand)) {
            arena.setSpawn(team, player.getLocation());
            arenaManager.save();
            send(player, Messages.tr("command.spawn_set", "color", Arena.colorOf(team), "team", team));
            return;
        }
        if (clear) {
            String zone = args[3].toLowerCase(Locale.ROOT);
            if ("spawnzone".equals(zone)) {
                arena.setSpawnZone(team, null);
            } else if ("chestzone".equals(zone)) {
                arena.setChestZone(team, null);
            } else {
                send(player, usage("clearzone " + Messages.tr("command.args.arena_team_zone")));
                return;
            }
            arenaManager.save();
            send(player, Messages.tr("config.team.zone_cleared"));
            return;
        }

        PlacementSession.Kind kind = "setpool".equals(subcommand) ? PlacementSession.Kind.POOL
                : "setspawnzone".equals(subcommand) ? PlacementSession.Kind.SPAWN_ZONE
                : PlacementSession.Kind.CHEST_ZONE;
        main.getPlacements().start(player, new PlacementSession(arena.getName(), kind, team, null));
    }

    // ================= Actions =================

    private void joinArena(Player player, String arenaName) {
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            send(player, Messages.tr("command.arena_not_found", "name", arenaName));
            return;
        }
        Arena current = arenaManager.getArenaOf(player);
        if (current != null) {
            send(player, Messages.tr("command.already_in_arena", "arena", current.getName()));
            return;
        }
        String problem = arena.join(player);
        if (problem != null) {
            send(player, Messages.tr("command.join_failed", "reason", problem));
        } else {
            send(player, Messages.tr("command.joined_arena", "arena", arena.getName()));
        }
    }

    private void startArena(Player player, Arena arena) {
        if (arena.isRunning() || arena.isState(State.FINISH)) {
            send(player, Messages.tr("command.game_already_running"));
            return;
        }
        String problem = arena.validate();
        if (problem != null) {
            send(player, Messages.tr("command.arena_incomplete", "problem", problem));
            return;
        }
        if (arena.getPlayerCount() < 2) {
            send(player, Messages.tr("command.need_two_players"));
            return;
        }
        arena.start();
        send(player, Messages.tr("command.game_started", "arena", arena.getName()));
    }

    private void applyNumericSetting(Player player, Arena arena, String subcommand, int value) {
        if (value <= 0) {
            send(player, Messages.tr("command.value_positive"));
            return;
        }
        if ("setminplayers".equals(subcommand)) {
            arena.setMinPlayers(value);
            send(player, Messages.tr("command.min_players_set", "value", value));
        } else if ("setmaxplayers".equals(subcommand)) {
            arena.setMaxPlayers(value);
            send(player, Messages.tr("command.max_players_set", "value", value));
        } else if ("setteams".equals(subcommand)) {
            if (value < 2 || value > 4) {
                send(player, Messages.tr("command.teams_range"));
                return;
            }
            arena.setTeamCount(value);
            send(player, Messages.tr("command.teams_set", "value", value));
        } else if ("setplayersperteam".equals(subcommand)) {
            arena.setPlayersPerTeam(value);
            send(player, Messages.tr("command.players_per_team_set", "value", value));
        } else if ("setpoints".equals(subcommand)) {
            arena.setPointsToWin(value);
            send(player, Messages.tr("command.points_set", "value", arena.getPointsToWin()));
        } else {
            arena.setDuration(value * 60);
            send(player, Messages.tr("command.duration_set", "value", arena.getDuration() / 60));
        }
        arenaManager.save();
    }

    private void listArenas(CommandSender sender) {
        List<String> names = arenaManager.getArenaNames();
        if (names.isEmpty()) {
            send(sender, Messages.tr("command.no_arenas"));
            return;
        }
        send(sender, Messages.tr("command.arenas_header", "count", names.size()));
        for (Arena arena : arenaManager.getArenas()) {
            String status = arena.isConfigured()
                    ? ChatColor.GREEN + arena.getState().name()
                    : ChatColor.RED + Messages.tr("command.status_incomplete");
            sender.sendMessage(Messages.tr("command.arena_list_entry",
                    "name", arena.getName(), "status", status,
                    "current", arena.getPlayerCount(), "max", arena.getMaxPlayers()));
        }
    }

    private void sendInfo(Player player, Arena arena) {
        send(player, Messages.tr("command.info_header", "arena", arena.getName()));
        player.sendMessage(Messages.tr("command.info_state", "state", arena.getState()));
        player.sendMessage(Messages.tr("command.info_players",
                "current", arena.getPlayerCount(), "max", arena.getMaxPlayers(),
                "min", arena.getMinPlayers()));
        player.sendMessage(Messages.tr("command.info_teams",
                "teams", arena.getTeamCount(), "per", arena.getPlayersPerTeam()));
        player.sendMessage(Messages.tr("command.info_rules",
                "points", arena.getPointsToWin(), "minutes", arena.getDuration() / 60,
                "bows", Messages.tr(arena.isAllowBows() ? "config.state.on" : "config.state.off")));
        player.sendMessage(Messages.tr("command.info_lobby", "value", format(arena.getLobby())));

        for (String team : arena.activeTeams()) {
            player.sendMessage(Messages.tr("command.info_team",
                    "color", Arena.colorOf(team), "team", team,
                    "spawn", format(arena.getSpawns().get(team)),
                    "pool", format(arena.getPools().get(team)),
                    "spawnzone", format(arena.getSpawnZones().get(team)),
                    "chestzone", format(arena.getChestZones().get(team))));
        }

        StringBuilder generators = new StringBuilder();
        for (GeneratorSpot spot : arena.getGenerators()) {
            if (generators.indexOf(spot.getType()) < 0) {
                if (generators.length() > 0) {
                    generators.append(", ");
                }
                generators.append(spot.getType()).append(" x").append(arena.countGenerators(spot.getType()));
            }
        }
        player.sendMessage(Messages.tr("command.info_generators", "value",
                generators.length() == 0 ? Messages.tr("command.none") : generators.toString()));

        String problem = arena.validate();
        player.sendMessage(problem == null
                ? Messages.tr("command.info_ready")
                : Messages.tr("command.info_problem", "problem", problem));
    }

    private String format(Location location) {
        if (location == null || location.getWorld() == null) {
            return ChatColor.DARK_GRAY + Messages.tr("command.undefined");
        }
        return ChatColor.WHITE + location.getWorld().getName() + " " + location.getBlockX() + ", "
                + location.getBlockY() + ", " + location.getBlockZ();
    }

    private String format(Cuboid zone) {
        return zone == null
                ? ChatColor.DARK_GRAY + Messages.tr("command.undefined")
                : ChatColor.WHITE + zone.describe();
    }

    // ================= Statistiques =================

    private void showStats(final CommandSender sender, String name) {
        // Un joueur connecte : on prefere les valeurs vives du cache (la partie
        // en cours n'est ecrite qu'a la fin).
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            PlayerStats cached = main.getStatsManager().getCached(online.getUniqueId());
            if (cached != null) {
                displayStats(sender, cached);
                return;
            }
        }
        main.getStatsManager().lookupByName(name, new Consumer<PlayerStats>() {
            @Override
            public void accept(PlayerStats stats) {
                if (stats == null) {
                    send(sender, Messages.tr("command.no_stats_player"));
                } else {
                    displayStats(sender, stats);
                }
            }
        });
    }

    private void displayStats(CommandSender sender, PlayerStats stats) {
        send(sender, Messages.tr("command.stats_header", "name", stats.getName()));
        sender.sendMessage(Messages.tr("command.stats_games", "games", stats.getGames()));
        sender.sendMessage(Messages.tr("command.stats_wins_losses",
                "wins", stats.getWins(), "losses", stats.getLosses()));
        sender.sendMessage(Messages.tr("command.stats_kd",
                "kills", stats.getKills(), "deaths", stats.getDeaths(),
                "kd", String.format(Locale.US, "%.2f", stats.kd())));
        sender.sendMessage(Messages.tr("command.stats_points", "points", stats.getPoints()));
        sender.sendMessage(Messages.tr("command.stats_rating", "rating", stats.getRating()));
    }

    private void showTop(final CommandSender sender, final String stat) {
        if (!StatsStorage.isColumn(stat)) {
            send(sender, Messages.tr("command.unknown_stat",
                    "choices", Arrays.toString(StatsStorage.COLUMNS)));
            return;
        }
        main.getStatsManager().top(stat, 10, new Consumer<List<PlayerStats>>() {
            @Override
            public void accept(List<PlayerStats> list) {
                if (list.isEmpty()) {
                    send(sender, Messages.tr("command.no_stats"));
                    return;
                }
                send(sender, Messages.tr("command.top_header", "stat", stat));
                int rank = 1;
                for (PlayerStats stats : list) {
                    sender.sendMessage(Messages.tr("command.top_entry",
                            "rank", rank, "name", stats.getName(), "value", stats.value(stat)));
                    rank++;
                }
            }
        });
    }

    // ================= Quetes du jour =================

    private void showQuests(Player player) {
        if (!main.isQuestsEnabled()) {
            send(player, Messages.tr("command.quests_disabled"));
            return;
        }
        QuestProgress progress = main.getQuestManager().getProgress(player);
        send(player, Messages.tr("command.quests_header"));
        for (QuestType type : QuestType.values()) {
            String mark = progress.isComplete(type) ? Messages.tr("command.quests_done_mark")
                    : Messages.tr("command.quests_todo_mark");
            player.sendMessage(Messages.tr("command.quests_line",
                    "mark", mark, "quest", Messages.tr(type.getLabel()),
                    "current", progress.get(type), "target", type.getTarget()));
        }
    }

    private void sendHelp(CommandSender sender) {
        send(sender, Messages.tr("command.help_header"));
        sender.sendMessage(Messages.tr("command.help_join"));
        sender.sendMessage(Messages.tr("command.help_leave"));
        sender.sendMessage(Messages.tr("command.help_list"));
        sender.sendMessage(Messages.tr("command.help_stats"));
        sender.sendMessage(Messages.tr("command.help_top"));
        sender.sendMessage(Messages.tr("command.help_quests"));
        sender.sendMessage(Messages.tr("command.help_kit"));
        if (!sender.hasPermission(ADMIN)) {
            return;
        }
        sender.sendMessage(Messages.tr("command.help_admin_header"));
        sender.sendMessage(Messages.tr("command.help_edit"));
        sender.sendMessage(Messages.tr("command.help_setlobby"));
        sender.sendMessage(Messages.tr("command.help_create_delete"));
        sender.sendMessage(Messages.tr("command.help_setarenalobby"));
        sender.sendMessage(Messages.tr("command.help_setspawn"));
        sender.sendMessage(Messages.tr("command.help_zones"));
        sender.sendMessage(Messages.tr("command.help_clearzone"));
        sender.sendMessage(Messages.tr("command.help_generators"));
        sender.sendMessage(Messages.tr("command.help_setteams"));
        sender.sendMessage(Messages.tr("command.help_setplayers"));
        sender.sendMessage(Messages.tr("command.help_rules"));
        sender.sendMessage(Messages.tr("command.help_save"));
        sender.sendMessage(Messages.tr("command.help_reload"));
    }

    // ================= Utilitaires =================

    private void send(CommandSender sender, String message) {
        sender.sendMessage(Main.PREFIX + message);
    }

    private String usage(String form) {
        return Messages.tr("command.usage", "form", form);
    }

    private boolean checkPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        send(sender, Messages.tr("command.no_permission", "permission", permission));
        return false;
    }

    /** Analyse un entier sans jamais lever d'exception. */
    private Integer parseInt(CommandSender sender, String raw) {
        try {
            return Integer.valueOf(Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            send(sender, Messages.tr("command.not_integer", "value", raw));
            return null;
        }
    }

    // ================= Completion automatique =================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<String>(PLAYER_SUBCOMMANDS);
            if (sender.hasPermission(ADMIN)) {
                options.addAll(ADMIN_SUBCOMMANDS);
            }
            return filter(options, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            if ("top".equals(sub)) {
                return filter(Arrays.asList(StatsStorage.COLUMNS), args[1]);
            }
            if ("kit".equals(sub)) {
                List<String> ids = new ArrayList<String>();
                for (KitDefinition kit : main.getKitManager().getKits()) {
                    ids.add(kit.getId());
                }
                return filter(ids, args[1]);
            }
            if ("stats".equals(sub)) {
                return filter(onlinePlayerNames(), args[1]);
            }
            if (!"setlobby".equals(sub) && !"create".equals(sub) && !"help".equals(sub)
                    && !"leave".equals(sub) && !"list".equals(sub) && !"quests".equals(sub)
                    && !"cancel".equals(sub) && !"reload".equals(sub)) {
                return filter(arenaManager.getArenaNames(), args[1]);
            }
        }

        if (args.length == 3) {
            if (TEAM_SUBCOMMANDS.contains(sub)) {
                return filter(Arrays.asList(Arena.TEAM_NAMES), args[2]);
            }
            if ("addgenerator".equals(sub) || "cleargenerators".equals(sub)) {
                return filter(new ArrayList<String>(main.getGeneratorTypes().keySet()), args[2]);
            }
            if ("bows".equals(sub)) {
                return filter(Arrays.asList("on", "off"), args[2]);
            }
        }

        if (args.length == 4 && "clearzone".equals(sub)) {
            return filter(Arrays.asList("spawnzone", "chestzone"), args[3]);
        }

        return Collections.emptyList();
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<String>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> matches = new ArrayList<String>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        Collections.sort(matches);
        return matches;
    }
}
