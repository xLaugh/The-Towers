package fr.laugh.thetower.api;

import java.util.Locale;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import fr.laugh.thetower.Arena;
import fr.laugh.thetower.Main;
import fr.laugh.thetower.stats.PlayerStats;

/**
 * Expansion PlaceholderAPI : expose les stats et l'etat de jeu sous forme de
 * placeholders {@code %thetower_...%}.
 *
 * <p><b>Isolation</b> : cette classe etend une classe de PlaceholderAPI. Elle
 * n'est instanciee par {@link Main} <b>qu'apres avoir verifie que le plugin
 * PlaceholderAPI est present</b> ; la JVM ne la resout donc jamais sur un serveur
 * sans PAPI. Ne jamais la referencer ailleurs sans ce garde.
 *
 * <p>Placeholders (prefixe {@code thetower_}) : {@code kills}, {@code deaths},
 * {@code wins}, {@code losses}, {@code games}, {@code points}, {@code rating},
 * {@code kd}, {@code arena}, {@code state}, {@code team}, {@code score},
 * {@code goal}, {@code kit} (kit choisi), {@code ingame}.
 */
public class Placeholders extends PlaceholderExpansion {

    private final Main main;

    public Placeholders(Main main) {
        this.main = main;
    }

    @Override
    public String getIdentifier() {
        return "thetower";
    }

    @Override
    public String getAuthor() {
        return "xLaugh";
    }

    @Override
    public String getVersion() {
        return main.getDescription().getVersion();
    }

    /** Reste enregistree quand PlaceholderAPI se recharge. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT);

        // --- Statistiques (cache) ---
        PlayerStats stats = main.getStatsManager().getCached(player.getUniqueId());
        if ("kd".equals(key)) {
            return stats == null ? "0.00" : String.format(Locale.US, "%.2f", stats.kd());
        }
        if ("kills".equals(key) || "deaths".equals(key) || "wins".equals(key) || "losses".equals(key)
                || "games".equals(key) || "points".equals(key) || "rating".equals(key)) {
            return stats == null ? ("rating".equals(key) ? "1000" : "0") : String.valueOf(stats.value(key));
        }

        // --- Etat de jeu (joueur connecte) ---
        Player online = player.getPlayer();
        Arena arena = online == null ? null : main.getArenaManager().getArenaOf(online);
        String team = arena == null ? null : arena.getTeam(online);
        if ("arena".equals(key)) {
            return arena == null ? "" : arena.getName();
        }
        if ("state".equals(key)) {
            return arena == null ? "" : arena.getState().name();
        }
        if ("team".equals(key)) {
            return team == null ? "" : team;
        }
        if ("score".equals(key)) {
            return team == null ? "" : String.valueOf(arena.getScore(team));
        }
        if ("goal".equals(key)) {
            return arena == null ? "" : String.valueOf(arena.getPointsToWin());
        }
        if ("kit".equals(key)) {
            return online == null ? "" : ChatColor.stripColor(main.getKitManager().getSelected(online).getDisplayName());
        }
        if ("ingame".equals(key)) {
            return arena != null && arena.isRunning() ? "oui" : "non";
        }

        // Placeholder inconnu : PlaceholderAPI laisse le texte tel quel.
        return null;
    }
}
