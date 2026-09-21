package fr.laugh.thetower;

/**
 * Etats successifs d'une arene.
 *
 * <p>L'etat est porte par chaque {@link Arena} et non par le plugin : plusieurs
 * arenes peuvent tourner en parallele, chacune dans sa phase. C'est la grande
 * difference avec l'ancien TheTower de BadBlock, ou tout etait statique et ou
 * un serveur ne pouvait faire tourner qu'une seule partie avant de redemarrer.
 */
public enum State {

    /** L'arene attend des joueurs. */
    WAITING,

    /** Le minimum de joueurs est atteint, le compte a rebours tourne. */
    STARTING,

    /** Partie en cours (y compris la mort subite, voir {@link Arena#isOvertime()}). */
    PLAYING,

    /** Partie terminee, remise a zero en cours. */
    FINISH
}
