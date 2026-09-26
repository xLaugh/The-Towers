package fr.laugh.thetowers.api;

import org.bukkit.OfflinePlayer;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import fr.laugh.thetowers.Main;

/**
 * Expansion PlaceholderAPI : expose les stats et l'etat de jeu sous forme de
 * placeholders {@code %thetowers_...%}, utilisables par n'importe quel plugin
 * d'affichage (tab, hologrammes, chat, scoreboards tiers).
 *
 * <p>Les valeurs sont calculees par {@link PlaceholderValues}, qui ne depend
 * pas de PAPI (le scoreboard de TheTowers s'en sert aussi sans PAPI). Voir
 * cette classe pour la liste des cles.
 *
 * <p><b>Isolation</b> : cette classe etend une classe de PlaceholderAPI. Elle
 * n'est instanciee par {@link Main} <b>qu'apres avoir verifie que le plugin
 * PlaceholderAPI est present</b> ; la JVM ne la resout donc jamais sur un serveur
 * sans PAPI. Ne jamais la referencer ailleurs sans ce garde.
 */
public class Placeholders extends PlaceholderExpansion {

    private final Main main;
    private final String identifier;

    /**
     * @param identifier prefixe des placeholders : {@code thetowers}, ou
     *                   {@code thetower} pour l'expansion de compatibilite avec
     *                   l'ancien nom du plugin (memes valeurs)
     */
    public Placeholders(Main main, String identifier) {
        this.main = main;
        this.identifier = identifier;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String getAuthor() {
        return "xLaugh";
    }

    @Override
    public String getVersion() {
        return main.getDescription().getVersion();
    }

    /** Reste enregistree quand PlaceholderAPI se recharge. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        // null = placeholder inconnu : PlaceholderAPI laisse le texte tel quel.
        return PlaceholderValues.resolve(main, player, params);
    }
}
