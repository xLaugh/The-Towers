package fr.laugh.thetowers.game;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Equipement d'un joueur mis de cote pendant une deconnexion : inventaire,
 * armure et XP (la monnaie d'enchantement du jeu).
 *
 * <p>Pourquoi le garder en memoire plutot que de laisser l'inventaire dans le
 * fichier du joueur : si le joueur ne revient jamais, rien de la partie ne doit
 * le suivre ailleurs sur le serveur. On vide donc son vrai inventaire au depart
 * et on le lui rend s'il revient a temps ; sinon la copie est simplement oubliee.
 *
 * <p>Cross-version : en 1.8 {@code getContents()} renvoie les 36 cases, a partir
 * de la 1.9 il inclut aussi armure et seconde main (41 cases). On rejoue donc
 * {@code setContents} PUIS {@code setArmorContents} : sur les deux familles de
 * versions, le resultat est le meme inventaire.
 */
public final class PlayerSnapshot {

    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final int level;
    private final float exp;

    private PlayerSnapshot(ItemStack[] contents, ItemStack[] armor, int level, float exp) {
        this.contents = contents;
        this.armor = armor;
        this.level = level;
        this.exp = exp;
    }

    public static PlayerSnapshot capture(Player player) {
        PlayerInventory inventory = player.getInventory();
        return new PlayerSnapshot(copy(inventory.getContents()), copy(inventory.getArmorContents()),
                player.getLevel(), player.getExp());
    }

    /** Rend l'equipement au joueur (inventaire deja vide). */
    public void restore(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setContents(copy(contents));
        inventory.setArmorContents(copy(armor));
        player.setLevel(level);
        player.setExp(exp);
    }

    private static ItemStack[] copy(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] == null ? null : items[i].clone();
        }
        return copy;
    }
}
