package fr.laugh.thetower.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

import fr.laugh.thetower.Arena;
import fr.laugh.thetower.Main;
import fr.laugh.thetower.lang.Messages;

/**
 * Gestion des degats.
 *
 * <p>Correction par rapport a l'ancien TheTower : la verification anti tir-ami
 * y etait enregistree deux fois (priorites HIGHEST et MONITOR), et annulait
 * l'evenement en MONITOR, ce que Bukkit interdit. Ici : une seule fois, a la
 * priorite normale, et le MONITOR ne fait que lire (compteur de degats).
 */
public class DamageListeners implements Listener {

    /** Degats "infinis" appliques a une chute dans le vide : mort immediate. */
    private static final double VOID_DAMAGE = 1000d;

    private final Main main;

    public DamageListeners(Main main) {
        this.main = main;
    }

    /** Degats generiques : chute, feu, vide... */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null) {
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            if (arena.isParticipant(player)) {
                // Mort immediate plutot que plusieurs secondes de chute (comme
                // sur BadBlock). Passer par l'evenement de degats plutot que par
                // une hauteur fixe marche sur toutes les versions : le vide est a
                // Y=0 jusqu'en 1.17, a Y=-64 depuis la 1.18.
                event.setDamage(VOID_DAMAGE);
            } else {
                // Au lobby d'attente ou en fin de partie : on rattrape le joueur.
                event.setCancelled(true);
                Location lobby = arena.getLobby();
                if (lobby != null && lobby.getWorld() != null) {
                    player.setFallDistance(0f);
                    player.teleport(lobby);
                }
            }
            return;
        }

        // Hors partie (attente, compte a rebours, fin), on protege les joueurs.
        if (!arena.isParticipant(player)) {
            event.setCancelled(true);
        }
    }

    /** Degats infliges par une autre entite : c'est ici que se joue le PvP. */
    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }
        Arena arena = main.getArenaManager().getArenaOf(victim);
        if (arena == null) {
            return;
        }
        // Un joueur exterieur ne peut pas frapper un participant, et inversement.
        if (!arena.contains(attacker) || !arena.isParticipant(victim) || !arena.isParticipant(attacker)) {
            event.setCancelled(true);
            return;
        }
        // Pas de tir ami.
        String victimTeam = arena.getTeam(victim);
        if (victimTeam != null && victimTeam.equals(arena.getTeam(attacker))) {
            event.setCancelled(true);
            if (!attacker.getUniqueId().equals(victim.getUniqueId())) {
                attacker.sendMessage(Main.PREFIX + Messages.tr("game.no_friendly_fire"));
            }
        }
    }

    /**
     * Lecture seule : cumule les degats reellement infliges (resume de fin) et
     * marque la victime "en combat" (anti deconnexion en combat).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Arena arena = main.getArenaManager().getArenaOf(attacker);
        if (arena != null && arena.isParticipant(attacker) && arena.isParticipant(victim)) {
            arena.recordHit(attacker, victim, event.getFinalDamage());
        }
    }

    /** Pas de faim au lobby d'attente ni en fin de partie. */
    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena != null && !arena.isParticipant(player)) {
            event.setCancelled(true);
        }
    }

    /** Retrouve le joueur a l'origine des degats, fleches comprises. */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }
        return null;
    }
}
