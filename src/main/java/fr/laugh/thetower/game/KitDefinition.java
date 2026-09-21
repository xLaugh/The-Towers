package fr.laugh.thetower.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import fr.laugh.thetower.compat.TTMaterial;

/**
 * Un kit tel que defini dans kits.yml : son nom, son icone, sa description,
 * ce qu'il faut pour le debloquer et son contenu.
 *
 * <p>Deblocage "plus on joue" : {@link #getRequirements()} associe une colonne
 * de statistiques (games, wins, kills, points...) a la valeur a atteindre ;
 * toutes doivent etre remplies. Une permission peut en plus reserver le kit a
 * un grade, comme les kits VIP de BadBlock.
 */
public final class KitDefinition {

    private final String id;
    private final String displayName;
    private final ItemStack icon;
    private final List<String> description;
    private final Map<String, Integer> requirements;
    private final String permission;
    private final List<ItemStack> items;

    public KitDefinition(String id, String displayName, ItemStack icon, List<String> description,
            Map<String, Integer> requirements, String permission, List<ItemStack> items) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
        this.requirements = requirements;
        this.permission = permission;
        this.items = items;
    }

    /** Identifiant (cle dans kits.yml, en minuscules). */
    public String getId() {
        return id;
    }

    /** Nom affiche, couleurs deja appliquees. */
    public String getDisplayName() {
        return displayName;
    }

    /** Un exemplaire de l'icone du menu. */
    public ItemStack createIcon() {
        return icon.clone();
    }

    public List<String> getDescription() {
        return Collections.unmodifiableList(description);
    }

    /** Colonne de statistiques -> valeur minimale. Vide = debloque d'office. */
    public Map<String, Integer> getRequirements() {
        return Collections.unmodifiableMap(requirements);
    }

    /** Permission requise, ou {@code null}. */
    public String getPermission() {
        return permission;
    }

    public List<ItemStack> getItems() {
        return Collections.unmodifiableList(items);
    }

    /** Contenu a donner, sans arcs ni fleches si l'arene les interdit. */
    public List<ItemStack> itemsFor(boolean allowBows) {
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (ItemStack item : items) {
            if (!allowBows && (TTMaterial.BOW.is(item.getType()) || TTMaterial.ARROW.is(item.getType()))) {
                continue;
            }
            result.add(item.clone());
        }
        return result;
    }
}
