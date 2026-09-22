package fr.laugh.thetowers.listeners;

import java.util.Iterator;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import fr.laugh.thetowers.Arena;
import fr.laugh.thetowers.Main;
import fr.laugh.thetowers.compat.Compat;
import fr.laugh.thetowers.compat.TTMaterial;
import fr.laugh.thetowers.lang.Messages;

/**
 * Connexions, deconnexions, morts et reapparitions.
 *
 * <p>The Towers est un jeu de reapparition permanente : un participant qui meurt
 * lache son inventaire (on le vole !) et revient aussitot a sa base avec le kit
 * de depart. Personne n'est jamais elimine.
 */
public class PlayerListeners implements Listener {

    /** Les porteurs de cette permission ne sont pas auto-rejoints en mode arene. */
    private static final String ARENA_MODE_BYPASS = "thetowers.arenamode.bypass";

    private final Main main;

    public PlayerListeners(Main main) {
        this.main = main;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        main.getStatsManager().load(player);
        main.getQuestManager().load(player);
        main.getKitManager().loadSelection(player);

        // Reconnexion : le joueur avait encore sa place dans une partie en cours.
        // Passe avant le mode arene, qui le refuserait ("partie deja en cours").
        if (main.getArenaManager().getRejoinableArena(player.getUniqueId()) != null) {
            scheduleRejoin(player);
            return;
        }
        // Deconnecte qui n'est pas revenu a temps : il reapparait au milieu
        // d'une map de jeu, on le ramene au lobby.
        boolean stranded = main.getArenaManager().consumeStranded(player.getUniqueId());

        // Mode arene (reseau "1 serveur = 1 partie") : on jette le joueur
        // directement dans la partie, sauf s'il a la permission de contournement.
        if (main.isArenaModeEnabled() && !player.hasPermission(ARENA_MODE_BYPASS)) {
            autoJoinArena(player);
            return;
        }
        if (stranded) {
            Compat.resetPlayer(player, GameMode.ADVENTURE);
            main.sendToLocalLobby(player);
            return;
        }

        if (!main.getConfig().getBoolean("lobby-on-join", true)) {
            return;
        }
        Location lobby = main.getServerLobby();
        if (lobby != null && lobby.getWorld() != null) {
            Compat.resetPlayer(player, GameMode.ADVENTURE);
            player.teleport(lobby);
        }
    }

    /**
     * Remet le joueur dans sa partie un tick apres la connexion (le temps que
     * celle-ci s'acheve). Si la partie s'est terminee entre-temps, on le traite
     * comme une connexion normale.
     */
    private void scheduleRejoin(final Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                Arena arena = main.getArenaManager().getRejoinableArena(player.getUniqueId());
                if (arena != null) {
                    arena.rejoin(player);
                    return;
                }
                main.getArenaManager().consumeStranded(player.getUniqueId());
                if (main.isArenaModeEnabled() && !player.hasPermission(ARENA_MODE_BYPASS)) {
                    joinArenaMode(player);
                } else {
                    Compat.resetPlayer(player, GameMode.ADVENTURE);
                    main.sendToLocalLobby(player);
                }
            }
        }.runTaskLater(main, 1L);
    }

    /** Fait rejoindre l'arene du mode arene un tick apres la connexion. */
    private void autoJoinArena(final Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    joinArenaMode(player);
                }
            }
        }.runTaskLater(main, 1L);
    }

    /**
     * Fait rejoindre l'arene configuree en mode arene. Si elle est introuvable,
     * pleine ou deja en partie, on renvoie le joueur au hub.
     */
    private void joinArenaMode(Player player) {
        Arena arena = main.getArenaManager().getArena(main.getArenaModeArena());
        String problem = arena == null ? "no arena" : arena.join(player);
        if (problem != null) {
            player.sendMessage(Main.PREFIX + Messages.tr("player.arena_mode_no_game"));
            Compat.resetPlayer(player, GameMode.ADVENTURE);
            main.returnToLobby(player);
        }
    }

    /**
     * Deconnexion : un participant en pleine partie garde sa place pour revenir
     * (voir {@link Arena#disconnect}) ; sinon il quitte simplement l'arene.
     * L'arene passe AVANT le dechargement des stats : une deconnexion en combat
     * compte une mort, qui doit etre enregistree.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena != null) {
            if (main.isReconnectionEnabled() && arena.isParticipant(player)) {
                arena.disconnect(player);
            } else {
                arena.leave(player, false);
            }
        }
        main.getStatsManager().unload(player);
        main.getQuestManager().unload(player);
        main.getKitManager().unloadSelection(player);
    }

    /**
     * Mort d'un participant : compteurs et message a l'arene uniquement (sinon
     * chaque mort de chaque arene s'afficherait a tout le serveur), puis
     * reapparition immediate au tick suivant.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null || !arena.isParticipant(player)) {
            return;
        }

        event.setDeathMessage(null);
        // L'armure en cuir du kit ne tombe pas : elle ne vaut rien et, teinte aux
        // couleurs de l'equipe, porterait a confusion sur le dos d'un adversaire.
        Iterator<ItemStack> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack item = drops.next();
            if (item != null && TTMaterial.isLeatherArmor(item.getType())) {
                drops.remove();
            }
        }

        Player killer = player.getKiller();
        arena.handleDeath(player, killer);

        // Un tick d'attente : forcer la reapparition au beau milieu de
        // l'evenement de mort laisse le joueur dans un etat incoherent.
        new BukkitRunnable() {
            @Override
            public void run() {
                Compat.forceRespawn(player);
            }
        }.runTaskLater(main, 1L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final Arena arena = main.getArenaManager().getArenaOf(player);
        if (arena == null) {
            return;
        }
        Location target = arena.respawnLocation(player);
        if (target != null && target.getWorld() != null) {
            event.setRespawnLocation(target);
        }
        if (!arena.isParticipant(player)) {
            return;
        }
        // Le kit est donne un tick apres : pendant l'evenement, l'inventaire du
        // joueur n'est pas encore dans son etat definitif sur toutes les versions.
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && arena.isParticipant(player)) {
                    arena.spawnPlayer(player);
                }
            }
        }.runTaskLater(main, 1L);
    }
}
