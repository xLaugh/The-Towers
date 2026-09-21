package fr.laugh.thetower.compat;

import org.bukkit.entity.Player;

import com.cryptomorin.xseries.XSound;

/**
 * Sons joues pendant la partie : compte a rebours, lancement, points marques,
 * series de kills.
 *
 * <p>Les noms de son ont autant change entre les versions que les materiaux :
 * {@code Sound.FUSE} et {@code Sound.BLAZE_DEATH} de la 1.8 (ceux qu'utilisait
 * l'ancien TheTower de BadBlock) deviennent {@code ENTITY_CREEPER_PRIMED} et
 * {@code ENTITY_BLAZE_DEATH} a partir de la 1.9. Ecrire {@code Sound.XXX} en dur
 * ne compilerait meme pas contre l'API 1.8.8 pour un nom recent, et planterait
 * au chargement pour un nom disparu.
 *
 * <p>On delegue donc a {@code XSound} (XSeries), qui a ses PROPRES constantes et
 * sait vers quel nom reel les faire correspondre a l'execution. Second et
 * dernier point de contact avec XSeries apres {@link TTMaterial}.
 *
 * <p>Forme {@code record()/soundPlayer()} : {@code XSound.play(Entity, float,
 * float)} est deprecie depuis XSeries 9.x au profit de celle-ci.
 */
public final class Sounds {

    private Sounds() {
    }

    /** Tic du compte a rebours (3, 2, 1) : un timbre qui monte a l'approche de zero. */
    public static void countdownTick(Player player, int secondsLeft) {
        int step = 3 - Math.max(0, Math.min(3, secondsLeft));
        play(XSound.BLOCK_NOTE_BLOCK_PLING, player, 1f + step * 0.2f);
    }

    /** Fanfare au lancement reel de la partie. */
    public static void gameStart(Player player) {
        play(XSound.ENTITY_PLAYER_LEVELUP, player, 1f);
    }

    /** Un coequipier vient de marquer (meme son que sur BadBlock : la meche de creeper). */
    public static void markAlly(Player player) {
        play(XSound.ENTITY_CREEPER_PRIMED, player, 1f);
    }

    /** Un adversaire vient de marquer (le cri du blaze, comme sur BadBlock). */
    public static void markEnemy(Player player) {
        play(XSound.ENTITY_BLAZE_DEATH, player, 1f);
    }

    /** Serie de kills : plus la serie est longue, plus le son est grave et menacant. */
    public static void killStreak(Player player, int streak) {
        if (streak >= 5) {
            play(XSound.ENTITY_ENDERMAN_SCREAM, player, 1f);
        } else if (streak >= 3) {
            play(XSound.ENTITY_ENDER_DRAGON_GROWL, player, 1.5f);
        } else {
            play(XSound.ENTITY_ZOMBIE_AMBIENT, player, 1f);
        }
    }

    /** Fin de partie : explosion pour marquer le coup. */
    public static void gameEnd(Player player) {
        play(XSound.ENTITY_GENERIC_EXPLODE, player, 1f);
    }

    private static void play(XSound sound, Player player, float pitch) {
        if (player == null || !player.isOnline()) {
            return;
        }
        sound.record()
                .withVolume(1f)
                .withPitch(pitch)
                .soundPlayer()
                .forPlayers(player)
                .play();
    }
}
