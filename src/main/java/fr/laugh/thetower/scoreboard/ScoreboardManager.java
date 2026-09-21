package fr.laugh.thetower.scoreboard;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import fr.laugh.thetower.Arena;
import fr.laugh.thetower.Main;
import fr.laugh.thetower.game.GameStats;
import fr.laugh.thetower.lang.Messages;
import fr.laugh.thetower.task.GameCycle;

/**
 * Scoreboard lateral de partie : un par joueur en partie, rafraichi chaque
 * seconde.
 *
 * <p>Contenu : date + arene, temps restant (ou "Mort subite"), le score de
 * chaque equipe sur l'objectif ("Rouge 3/10"), puis les compteurs du joueur
 * pour la partie (kills, points marques). La mention "VOUS" marque l'equipe
 * de celui qui regarde. Voir {@link Sidebar} pour la mecanique cross-version.
 *
 * <p>Attache et detache paresseux : chaque tick on (re)cree le scoreboard des
 * joueurs d'une partie en cours et on rend son scoreboard normal a quiconque
 * n'en est plus. Aucun accrochage au cycle de partie n'est donc necessaire.
 */
public class ScoreboardManager {

    private final Main main;
    private final Map<UUID, Sidebar> sidebars = new HashMap<UUID, Sidebar>();
    // Format numerique, insensible a la langue.
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy");

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

        String title = Messages.tr("scoreboard.title");
        Set<UUID> seen = new HashSet<UUID>();

        for (Arena arena : main.getArenaManager().getArenas()) {
            if (!arena.isRunning()) {
                continue;
            }
            for (Player player : arena.onlinePlayers()) {
                seen.add(player.getUniqueId());
                Sidebar sidebar = sidebars.get(player.getUniqueId());
                if (sidebar == null) {
                    sidebar = new Sidebar(title);
                    sidebars.put(player.getUniqueId(), sidebar);
                    sidebar.apply(player);
                }
                sidebar.update(title, render(arena, player));
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

    // ================= Construction des lignes =================

    private List<String> render(Arena arena, Player viewer) {
        List<String> lines = new ArrayList<String>();

        lines.add(Messages.tr("scoreboard.date",
                "date", dateFormat.format(new Date()), "arena", arena.getName()));
        lines.add("");

        if (arena.isOvertime()) {
            lines.add(Messages.tr("scoreboard.overtime"));
        } else {
            lines.add(Messages.tr("scoreboard.time_left", "time", GameCycle.clock(arena.getTimeLeft())));
        }
        lines.add(Messages.tr("scoreboard.goal", "goal", arena.getPointsToWin()));
        lines.add("");

        String myTeam = arena.getTeam(viewer);
        for (String team : arena.activeTeams()) {
            String you = team.equals(myTeam) ? Messages.tr("scoreboard.you_suffix") : "";
            lines.add(Messages.tr("scoreboard.team_line",
                    "color", Arena.colorOf(team), "letter", team.substring(0, 1),
                    "team", team, "score", arena.getScore(team),
                    "goal", arena.getPointsToWin(), "you", you));
        }
        lines.add("");

        GameStats stats = arena.getGameStats(viewer);
        lines.add(Messages.tr("scoreboard.kills", "value", stats == null ? 0 : stats.getKills()));
        lines.add(Messages.tr("scoreboard.points", "value", stats == null ? 0 : stats.getPoints()));

        String footer = main.getConfig().getString("scoreboard.footer", "");
        if (footer != null && !footer.isEmpty()) {
            lines.add("");
            lines.add(Messages.tr("scoreboard.footer", "footer", footer));
        }
        return lines;
    }
}
