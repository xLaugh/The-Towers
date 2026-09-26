package fr.laugh.thetowers.compat;

import java.util.Optional;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.cryptomorin.xseries.XEnchantment;

/**
 * Enchantements des objets de kit, de la 1.8 a la 1.21.
 *
 * <p>Les noms d'enchantement de Bukkit ont change en 1.20.5 ({@code DIG_SPEED}
 * devient {@code EFFICIENCY}, {@code DAMAGE_ALL} devient {@code SHARPNESS}...).
 * Ecrire {@code Enchantment.DAMAGE_ALL} planterait donc au chargement sur un
 * serveur recent, et {@code Enchantment.SHARPNESS} ne compilerait pas contre
 * l'API 1.8. On delegue a {@code XEnchantment} (XSeries), qui accepte les deux
 * familles de noms dans kits.yml et renvoie l'enchantement reel du serveur.
 *
 * <p>Troisieme point de contact avec XSeries, apres {@link TTMaterial} et
 * {@link Sounds}.
 */
public final class Enchants {

    private Enchants() {
    }

    /**
     * Ajoute un enchantement par son nom (moderne ou ancien), sans limite de
     * niveau ni de compatibilite d'objet.
     *
     * @return faux si le nom est inconnu sur cette version
     */
    public static boolean apply(ItemStack item, String name, int level) {
        Enchantment enchantment = resolve(name);
        if (item == null || enchantment == null) {
            return false;
        }
        item.addUnsafeEnchantment(enchantment, Math.max(1, level));
        return true;
    }

    /**
     * Fait briller une icone de menu (kit selectionne) sans afficher
     * d'enchantement : un enchantement quelconque + le drapeau qui masque la
     * liste. {@code ItemFlag} existe depuis la 1.8.
     */
    public static void glow(ItemMeta meta) {
        Enchantment any = resolve("UNBREAKING");
        if (meta == null || any == null) {
            return;
        }
        meta.addEnchant(any, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }

    private static Enchantment resolve(String name) {
        if (name == null) {
            return null;
        }
        Optional<XEnchantment> match = XEnchantment.matchXEnchantment(name.trim());
        if (!match.isPresent() || !match.get().isSupported()) {
            return null;
        }
        return match.get().getEnchant();
    }
}
