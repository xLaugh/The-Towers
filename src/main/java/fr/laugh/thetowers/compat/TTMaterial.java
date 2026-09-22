package fr.laugh.thetowers.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.cryptomorin.xseries.XMaterial;

/**
 * Tous les materiaux utilises par TheTowers, sous une forme qui survit au
 * changement de nommage de la 1.13 ("The Flattening").
 *
 * <p>Le probleme resolu ici : ecrire {@code Material.EXP_BOTTLE} dans le code
 * compile une reference directe au champ de l'enum. Ce champ n'existe plus a
 * partir de la 1.13 (il devient {@code EXPERIENCE_BOTTLE}), et la JVM leve
 * alors un {@code NoSuchFieldError} des le chargement de la classe, ce qui tue
 * le plugin au demarrage. C'est exactement ce qui rendait l'ancien TheTower de
 * BadBlock inutilisable apres la 1.8.
 *
 * <p>La resolution est donc deleguee a XMaterial (bibliotheque XSeries), qui
 * connait la correspondance entre les noms de toutes les versions et gere au
 * passage les valeurs de data des versions 1.8 a 1.12 (laines et vitres
 * colorees notamment).
 *
 * <p>Cette classe est, avec {@code Sounds}, l'un des deux seuls points de
 * contact entre TheTowers et XSeries : changer de bibliotheque ne demanderait de
 * toucher qu'a ces deux-la.
 */
public enum TTMaterial {

    // --- Identiques sur toutes les versions ---
    CHEST(XMaterial.CHEST),
    TRAPPED_CHEST(XMaterial.TRAPPED_CHEST),
    ENDER_CHEST(XMaterial.ENDER_CHEST),
    BOW(XMaterial.BOW),
    ARROW(XMaterial.ARROW),
    PAPER(XMaterial.PAPER),
    BARRIER(XMaterial.BARRIER),
    WRITTEN_BOOK(XMaterial.WRITTEN_BOOK),
    NETHER_STAR(XMaterial.NETHER_STAR),
    IRON_SWORD(XMaterial.IRON_SWORD),
    IRON_INGOT(XMaterial.IRON_INGOT),
    GOLD_INGOT(XMaterial.GOLD_INGOT),
    DIAMOND(XMaterial.DIAMOND),
    BAKED_POTATO(XMaterial.BAKED_POTATO),
    TNT(XMaterial.TNT),
    BEACON(XMaterial.BEACON),
    HOPPER(XMaterial.HOPPER),
    COMPASS(XMaterial.COMPASS),
    WATER_BUCKET(XMaterial.WATER_BUCKET),
    LEATHER_HELMET(XMaterial.LEATHER_HELMET),
    LEATHER_CHESTPLATE(XMaterial.LEATHER_CHESTPLATE),
    LEATHER_LEGGINGS(XMaterial.LEATHER_LEGGINGS),
    LEATHER_BOOTS(XMaterial.LEATHER_BOOTS),
    CHAINMAIL_CHESTPLATE(XMaterial.CHAINMAIL_CHESTPLATE),

    // --- Renommes en 1.13 (anciens noms entre parentheses) ---
    EXPERIENCE_BOTTLE(XMaterial.EXPERIENCE_BOTTLE),   // EXP_BOTTLE
    CLOCK(XMaterial.CLOCK),                           // WATCH
    IRON_BARS(XMaterial.IRON_BARS),                   // IRON_FENCE

    // --- Renommes ET colores en 1.13 (laine:data et vitre:data avant) ---
    GRAY_STAINED_GLASS_PANE(XMaterial.GRAY_STAINED_GLASS_PANE),   // STAINED_GLASS_PANE:7
    GREEN_STAINED_GLASS_PANE(XMaterial.GREEN_STAINED_GLASS_PANE), // STAINED_GLASS_PANE:5
    RED_STAINED_GLASS_PANE(XMaterial.RED_STAINED_GLASS_PANE),     // STAINED_GLASS_PANE:14
    RED_WOOL(XMaterial.RED_WOOL),                                 // WOOL:14
    BLUE_WOOL(XMaterial.BLUE_WOOL),                               // WOOL:11
    YELLOW_WOOL(XMaterial.YELLOW_WOOL),                           // WOOL:4
    LIME_WOOL(XMaterial.LIME_WOOL);                               // WOOL:5

