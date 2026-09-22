package fr.laugh.thetowers.region;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Zone rectangulaire alignee sur les blocs, definie par deux coins : piscine
 * d'une equipe, zone protegee du spawn, zone des coffres d'equipe.
 *
 * <p>On ne retient que le NOM du monde, jamais l'objet {@link World} : un monde
 * peut etre decharge puis recharge (la reference deviendrait invalide), et le
 * nom suffit a comparer avec la position d'un joueur. Bornes incluses des deux
 * cotes : un cuboid defini sur un seul bloc contient ce bloc.
 *
 * <p>Classe immuable : on peut la partager sans precaution.
 */
public final class Cuboid {

    private final String world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public Cuboid(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    /**
     * Cuboid entre deux positions, ou {@code null} si elles ne sont pas dans le
     * meme monde (ce qui n'aurait aucun sens pour une zone).
     */
    public static Cuboid of(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null
                || !a.getWorld().getName().equals(b.getWorld().getName())) {
            return null;
        }
        return new Cuboid(a.getWorld().getName(),
                a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    public String getWorldName() {
        return world;
    }

    /** Monde de la zone s'il est charge, sinon {@code null}. */
    public World getWorld() {
        return Bukkit.getWorld(world);
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public boolean contains(String worldName, int x, int y, int z) {
        return world.equals(worldName)
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean contains(Location location) {
        return location != null && location.getWorld() != null
                && contains(location.getWorld().getName(),
                        location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean contains(Block block) {
        return block != null
                && contains(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /** Copie agrandie de {@code horizontal} blocs en X/Z et {@code vertical} en Y. */
    public Cuboid expand(int horizontal, int vertical) {
        return new Cuboid(world, minX - horizontal, minY - vertical, minZ - horizontal,
                maxX + horizontal, maxY + vertical, maxZ + horizontal);
    }

    /**
     * Plus petite zone contenant les deux, ou {@code this} si l'autre est dans
     * un autre monde (on ne melange jamais deux mondes).
     */
    public Cuboid union(Cuboid other) {
        if (other == null || !world.equals(other.world)) {
            return this;
        }
        return new Cuboid(world,
                Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
    }

    /** Nombre de blocs de la zone (long : une zone mal definie peut etre enorme). */
    public long volume() {
        return (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);
    }

    /** Resume lisible pour les messages : "x,y,z -> x,y,z". */
    public String describe() {
        return minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ;
    }
}
