package fr.laugh.thetowers.game;

import org.bukkit.Location;

/**
 * Emplacement d'un generateur dans une arene : un type (cle de config.yml) et
 * une position. C'est la partie persistante (arenes.yml) ; la partie vivante,
 * celle qui fait reellement apparaitre les items, est {@link Generator}.
 */
public final class GeneratorSpot {

    private final String type;
    private final Location location;

    public GeneratorSpot(String type, Location location) {
        this.type = type.toLowerCase();
        this.location = location;
    }

    public String getType() {
        return type;
    }

    public Location getLocation() {
        return location.clone();
    }
}
