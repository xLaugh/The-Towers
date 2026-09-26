package fr.laugh.thetowers.scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import fr.laugh.thetowers.Arena;
import fr.laugh.thetowers.Main;
import fr.laugh.thetowers.api.PapiBridge;
import fr.laugh.thetowers.api.PlaceholderValues;
import fr.laugh.thetowers.lang.Messages;
import fr.laugh.thetowers.task.GameCycle;

/**
 * Scoreboard lateral de partie : un par joueur en partie, rafraichi chaque
 * seconde.
 *
 * <p><b>Entierement configurable</b> : titre et lignes viennent de config.yml
 * ({@code scoreboard.title}, {@code scoreboard.lines}) ou, a defaut, de la mise
 * en page par defaut du fichier de langue ({@code scoreboard.lines}). Chaque
 * ligne peut contenir :
 * <ul>
 *   <li>nos placeholders {@code %thetowers_...%}, remplaces ici meme - ils
 *       marchent donc sans PlaceholderAPI (voir {@link PlaceholderValues}) ;</li>
 *   <li>ceux de n'importe quel autre plugin, si PlaceholderAPI est present ;</li>
 *   <li>trois lignes speciales, seules sur leur ligne : {@code {teams}} (une
 *       ligne par equipe avec son score), {@code {timer}} (temps restant ou
 *       mort subite) et {@code {footer}} (masquee si le footer est vide).</li>
 * </ul>
 *
 * <p>Attache et detache paresseux : chaque tick on (re)cree le scoreboard des
 * joueurs d'une partie en cours et on rend son scoreboard normal a quiconque
 * n'en est plus. Voir {@link Sidebar} pour la mecanique cross-version.
 */
public class ScoreboardManager {

    private static final String TEAMS = "{teams}";
    private static final String TIMER = "{timer}";
    private static final String FOOTER = "{footer}";

    private final Main main;
    private final Map<UUID, Sidebar> sidebars = new HashMap<UUID, Sidebar>();

    public ScoreboardManager(Main main) {
        this.main = main;
    }

    /** Rafraichit tous les scoreboards (appele chaque seconde). */
    public void updateAll() {
        if (Bukkit.getScoreboardManager() == null) {
            return; // aucun monde charge : rien a afficher.
        }
        if (!main.getConfig().getBoolean("scoreboard.enabled", true)) {
            clearAll();
            return;
        }

        // Modele relu a chaque rafraichissement : un /tt reload s'applique aussitot.
        String titleTemplate = titleTemplate();
        List<String> template = linesTemplate();
        Set<UUID> seen = new HashSet<UUID>();

        for (Arena arena : main.getArenaManager().getArenas()) {
            if (!arena.isRunning()) {
                continue;
            }
            for (Player player : arena.onlinePlayers()) {
                seen.add(player.getUniqueId());
                String title = resolve(player, titleTemplate);
                Sidebar sidebar = sidebars.get(player.getUniqueId());
                if (sidebar == null) {
                    sidebar = new Sidebar(title);
                    sidebars.put(player.getUniqueId(), sidebar);
                    sidebar.apply(player);
                }
                sidebar.update(title, render(arena, player, template));
            }
        }

        // Menage : rendre son scoreboard normal a quiconque n'est plus en partie.
        Iterator<Map.Entry<UUID, Sidebar>> it = sidebars.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Sidebar> entry = it.next();
            if (!seen.contains(entry.getKey())) {
                resetBoard(Bukkit.getPlayer(entry.getKey()));
                it.remove();
            }
        }
    }

    /** Retire immediatement le scoreboard d'un joueur (fin de partie, /tt leave). */
    public void clear(Player player) {
        if (player == null) {
            return;
        }
        if (sidebars.remove(player.getUniqueId()) != null) {
            resetBoard(player);
        }
    }

    /** Retire tous les scoreboards (arret du plugin, scoreboard desactive). */
    public void clearAll() {
        for (UUID id : sidebars.keySet()) {
            resetBoard(Bukkit.getPlayer(id));
        }
        sidebars.clear();
    }

    private void resetBoard(Player player) {
        if (player != null && player.isOnline() && Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    // ================= Modele (config ou langue) =================

    /** Titre de config.yml s'il est renseigne, sinon celui de la langue. */
    private String titleTemplate() {
        String custom = main.getConfig().getString("scoreboard.title", "");
        return custom == null || custom.trim().isEmpty() ? Messages.tr("scoreboard.title") : custom;
    }

    /** Lignes de config.yml si la liste est renseignee, sinon la mise en page de la langue. */
    private List<String> linesTemplate() {
        List<String> custom = main.getConfig().getStringList("scoreboard.lines");
        return custom == null || custom.isEmpty() ? Messages.trList("scoreboard.lines") : custom;
    }

    // ================= Construction des lignes =================

    private List<String> render(Arena arena, Player viewer, List<String> template) {
        List<String> lines = new ArrayList<String>();
        for (String raw : template) {
            String marker = raw == null ? "" : raw.trim();
            if (TEAMS.equalsIgnoreCase(marker)) {
                addTeamLines(lines, arena, viewer);
            } else if (TIMER.equalsIgnoreCase(marker)) {
                lines.add(arena.isOvertime() ? Messages.tr("scoreboard.overtime")
                        : Messages.tr("scoreboard.time_left", "time", GameCycle.clock(arena.getTimeLeft())));
            } else if (FOOTER.equalsIgnoreCase(marker)) {
                String footer = main.getConfig().getString("scoreboard.footer", "");
                if (footer != null && !footer.isEmpty()) {
                    lines.add(Messages.tr("scoreboard.footer", "footer", footer));
                }
            } else {
                lines.add(resolve(viewer, raw));
            }
        }
        // Lignes vides en bas (typiquement : footer vide masque) : on les retire
        // pour ne pas laisser un trou sous le scoreboard.
        while (!lines.isEmpty() && ChatColor.stripColor(lines.get(lines.size() - 1)).trim().isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private void addTeamLines(List<String> lines, Arena arena, Player viewer) {
        String myTeam = arena.getTeam(viewer);
        for (String team : arena.activeTeams()) {
            String you = team.equals(myTeam) ? Messages.tr("scoreboard.you_suffix") : "";
            lines.add(Messages.tr("scoreboard.team_line",
                    "color", Arena.colorOf(team), "letter", team.substring(0, 1),
                    "team", team, "score", arena.getScore(team),
                    "goal", arena.getPointsToWin(), "you", you));
        }
    }

    /**
     * Remplit une ligne libre : nos placeholders d'abord (sans PAPI), puis ceux
     * des autres plugins si PlaceholderAPI est la, enfin les couleurs {@code &}
     * (en dernier, pour colorer aussi ce qu'ont renvoye les placeholders).
     */
    private String resolve(Player viewer, String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        String text = PlaceholderValues.replace(main, viewer, line);
        if (main.hasPlaceholderApi() && text.indexOf('%') >= 0) {
            // Garde d'isolation : PapiBridge n'est jamais appele sans PAPI.
            text = PapiBridge.apply(viewer, text);
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
