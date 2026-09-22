package fr.laugh.thetowers.game;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import fr.laugh.thetowers.compat.TTMaterial;

/**
 * Un type de generateur de ressources, tel que defini dans la section
 * {@code generators:} de config.yml : quel item, a quelle frequence, et combien
 * au maximum peuvent trainer au sol.
 *
 * <p>L'ancien TheTower codait en dur fer (3 s), bouteilles d'XP (1,5 s) et
 * diamant (40 s). Ce sont desormais les valeurs par defaut du fichier, et
 * l'admin peut en ajouter autant qu'il veut (or, emeraude, pommes...).
 */
public final class GeneratorType {

    private final String key;
    private final ItemStack template;
    private final int intervalTicks;
    private final int maxOnGround;

    private GeneratorType(String key, ItemStack template, int intervalTicks, int maxOnGround) {
        this.key = key;
        this.template = template;
        this.intervalTicks = intervalTicks;
        this.maxOnGround = maxOnGround;
    }

    /** Identifiant du type (cle dans config.yml, ex. {@code iron}). */
    public String getKey() {
        return key;
    }

    /** Un nouvel exemplaire de l'item produit. */
    public ItemStack createItem() {
        return template.clone();
    }

    public Material getMaterial() {
        return template.getType();
    }

    /** Intervalle entre deux apparitions, en ticks (20 ticks = 1 seconde). */
    public int getIntervalTicks() {
        return intervalTicks;
    }

    /** Nombre maximal d'items de ce generateur au sol en meme temps. */
    public int getMaxOnGround() {
        return maxOnGround;
    }

    /**
     * Lit la section {@code generators:} de config.yml. Un type au materiau
     * inconnu sur cette version est ignore avec un avertissement, plutot que de
     * faire echouer le demarrage.
     */
    public static Map<String, GeneratorType> load(ConfigurationSection section, Logger logger) {
        Map<String, GeneratorType> types = new LinkedHashMap<String, GeneratorType>();
        if (section == null) {
            return types;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String materialName = entry.getString("material", "");
            ItemStack item = TTMaterial.itemFromName(materialName, Math.max(1, entry.getInt("amount", 1)));
            if (item == null) {
                logger.warning("Generateur '" + key + "' ignore : materiau inconnu (" + materialName + ").");
                continue;
            }
            int interval = Math.max(1, entry.getInt("interval", 60));
            int max = Math.max(1, entry.getInt("max-on-ground", 1));
            types.put(key.toLowerCase(), new GeneratorType(key.toLowerCase(), item, interval, max));
        }
        return types;
    }
}
