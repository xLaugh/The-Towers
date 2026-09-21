package fr.laugh.thetower.api;

import java.util.UUID;

import org.bukkit.entity.Player;

import fr.laugh.thetower.Arena;
import fr.laugh.thetower.Main;
import fr.laugh.thetower.State;
import fr.laugh.thetower.stats.PlayerStats;

/**
 * Point d'entree public de TheTower pour les autres plugins.
 *
 * <p>Usage cote plugin tiers (avec un {@code softdepend: [TheTower]}) :
 * <pre>
 * PlayerStats s = TheTowerAPI.get().getStats(uuid);
 * boolean playing = TheTowerAPI.get().isInGame(player);
 * </pre>
 *
 * <p>Contrat de stabilite : on ne rend jamais nos objets internes vivants. Les
 * statistiques sortent en {@link PlayerStats#copy() copie}, l'etat d'arene sort
 * en valeurs simples. Rien ici ne laisse un plugin tiers casser une partie.
 *
 * <p>Les stats renvoyees sont celles du <b>cache</b> (joueurs connectes) : le
 * chargement etant asynchrone, un joueur hors ligne renvoie {@code null}.
 */
public final class TheTowerAPI {

    private static TheTowerAPI instance;

    private final Main main;

    private TheTowerAPI(Main main) {
        this.main = main;
    }

    /** Appelee une fois par TheTower a son demarrage. */
    public static void init(Main main) {
        instance = new TheTowerAPI(main);
    }

    /** Instance de l'API, ou {@code null} si TheTower n'est pas encore active. */
    public static TheTowerAPI get() {
        return instance;
    }

    // ================= Statistiques =================

    /** Copie des stats d'un joueur connecte, ou {@code null} s'il est hors ligne. */
    public PlayerStats getStats(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        PlayerStats stats = main.getStatsManager().getCached(uuid);
        return stats == null ? null : stats.copy();
    }

    public PlayerStats getStats(Player player) {
        return player == null ? null : getStats(player.getUniqueId());
    }

    // ================= Etat de jeu =================

    /** Vrai si le joueur est dans une arene dont la partie est en cours. */
    public boolean isInGame(Player player) {
        Arena arena = arenaOf(player);
        return arena != null && arena.isRunning();
    }

    /** Nom de l'arene du joueur, ou {@code null} s'il n'est dans aucune. */
    public String getArenaName(Player player) {
        Arena arena = arenaOf(player);
        return arena == null ? null : arena.getName();
    }

    /** Etat de l'arene du joueur, ou {@code null} s'il n'est dans aucune. */
    public State getArenaState(Player player) {
        Arena arena = arenaOf(player);
        return arena == null ? null : arena.getState();
    }

    /** Equipe du joueur dans sa partie ({@code Rouge}...), ou {@code null}. */
    public String getTeam(Player player) {
        Arena arena = arenaOf(player);
        return arena == null ? null : arena.getTeam(player);
    }

    /** Score actuel de l'equipe du joueur, ou -1 s'il n'est pas en partie. */
    public int getTeamScore(Player player) {
        Arena arena = arenaOf(player);
        String team = arena == null ? null : arena.getTeam(player);
        return team == null ? -1 : arena.getScore(team);
    }

    private Arena arenaOf(Player player) {
        return player == null ? null : main.getArenaManager().getArenaOf(player);
    }
}
