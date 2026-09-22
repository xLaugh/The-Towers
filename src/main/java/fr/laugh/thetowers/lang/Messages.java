package fr.laugh.thetowers.lang;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import fr.laugh.thetowers.Main;
import fr.laugh.thetowers.compat.Yaml;

/**
 * Traduction des messages joueur. La langue est choisie pour tout le serveur
 * par l'option {@code language} de la config.
 *
 * <p>Les textes vivent dans {@code lang/<code>.yml} (fr, en), embarques dans
 * le jar et recopies dans le dossier du plugin pour pouvoir etre edites. Chaque
 * cle donne un texte avec codes couleur {@code &} et variables {@code %nom%}.
 *
 * <p>Repli : une cle absente du fichier du serveur est cherchee dans la meme
 * langue embarquee dans le jar (cles ajoutees par une mise a jour), puis dans
 * le francais embarque (toujours complet). Absente partout, on renvoie la cle
 * elle-meme, ce qui saute aux yeux et aide a reperer un oubli.
 */
public class Messages {

    private static final String[] BUNDLED = { "fr", "en" };
    private static final String FALLBACK = "fr";

    /**
     * Instance courante, pour l'acces statique {@link #tr}. Les messages sont
     * demandes depuis partout (y compris des classes sans reference a Main :
     * objets de lobby, taches, enums) ; un point d'acces statique evite de faire
     * transiter Main dans chacune.
     */
    private static Messages instance;

    private final Main main;
    private FileConfiguration lang;
    private FileConfiguration fallback;
    /**
     * La langue choisie telle qu'embarquee dans le jar. Le fichier du dossier du
     * plugin est copie au premier lancement et jamais mis a jour : les cles
     * ajoutees par une nouvelle version n'y sont pas. Sans ce niveau, elles
     * retomberaient directement sur le francais, meme sur un serveur anglais.
     */
    private FileConfiguration bundled;

    public Messages(Main main) {
        this.main = main;
        instance = this;
        load();
    }

    /** Message traduit, ou la cle si le systeme n'est pas encore pret. */
    public static String tr(String key, Object... placeholders) {
        return instance == null ? key : instance.get(key, placeholders);
    }

    /**
     * Liste de lignes traduites (couleurs appliquees), par exemple la mise en
     * page par defaut du scoreboard. Meme repli que {@link #tr} ; absente
     * partout -> liste vide.
     */
    public static List<String> trList(String key) {
        List<String> result = new ArrayList<String>();
        if (instance == null) {
            return result;
        }
        for (FileConfiguration source : instance.sources()) {
            if (source != null && source.isList(key) && !source.getStringList(key).isEmpty()) {
                for (String line : source.getStringList(key)) {
                    result.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                return result;
            }
        }
        return result;
    }

    /**
     * Ordre de recherche d'une cle : le fichier du serveur (modifiable), puis
     * la meme langue embarquee dans le jar (cles ajoutees par une mise a jour),
     * enfin le francais embarque (toujours complet).
     */
    private FileConfiguration[] sources() {
        return new FileConfiguration[] { lang, bundled, fallback };
    }

    /** (Re)charge la langue configuree. Appelee au demarrage et au reload. */
    public void load() {
        for (String code : BUNDLED) {
            saveIfAbsent(code);
        }

        String code = main.getConfig().getString("language", FALLBACK).toLowerCase();
        File file = new File(main.getDataFolder(), "lang/" + code + ".yml");
        if (!file.exists()) {
            main.getLogger().warning("Langue '" + code + "' introuvable, retour au francais.");
            code = FALLBACK;
            file = new File(main.getDataFolder(), "lang/" + FALLBACK + ".yml");
        }
        // Lecture UTF-8 forcee : voir compat/Yaml (accents casses sur Windows + Java 8).
        lang = Yaml.load(file);
        bundled = fromJar(code);
        fallback = fromJar(FALLBACK);
        main.getLogger().info("Langue chargee : " + code + ".");
    }

    /**
     * Message traduit : couleurs {@code &} appliquees et variables {@code %nom%}
     * remplacees. Les variables se passent par paires, nom puis valeur :
     * {@code get("arena.joined", "player", nom, "count", 3)}.
     */
    public String get(String key, Object... placeholders) {
        String raw = null;
        for (FileConfiguration source : sources()) {
            Object value = source == null ? null : source.get(key);
            // Toute valeur simple compte (un nombre ecrit sans guillemets par
            // l'admin aussi, comme avec getString) ; pas une section ni une liste.
            if (value != null && !(value instanceof ConfigurationSection) && !(value instanceof List)) {
                raw = value.toString();
                break;
            }
        }
        if (raw == null) {
            return key;
        }
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = raw.replace("%" + placeholders[i] + "%", String.valueOf(placeholders[i + 1]));
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    // ================= Interne =================

    private void saveIfAbsent(String code) {
        File file = new File(main.getDataFolder(), "lang/" + code + ".yml");
        if (file.exists()) {
            return;
        }
        try {
            main.saveResource("lang/" + code + ".yml", false);
        } catch (IllegalArgumentException e) {
            main.getLogger().warning("Fichier de langue absent du jar : lang/" + code + ".yml");
        }
    }

    private FileConfiguration fromJar(String code) {
        InputStream in = main.getResource("lang/" + code + ".yml");
        if (in == null) {
            return new YamlConfiguration();
        }
        // Lecture en UTF-8 pour que les accents du fichier arrivent intacts.
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, Charset.forName("UTF-8")));
    }
}
