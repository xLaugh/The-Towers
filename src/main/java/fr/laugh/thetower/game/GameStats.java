package fr.laugh.thetower.game;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;

/**
 * Compteurs d'un joueur pour LA partie en cours (et non ses stats de carriere,
 * qui vivent dans {@code stats/}). Servent au resume de fin de partie (meilleur
 * marqueur, plus de kills, plus de degats) et aux series de kills.
 *
 * <p>Le nom est retenu ici : le resume doit pouvoir citer un joueur qui s'est
 * deconnecte entre-temps.
 */
public final class GameStats {

    private final String name;
    private int kills;
    private int deaths;
    private int points;
    private double damage;
    /** Instants (ms) des derniers kills, pour detecter les series. */
    private final LinkedList<Long> recentKills = new LinkedList<Long>();
    /** Dernier adversaire a avoir frappe ce joueur, et quand (anti deconnexion en combat). */
    private UUID lastAttacker;
    private long lastAttackTime;
    /** Kits deja debloques au lancement, pour annoncer les nouveaux en fin de partie. */
    private Set<String> kitsAtStart = Collections.emptySet();

    public GameStats(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public int getPoints() {
        return points;
    }

    public double getDamage() {
        return damage;
    }

    public void addPoint() {
        points++;
    }

    public void addDeath() {
        deaths++;
    }

    public void addDamage(double amount) {
        if (amount > 0) {
            damage += amount;
        }
    }

    /**
     * Enregistre un kill et renvoie la longueur de la serie en cours : le nombre
     * de kills faits dans les {@code windowMs} dernieres millisecondes (2 =
     * double kill, 3 = triple...).
     */
    public int registerKill(long now, long windowMs) {
        kills++;
        recentKills.add(Long.valueOf(now));
        return streak(now, windowMs);
    }

    /** Longueur de la serie en cours sans rien enregistrer. */
    public int streak(long now, long windowMs) {
        Iterator<Long> it = recentKills.iterator();
        while (it.hasNext()) {
            if (now - it.next().longValue() > windowMs) {
                it.remove();
            }
        }
        return recentKills.size();
    }

    /** La mort coupe la serie. */
    public void resetStreak() {
        recentKills.clear();
    }

    /** Ce joueur vient d'etre frappe par un adversaire. */
    public void tagAttacker(UUID attacker, long now) {
        lastAttacker = attacker;
        lastAttackTime = now;
    }

    /**
     * Adversaire qui a frappe ce joueur dans les {@code windowMs} dernieres
     * millisecondes, ou {@code null} : le joueur est-il "en combat" ?
     */
    public UUID recentAttacker(long now, long windowMs) {
        return lastAttacker != null && now - lastAttackTime <= windowMs ? lastAttacker : null;
    }

    /** Oublie le dernier agresseur (a la mort : le combat est termine). */
    public void clearAttacker() {
        lastAttacker = null;
    }

    public Set<String> getKitsAtStart() {
        return kitsAtStart;
    }

    public void setKitsAtStart(Set<String> kitsAtStart) {
        this.kitsAtStart = kitsAtStart == null ? Collections.<String>emptySet() : kitsAtStart;
    }
}
