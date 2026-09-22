package fr.laugh.thetowers.task;

import org.bukkit.scheduler.BukkitRunnable;

import fr.laugh.thetowers.Arena;
import fr.laugh.thetowers.lang.Messages;

/**
 * Horloge d'une partie : decompte du temps reglementaire, une fois par seconde.
 *
 * <p>C'est ce qui manquait a l'ancien TheTower de BadBlock : sa boucle de jeu
 * comptait le temps sans jamais s'arreter, si bien que deux equipes prudentes
 * pouvaient faire durer une partie indefiniment. A zero, {@link Arena#timeUp()}
 * designe le vainqueur ou declenche la mort subite ; la tache continue alors a
 * tourner sans rien faire jusqu'a la fin de la partie.
 */
public class GameCycle extends BukkitRunnable {

    private final Arena arena;

    public GameCycle(Arena arena) {
        this.arena = arena;
    }

    @Override
    public void run() {
        if (!arena.isRunning()) {
            cancel();
            return;
        }
        // Delais de reconnexion ecoules : peut faire abandonner une equipe, donc
        // finir la partie ; d'ou la seconde verification juste apres.
        arena.checkDisconnected();
        if (!arena.isRunning() || arena.isOvertime()) {
            return;
        }

        int left = arena.tickTimer();
        if (left <= 0) {
            arena.timeUp();
            return;
        }

        if (left == 600 || left == 300 || left == 120 || left == 60 || left == 30
                || left == 10 || left <= 5) {
            arena.broadcast(Messages.tr("game.time_left", "time", clock(left)));
        }
    }

    /** Secondes -> M:SS. */
    public static String clock(int totalSeconds) {
        int seconds = Math.max(0, totalSeconds);
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return minutes + ":" + (rest < 10 ? "0" + rest : Integer.toString(rest));
    }
}
