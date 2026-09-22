package fr.laugh.thetowers.config;

import org.bukkit.Location;

/**
 * Une session de "mode pose" : l'admin a clique un bouton du menu (ou lance une
 * commande) qui demande une position dans le monde. Le menu se ferme et il pose
 * en marchant, par clic droit.
 *
 * <p>Deux familles :
 * <ul>
 *   <li><b>points</b> (lobby, spawn, generateur) : un seul clic, la position du
 *       joueur est capturee ;</li>
 *   <li><b>zones</b> (piscine, zone de spawn, zone des coffres) : deux clics,
 *       un par coin oppose. Le premier coin est retenu ici en attendant le
 *       second.</li>
 * </ul>
 */
public class PlacementSession {

    /** Ce que l'admin est en train de placer. */
    public enum Kind {
        LOBBY(false),
        SPAWN(false),
        GENERATOR(false),
        POOL(true),
        SPAWN_ZONE(true),
        CHEST_ZONE(true);

        private final boolean zone;

        Kind(boolean zone) {
            this.zone = zone;
        }

        /** Vrai si ce placement demande deux coins. */
        public boolean isZone() {
            return zone;
        }
    }

    private final String arena;
    private final Kind kind;
    /** Equipe concernee (spawn et zones), ou {@code null}. */
    private final String team;
    /** Type de generateur (cle de config.yml), ou {@code null}. */
    private final String generatorType;
    private Location firstCorner;

    public PlacementSession(String arena, Kind kind, String team, String generatorType) {
        this.arena = arena;
        this.kind = kind;
        this.team = team;
        this.generatorType = generatorType;
    }

    public String getArena() {
        return arena;
    }

    public Kind getKind() {
        return kind;
    }

    public String getTeam() {
        return team;
    }

    public String getGeneratorType() {
        return generatorType;
    }

    /** Premier coin deja capture pour une zone, ou {@code null}. */
    public Location getFirstCorner() {
        return firstCorner;
    }

    public void setFirstCorner(Location firstCorner) {
        this.firstCorner = firstCorner;
    }
}
