package fr.laugh.thetowers.quest;

/**
 * Les quetes du jour : trois objectifs fixes, identiques pour tous les joueurs,
 * qui se reinitialisent chaque jour (voir {@link QuestManager}).
 *
 * <p>Meme principe qu'AntWars, objectifs adaptes a The Towers : la destruction
 * de lit y est remplacee par le point marque dans la piscine adverse.
 */
public enum QuestType {

    /** Gagner des parties. */
    WIN_GAMES("quest.win_games", 3),
    /** Faire des kills. */
    GET_KILLS("quest.get_kills", 10),
    /** Marquer des points dans la piscine adverse. */
    SCORE_POINTS("quest.score_points", 3);

    private final String label;
    private final int target;

    QuestType(String label, int target) {
        this.label = label;
        this.target = target;
    }

    /** Cle de traduction du libelle (a passer a Messages.tr). */
    public String getLabel() {
        return label;
    }

    /** Objectif a atteindre pour que la quete soit accomplie. */
    public int getTarget() {
        return target;
    }
}
