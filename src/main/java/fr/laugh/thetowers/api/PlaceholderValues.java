package fr.laugh.thetowers.api;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import fr.laugh.thetowers.Arena;
import fr.laugh.thetowers.Main;
import fr.laugh.thetowers.game.GameStats;
import fr.laugh.thetowers.stats.PlayerStats;
import fr.laugh.thetowers.task.GameCycle;

/**
 * Valeurs des placeholders {@code %thetowers_...%}, SANS dependance a
 * PlaceholderAPI.
 *
 * <p>Separees de {@link Placeholders} (l'expansion PAPI) pour servir a deux
 * endroits : l'expansion elle-meme, et le scoreboard configurable, qui
 * remplace nos placeholders lui-meme - ils y fonctionnent donc meme sur un
 * serveur sans PlaceholderAPI.
 *
 * <p>Cles : statistiques de carriere ({@code kills}, {@code deaths},
 * {@code wins}, {@code losses}, {@code games}, {@code points}, {@code rating},
 * {@code kd}), partie en cours ({@code arena}, {@code state}, {@code team},
 * {@code team_color}, {@code score}, {@code score_<equipe>}, {@code goal},
 * {@code time}, {@code overtime}, {@code players}, {@code maxplayers},
 * {@code game_kills}, {@code game_deaths}, {@code game_points}), et
 * {@code kit}, {@code date}, {@code ingame}.
 */
public final class PlaceholderValues {

    // "s" facultatif : l'ancien prefixe %thetower_...% (d'avant le renommage du
    // plugin) reste accepte dans les lignes de scoreboard deja configurees.
    private static final Pattern PLACEHOLDER = Pattern.compile("%thetowers?_([A-Za-z0-9_]+)%");

    private PlaceholderValues() {
    }

    /**
     * Remplace tous les {@code %thetowers_...%} d'un texte. Un placeholder
     * inconnu est laisse tel quel (comme le fait PlaceholderAPI).
     */
    public static String replace(Main main, Player player, String text) {
        if (text == null || text.indexOf('%') < 0) {
            return text;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        // StringBuffer et non StringBuilder : Matcher.appendReplacement(StringBuilder)
        // n'existe qu'a partir de Java 9.
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = resolve(main, player, matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Valeur d'un placeholder (la partie apres {@code thetowers_}), ou
     * {@code null} s'il est inconnu.
     */
    public static String resolve(Main main, OfflinePlayer player, String params) {
        if (player == null || params == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT);

        if ("date".equals(key)) {
            // Nouvelle instance a chaque appel : SimpleDateFormat n'est pas
            // thread-safe, et PAPI peut nous appeler depuis un thread asynchrone
            // (plugins de tab ou de scoreboard).
            return new SimpleDateFormat("dd/MM/yy").format(new Date());
        }

        // --- Statistiques de carriere (cache) ---
        PlayerStats stats = main.getStatsManager().getCached(player.getUniqueId());
        if ("kd".equals(key)) {
            return stats == null ? "0.00" : String.format(Locale.US, "%.2f", stats.kd());
        }
        if ("kills".equals(key) || "deaths".equals(key) || "wins".equals(key) || "losses".equals(key)
                || "games".equals(key) || "points".equals(key) || "rating".equals(key)) {
            return stats == null ? ("rating".equals(key) ? "1000" : "0") : String.valueOf(stats.value(key));
        }

        // --- Partie en cours (joueur connecte) ---
        Player online = player.getPlayer();
        Arena arena = online == null ? null : main.getArenaManager().getArenaOf(online);
        String team = arena == null ? null : arena.getTeam(online);

        if ("kit".equals(key)) {
            return online == null ? "" : ChatColor.stripColor(main.getKitManager().getSelected(online).getDisplayName());
        }
        if ("ingame".equals(key)) {
            return arena != null && arena.isRunning() ? "oui" : "non";
        }
        if (arena == null) {
            // Toutes les cles restantes concernent une arene : vide hors partie,
            // mais null si la cle n'existe pas du tout.
            return isArenaKey(key) ? "" : null;
        }

        if ("arena".equals(key)) {
            return arena.getName();
        }
        if ("state".equals(key)) {
            return arena.getState().name();
        }
        if ("team".equals(key)) {
            return team == null ? "" : team;
        }
        if ("team_color".equals(key)) {
            return team == null ? "" : Arena.colorOf(team) + team;
        }
        if ("score".equals(key)) {
            return team == null ? "" : String.valueOf(arena.getScore(team));
        }
        if (key.startsWith("score_")) {
            String other = Arena.matchTeamName(key.substring("score_".length()));
            return other == null ? null : String.valueOf(arena.getScore(other));
        }
        if ("goal".equals(key)) {
            return String.valueOf(arena.getPointsToWin());
        }
        if ("time".equals(key)) {
            return GameCycle.clock(arena.getTimeLeft());
        }
        if ("overtime".equals(key)) {
            return arena.isOvertime() ? "oui" : "non";
        }
        if ("players".equals(key)) {
            return String.valueOf(arena.getPlayerCount());
        }
        if ("maxplayers".equals(key)) {
            return String.valueOf(arena.getMaxPlayers());
        }
        if ("game_kills".equals(key) || "game_deaths".equals(key) || "game_points".equals(key)) {
            GameStats game = arena.getGameStats(online);
            if (game == null) {
                return "0";
            }
            int value = "game_kills".equals(key) ? game.getKills()
                    : "game_deaths".equals(key) ? game.getDeaths() : game.getPoints();
            return String.valueOf(value);
        }
        return null;
    }

    private static boolean isArenaKey(String key) {
        return "arena".equals(key) || "state".equals(key) || "team".equals(key)
                || "team_color".equals(key) || "score".equals(key) || key.startsWith("score_")
                || "goal".equals(key) || "time".equals(key) || "overtime".equals(key)
                || "players".equals(key) || "maxplayers".equals(key) || "game_kills".equals(key)
                || "game_deaths".equals(key) || "game_points".equals(key);
    }
}