    private final XMaterial delegate;

    /** Materiau resolu a l'execution, mis en cache. */
    private Material resolved;

    TTMaterial(XMaterial delegate) {
        this.delegate = delegate;
    }

    /**
     * Materiau correspondant sur la version en cours, ou {@code null} s'il
     * n'existe pas.
     */
    public Material material() {
        if (resolved == null) {
            resolved = delegate.parseMaterial();
        }
        return resolved;
    }

    /** Vrai si {@code other} est ce materiau sur la version en cours. */
    public boolean is(Material other) {
        return other != null && other == material();
    }

    /** Un exemplaire de cet item. */
    public ItemStack item() {
        return item(1);
    }

    /**
     * {@code amount} exemplaires de cet item. C'est {@code parseItem()} qui
     * applique la valeur de data attendue par les versions 1.8 a 1.12.
     */
    public ItemStack item(int amount) {
        ItemStack item = delegate.parseItem();
        if (item == null) {
            throw new IllegalStateException("Materiau introuvable pour " + name()
                    + " sur la version " + MCVersion.raw());
        }
        item.setAmount(amount);
        return item;
    }

    /**
     * Resout tous les materiaux d'un coup au demarrage, pour decouvrir un
     * eventuel probleme de compatibilite tout de suite plutot qu'au milieu
     * d'une partie.
     *
     * @return la liste des materiaux introuvables, vide si tout va bien
     */
    public static List<String> resolveAll(Logger logger) {
        List<String> missing = new ArrayList<String>();
        for (TTMaterial value : values()) {
            if (!value.delegate.isSupported() || value.material() == null) {
                missing.add(value.name());
            }
        }
        if (!missing.isEmpty()) {
            logger.warning("Materiaux introuvables sur " + MCVersion.raw() + " : " + missing);
        }
        return missing;
    }

    /**
     * Item de <b>n'importe quel</b> materiau nomme (nom moderne ou d'avant la
     * 1.13), pour tout ce que l'admin choisit dans la config : kit de depart,
     * generateurs de ressources. XSeries reste seul juge.
     *
     * @return l'item dans la quantite voulue, ou {@code null} si le nom ne
     *         correspond a aucun materiau existant sur cette version
     */
    public static ItemStack itemFromName(String name, int amount) {
        if (name == null) {
            return null;
        }
        Optional<XMaterial> match = XMaterial.matchXMaterial(name.trim());
        if (!match.isPresent() || !match.get().isSupported()) {
            return null;
        }
        ItemStack item = match.get().parseItem();
        if (item == null) {
            return null;
        }
        item.setAmount(Math.max(1, amount));
        return item;
    }

    /** Vrai si ce materiau est un coffre (simple, piege ou de l'Ender). */
    public static boolean isChest(Material material) {
        return CHEST.is(material) || TRAPPED_CHEST.is(material) || ENDER_CHEST.is(material);
    }

    /**
     * Vrai pour les pistons (normal, collant, tete...). Test par NOM et non par
     * constante : les noms ont change en 1.13 (PISTON_BASE -> PISTON...), mais
     * tous contiennent "PISTON" sur toutes les versions.
     */
    public static boolean isPiston(Material material) {
        return material != null && material.name().contains("PISTON");
    }

    /** Vrai pour une piece d'armure en cuir (celles du kit, teintes par equipe). */
    public static boolean isLeatherArmor(Material material) {
        return LEATHER_HELMET.is(material) || LEATHER_CHESTPLATE.is(material)
                || LEATHER_LEGGINGS.is(material) || LEATHER_BOOTS.is(material);
    }
}
