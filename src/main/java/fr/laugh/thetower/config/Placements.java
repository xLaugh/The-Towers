package fr.laugh.thetower.config;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import fr.laugh.thetower.Arena;
import fr.laugh.thetower.Main;
import fr.laugh.thetower.lang.Messages;
import fr.laugh.thetower.region.Cuboid;

/**
 * Gestion du "mode pose" : quand l'admin demande une position (lobby, spawn,
 * generateur) ou une zone (piscine, zone de spawn, zone des coffres), le menu
 * se ferme et on retient ce qu'il place. Ses clics droits suivants capturent
 * la ou les positions.
 *
 * <p>Pour une zone, chaque coin est le <b>bloc vise</b> par le clic droit (le
 * bloc clique), ou a defaut le bloc sous les pieds de l'admin (clic dans le
 * vide). Viser le bloc est bien plus precis pour delimiter une piscine que de
 * se tenir dedans.
 *
 * <p>Pourquoi ce detour plutot que tout regler dans le menu : un inventaire ne
 * peut pas faire "va te placer quelque part". On sort donc du menu, l'admin se
 * deplace, et son clic droit sert de validation.
 */
public class Placements {

    /**
     * Anti double-declenchement : depuis la 1.9 un clic droit leve
     * PlayerInteractEvent deux fois (main principale + seconde main). On ne peut
     * pas filtrer par {@code getHand()} (API post-1.8, interdite), donc on ignore
     * deux clics rapproches du meme joueur.
     */
    private static final long DEBOUNCE_MS = 250L;

    /** Garde-fou : au-dela, c'est presque surement une erreur de coin. */
    private static final long MAX_ZONE_VOLUME = 2000000L;

    private final Main main;
    private final Map<UUID, PlacementSession> sessions = new HashMap<UUID, PlacementSession>();
    private final Map<UUID, Long> lastClick = new HashMap<UUID, Long>();

    public Placements(Main main) {
        this.main = main;
    }

    public boolean isPlacing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Demarre une session de pose : ferme le menu et annonce ce qu'il faut placer. */
    public void start(Player player, PlacementSession session) {
        sessions.put(player.getUniqueId(), session);
        lastClick.remove(player.getUniqueId());
        player.closeInventory();
        prompt(player, session);
    }

    public void cancel(Player player) {
        if (sessions.remove(player.getUniqueId()) != null) {
            player.sendMessage(Main.PREFIX + Messages.tr("config.place.cancelled"));
        }
    }

    /** Retire une session sans message (deconnexion). */
    public void clear(Player player) {
        sessions.remove(player.getUniqueId());
        lastClick.remove(player.getUniqueId());
    }

    private void prompt(Player player, PlacementSession session) {
        String what = label(session);
        if (session.getKind().isZone()) {
            player.sendMessage(Main.PREFIX + Messages.tr("config.place.zone_first", "what", what));
        } else {
            player.sendMessage(Main.PREFIX + Messages.tr("config.place.point", "what", what));
        }
    }

    /** Libelle de ce qui est place, pour les messages ("la piscine de l'equipe Rouge"...). */
    private String label(PlacementSession session) {
        String team = session.getTeam() == null ? "" : session.getTeam();
        String color = Arena.colorOf(team).toString();
        switch (session.getKind()) {
            case LOBBY:
                return Messages.tr("config.place.what.lobby");
            case SPAWN:
                return Messages.tr("config.place.what.spawn", "color", color, "team", team);
            case GENERATOR:
                return Messages.tr("config.place.what.generator", "type", session.getGeneratorType());
            case POOL:
                return Messages.tr("config.place.what.pool", "color", color, "team", team);
            case SPAWN_ZONE:
                return Messages.tr("config.place.what.spawn_zone", "color", color, "team", team);
            case CHEST_ZONE:
                return Messages.tr("config.place.what.chest_zone", "color", color, "team", team);
            default:
                return "";
        }
    }

    /**
     * Traite le clic droit d'un joueur en session de pose. Renvoie {@code true}
     * si le clic a ete consomme (le listener doit alors annuler l'evenement).
     *
     * @param clicked le bloc vise, ou {@code null} pour un clic dans le vide
     */
    public boolean handleInteract(Player player, Block clicked) {
        PlacementSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long previous = lastClick.get(player.getUniqueId());
        if (previous != null && now - previous.longValue() < DEBOUNCE_MS) {
            return true;
        }
        lastClick.put(player.getUniqueId(), Long.valueOf(now));

        Arena arena = main.getArenaManager().getArena(session.getArena());
        if (arena == null) {
            sessions.remove(player.getUniqueId());
            player.sendMessage(Main.PREFIX + Messages.tr("config.place.arena_gone"));
            return true;
        }

        if (session.getKind().isZone()) {
            captureCorner(player, session, arena, clicked);
            return true;
        }

        Location here = player.getLocation();
        switch (session.getKind()) {
            case LOBBY:
                arena.setLobby(here);
                break;
            case SPAWN:
                arena.setSpawn(session.getTeam(), here);
                break;
            case GENERATOR:
                arena.addGenerator(session.getGeneratorType(), here);
                break;
            default:
                break;
        }
        finish(player, arena, session, Messages.tr("config.place.point_done", "what", label(session)));
        return true;
    }

    private void captureCorner(Player player, PlacementSession session, Arena arena, Block clicked) {
        Location corner = clicked != null
                ? clicked.getLocation()
                : player.getLocation().getBlock().getLocation();

        if (session.getFirstCorner() == null) {
            session.setFirstCorner(corner);
            player.sendMessage(Main.PREFIX + Messages.tr("config.place.zone_second",
                    "pos", format(corner)));
            return;
        }

        Cuboid zone = Cuboid.of(session.getFirstCorner(), corner);
        if (zone == null) {
            player.sendMessage(Main.PREFIX + Messages.tr("config.place.zone_other_world"));
            return;
        }
        if (zone.volume() > MAX_ZONE_VOLUME) {
            // On ne fait pas avancer la session : l'admin reclique un second coin.
            player.sendMessage(Main.PREFIX + Messages.tr("config.place.zone_too_big",
                    "volume", zone.volume()));
            return;
        }

        switch (session.getKind()) {
            case POOL:
                arena.setPool(session.getTeam(), zone);
                break;
            case SPAWN_ZONE:
                arena.setSpawnZone(session.getTeam(), zone);
                break;
            case CHEST_ZONE:
                arena.setChestZone(session.getTeam(), zone);
                break;
            default:
                break;
        }
        finish(player, arena, session, Messages.tr("config.place.zone_done",
                "what", label(session), "zone", zone.describe()));
    }

    /** Termine une session : sauve, informe, et rouvre le menu adapte. */
    private void finish(Player player, Arena arena, PlacementSession session, String message) {
        sessions.remove(player.getUniqueId());
        main.getArenaManager().save();
        player.sendMessage(Main.PREFIX + message);
        if (session.getKind() == PlacementSession.Kind.GENERATOR) {
            main.getConfigMenu().openGenerators(player, arena.getName());
        } else if (session.getTeam() != null) {
            main.getConfigMenu().openTeam(player, arena.getName(), session.getTeam());
        } else {
            main.getConfigMenu().openEdit(player, arena.getName());
        }
    }

    private static String format(Location location) {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }
}
