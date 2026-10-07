package fr.laugh.thetowers.compat;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Petites operations dont l'ecriture "naturelle" casserait sur une partie de
 * l'intervalle 1.8 - 1.21. Tout ce qui est ici compile contre l'API 1.8 et
 * s'execute sans erreur jusqu'en 1.21.
 */
public final class Compat {

    private Compat() {
    }

    /**
     * Remet un joueur a pleine vie.
     *
     * <p>L'ecriture {@code player.setHealth(20)} est fausse : elle leve une
     * {@code IllegalArgumentException} des qu'un autre plugin, un attribut ou
     * un effet a modifie la vie maximale du joueur. On lit donc toujours le
     * maximum reel.
     *
     * <p>{@code getMaxHealth()} est deprecie depuis la 1.9 au profit de
     * l'API Attribute, mais il est toujours present en 1.21 et reste le seul
     * appel qui fonctionne aussi en 1.8. L'API Attribute ne conviendrait pas :
     * l'enum a encore change en 1.21.3.
     */
    @SuppressWarnings("deprecation")
    public static void healFully(Player player) {
        player.setHealth(player.getMaxHealth());
    }

    /**
     * Remet un joueur completement a zero : inventaire, armure, vie, faim,
     * experience, effets et feu. A utiliser a chaque changement de phase pour
     * eviter qu'un etat de partie precedente ne fuite dans la suivante.
     */
    public static void resetPlayer(Player player, GameMode mode) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);

        healFully(player);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);

        player.setLevel(0);
        player.setExp(0f);

        player.setFireTicks(0);
        player.setFallDistance(0f);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        player.setAllowFlight(mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR);
        player.setFlying(false);
        player.setCanPickupItems(true);
        player.setGameMode(mode);
    }

    /**
     * Met un joueur en spectateur : aventure, en vol, invisible et sans rien
     * ramasser. Le mode "spectateur" de Minecraft n'est pas utilise car il
     * cache la hotbar, donc la boussole et la barriere du spectateur.
     */
    public static void makeSpectator(Player player) {
        resetPlayer(player, GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCanPickupItems(false);
        // Invisible meme si masquer le joueur (voir setHidden) echoue.
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE, 0, true, false));
    }

    /**
     * Cache (ou montre) un joueur a un autre. {@code hidePlayer(Player)} est
     * deprecie depuis la 1.12 mais present jusqu'en 1.21 ; on rattrape
     * l'erreur si un serveur le retirait un jour.
     */
    public static void setHidden(Player viewer, Player target, boolean hidden) {
        try {
            if (hidden) {
                viewer.hidePlayer(target);
            } else {
                viewer.showPlayer(target);
            }
        } catch (Throwable ignored) {
            // Sans masquage, l'invisibilite de makeSpectator suffit.
        }
    }

    /**
     * Fait reapparaitre immediatement un joueur mort, sans passer par l'ecran
     * "Vous etes mort". The Towers est un jeu de reapparition permanente :
     * attendre un clic a chaque mort casserait le rythme.
     *
     * <p>{@code Player.spigot().respawn()} est une API Spigot presente de la 1.8
     * a la 1.21 (Paper la conserve). On rattrape tout de meme n'importe quelle
     * erreur : sur un serveur Bukkit pur ou un fork qui l'aurait retiree, le
     * joueur cliquera simplement sur "Reapparaitre", ce qui n'est pas bloquant.
     * A appeler un tick APRES la mort, jamais pendant l'evenement.
     */
    public static void forceRespawn(Player player) {
        if (player == null || !player.isOnline() || !player.isDead()) {
            return;
        }
        try {
            player.spigot().respawn();
        } catch (Throwable ignored) {
            // Pas d'API Spigot : reapparition manuelle, sans consequence.
        }
    }

    /** Traduit les codes couleur ecrits avec {@code &} en codes Minecraft. */
    public static String color(String message) {
        return message == null ? "" : ChatColor.translateAlternateColorCodes('&', message);
    }
}
