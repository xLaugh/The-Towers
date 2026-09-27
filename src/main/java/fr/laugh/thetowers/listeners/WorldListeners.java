package fr.laugh.thetowers.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

import fr.laugh.thetowers.Main;

/**
 * Un monde charge apres TheTowers (Multiverse ou autre gestionnaire de mondes)
 * : on lui rend les positions, panneaux et lobby qui l'attendaient.
 */
public class WorldListeners implements Listener {

    private final Main main;

    public WorldListeners(Main main) {
        this.main = main;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        main.onWorldLoaded(event.getWorld());
    }
}
