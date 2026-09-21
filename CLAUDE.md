# TheTower — plugin Minecraft

Mini-jeu **The Tower** : chaque équipe a une base avec une **piscine** ; sauter
dans la piscine d'une équipe adverse marque un point et renvoie à sa base. La
première équipe à l'objectif (10 points par défaut) gagne. Personne n'est
éliminé : on réapparaît à sa base avec le kit de départ.

C'est une **réécriture autonome** du TheTower du serveur BadBlock, sur le modèle
du projet AntWars (`../AntWars`, même auteur, même architecture). Le code
BadBlock d'origine est conservé dans `legacy/` pour référence, **jamais compilé**.

**Le plugin doit tourner sur toutes les versions de Minecraft de la 1.8.8 à la
1.21.x, en Spigot comme en Paper, avec un jar unique.** C'est la contrainte qui
prime sur tout le reste.

---

## Build

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Eclipse Adoptium\jdk-25.0.3.9-hotspot"; & "C:\Users\Laugh\tools\apache-maven-3.9.16\bin\mvn.cmd" clean package
```

Sortie : `target/TheTower-1.0.0.jar` (~1,2 Mo : XSeries + pilote MariaDB embarqués
et relocalisés sous `fr.laugh.thetower.libs`).

- JDK 25 Adoptium, mais compilation en `--release 8`.
- Le projet **n'est pas sous git** : sauvegarder avant tout refactor important.
- Eclipse : `File > Import > Existing Maven Projects`. Le `.classpath` présent
  n'est qu'un filet de secours pointant sur les jars du dépôt local.

---

## Le dossier `legacy/`

- `legacy/TheTowerBadblock/` : le TheTower v2 de BadBlock (2019, Spigot 1.8.8).
  Dépend de la **BadblockGameAPI**, dont l'implémentation n'existe plus : il ne
  peut ni compiler seul ni tourner. Sert de référence pour les règles du jeu.
- `legacy/BadBlock-API/` : les **interfaces** de cette API (273 fichiers,
  presque uniquement des déclarations), sans implémentation.

Ne rien en importer. Tout ce qui y est utile a été réécrit dans `src/`.

---

## Les cinq règles à ne jamais enfreindre

Identiques à AntWars. Les enfreindre ne casse pas la compilation : ça casse le
plugin à l'exécution, sur une version seulement.

1. **Syntaxe Java 8 uniquement.** Pas de `var`, records, text blocks, switch
   expressions, `List.of`. Le bytecode doit rester en version 52.
2. **Jamais de `Material.XXX`, `Sound.XXX` ni `Enchantment.XXX` écrit en dur**
   (sauf `Material.AIR`). Passer par `compat/TTMaterial` (XMaterial),
   `compat/Sounds` (XSound) et `compat/Enchants` (XEnchantment) — les trois
   seuls points de contact avec XSeries. C'est exactement ce qui rendait
   l'ancien TheTower inutilisable après la 1.8 (`EXP_BOTTLE`, `WOOL`,
   `Sound.FUSE`...) ; les enchantements, eux, ont été renommés en 1.20.5.
3. **Aucune API postérieure à la 1.8**, et **aucune méthode appelée sur
   `InventoryView`** (interface en Paper 1.21).
4. **`minimizeJar` reste à `false`** dans le pom (XSeries résout des classes
   dynamiquement).
5. **Ne pas retirer l'exclusion de `bungeecord-chat`** dans le pom.

## Vérifier avant de conclure

Après une modification qui touche matériaux, sons, blocs ou inventaires,
extraire le jar et vérifier :

```bash
# 1. Tout doit être en bytecode 52 (Java 8), bibliothèques comprises
find . -name "*.class" -exec sh -c 'od -An -tu1 -j7 -N1 "$1"' _ {} \; | tr -d ' ' | sort | uniq -c
# 2. Aucun champ Material/Sound à part Material.AIR
javap -c -p <classes> | grep -oE "Field org/bukkit/(Material|Sound)\.[A-Z_]+"
# 3. Aucune méthode appelée sur InventoryView
javap -c -p <classes> | grep -oE "(Method|InterfaceMethod) org/bukkit/inventory/InventoryView\.[a-zA-Z]+"
```

Et pour les langues : `fr.yml` et `en.yml` doivent avoir **exactement** le même
jeu de clés, et chaque `Messages.tr("clé")` du code doit exister dans `fr.yml`.

---

## Architecture

```
fr.laugh.thetower
  Main            point d'entrée, config, accès aux composants, kit et
                  types de générateurs (rechargés par /tt reload)
  Arena           une arène : config (équipes, piscines, zones, générateurs)
                  + déroulement complet d'une partie (points, morts, temps,
                  mort subite, fin, résumé, remise à zéro)
  ArenaManager    arenes.yml ; arène d'un joueur ; arène en cours à une position
  CommandTower    /tt + tab-completion (tout ce que fait le menu)
  State           WAITING, STARTING, PLAYING, FINISH
  region/Cuboid   zone à deux coins (piscine, zone de spawn, zone des coffres) ;
                  ne retient que le NOM du monde
  game/           GeneratorType (config.yml), GeneratorSpot (arenes.yml),
                  Generator (tâche vivante), GameStats (compteurs d'UNE partie,
                  dont le marquage "en combat" et les kits débloqués au départ),
                  PlayerSnapshot (équipement mis de côté pendant une déconnexion),
                  KitDefinition / KitManager (kits.yml, déblocage, choix du
                  joueur dans kit-selections.yml) / KitMenu + KitHolder (menu)
  compat/         MCVersion, TTMaterial, Sounds, Enchants, Titles, Proxy, Compat
                  (dont forceRespawn : Player.spigot().respawn() sous try/catch),
                  Yaml (lecture UTF-8 forcée, voir plus bas)
  config/         menu GUI (ConfigMenu, ConfigHolder) + "mode pose"
                  (Placements, PlacementSession) : points en 1 clic, zones en
                  2 clics sur les coins (le bloc visé)
  map/MapTracker  régénération : instantané BlockState de chaque bloc modifié
                  ET contenu d'origine de chaque conteneur ouvert
  scoreboard/     Sidebar (cross-version, copie d'AntWars) + ScoreboardManager
  stats/          PlayerStats (+ points marqués), StatsStorage, YAML/MySQL,
                  StatsManager (cache + I/O sur un thread dédié), Elo
  quest/          quêtes du jour (gagner, kills, marquer)
  sign/           panneaux de connexion [thetower]
  lobby/          objets de la hotbar d'attente (livre, kits, vote, quitter)
  lang/Messages   fr + en ; clé absente -> français embarqué
  api/            TheTowerAPI (façade publique) + Placeholders (%thetower_...%)
  discord/        annonce de fin de partie sur webhook
  listeners/      Player, Damage, Blocks, Game, Map, Lobby, Sign, Config
  task/           Countdown (attente), GameCycle (temps réglementaire)
```

Repris d'AntWars quasiment à l'identique : compat/, Sidebar, stats/, quest/,
sign/, lobby/, lang/, discord/, le socle du menu de config. Les invariants
d'AntWars s'appliquent (threading des stats, isolation de PlaceholderAPI et du
pilote MariaDB, identification des menus par `InventoryHolder`).

**Encodage des YAML.** Jusqu'en 1.12, `YamlConfiguration.loadConfiguration(File)`
lit avec l'encodage par défaut du système : cp1252 sur un serveur Windows sous
Java 8, d'où des accents cassés. Tout fichier susceptible de contenir des
accents (langues, kits.yml) se lit par `compat/Yaml.load` (UTF-8 forcé). AntWars
a le même problème dans son `Messages`, non corrigé à ce jour.

---

## Règles du jeu

- Entrée par `/tt join <arène>`, un panneau `[thetower]`, ou automatiquement en
  **mode arène** (`arena-mode`, 1 serveur = 1 partie, comme BadBlock).
- 2 à 4 équipes (Rouge, Bleu, Jaune, Vert), défaut **2 équipes de 4**.
  Répartition automatique équilibrée, dans l'ordre d'arrivée.
- **Marquer** : entrer dans la piscine d'une équipe adverse (détection sur
  changement de bloc dans `GameListeners.onMove`). Le joueur est renvoyé à sa
  base, soigné ; titre et son pour toute l'arène. Anti double-déclenchement
  `mark-cooldown` (3 s).
- **Victoire** : objectif de points atteint, ou dernière équipe avec des joueurs.
- **Durée maximale** (ajout, BadBlock n'en avait pas), **40 min** par défaut
  (`game-duration`, réglable par arène) : à la fin du temps, l'équipe qui mène
  gagne ; égalité -> **mort subite**, le prochain point gagne.
- **Reconnexion en cours de partie** (`reconnection:`) : un participant qui se
  déconnecte garde sa place (`Arena.disconnect`), son équipement est mis de
  côté en mémoire et son vrai inventaire vidé (rien ne fuit s'il ne revient
  pas). À sa reconnexion avant `timeout` (180 s, 0 = jusqu'à la fin), il est
  remis à sa base avec son équipement (`Arena.rejoin`, passe avant le mode
  arène). Anti-abus : déconnecté moins de 10 s après un coup adverse = mort
  (objets au sol, kill pour l'adversaire, retour avec le kit). Une équipe dont
  tous les joueurs sont déconnectés n'abandonne qu'à l'expiration des délais
  (`GameCycle` -> `checkDisconnected`) ; si plus PERSONNE n'est connecté, la
  partie s'arrête sans vainqueur. Un joueur qui revient trop tard est ramené au
  lobby local à sa connexion (`ArenaManager.markStranded`, en mémoire).
- **Kits** (`kits.yml`) : au départ on n'a que le kit par défaut ; les autres
  se débloquent **en jouant**, d'après les stats de carrière (`requires:`
  parties, victoires, kills, points...), ou par permission (kit VIP). Choix au
  lobby (item épée « Kits », slot 2) ou par `/tt kit [kit]` ; retenu d'une
  partie à l'autre, applicable en partie à la prochaine réapparition. Un kit
  débloqué pendant une partie est annoncé à la fin. `thetower.kits.all` (op)
  débloque tout. Les kits d'origine de BadBlock sont perdus (fichiers JSON sur
  leurs serveurs, jamais publiés, cf. `ApiCore` de l'archive badblock-game/API0) :
  ceux fournis sont une proposition.
- **Mort** : réapparition immédiate à la base avec le kit choisi (et l'armure
  en cuir teinte de l'équipe). Les objets tombent (sauf l'armure en cuir). Chute dans
  le vide = mort immédiate (via l'événement de dégâts VOID, donc valable que le
  vide soit à Y=0 ou Y=-64). Messages de mort limités à l'arène.
- **Séries de kills** (double, triple, quadruple, plus) dans une fenêtre de 8 s,
  et annonce quand on met fin à la série de quelqu'un (reprises de BadBlock).
- **Construction libre** sur la map (régénérée en fin de partie), sauf :
  zones de spawn et piscines protégées (blocs, seaux, explosions), coffres
  incassables et non posables, pistons interdits.
- **Zone des coffres** : seuls les membres de l'équipe y ouvrent les conteneurs.
- **Générateurs** : types libres dans config.yml (défaut BadBlock : fer 3 s,
  XP 1,5 s, diamant 40 s), un seul item au sol par générateur par défaut.
- **Arcs** interdictibles par arène : retirés du kit et de l'établi.
- **Fin** : titres victoire/défaite, feux d'artifice, stats + ELO, résumé
  (meilleur marqueur, plus de kills, plus de dégâts), retour au lobby après
  `end-delay`.
- Le niveau d'XP n'est **jamais** touché en partie : c'est la monnaie
  d'enchantement. Il ne sert de minuteur que pendant le compte à rebours.

---

## État et limites connues

- Build vert, jar produit, YAML validés par les classes de Bukkit, jeu de clés
  fr/en identique et complet, vérifications 1 à 3 passées.
- **Aucun test sur serveur réel**, ni en 1.8.8 ni en 1.21. Toute la validation
  est statique.
- **Non repris de BadBlock** (volontairement, pour une première version) :
  la loterie du beacon, le mode « TowerRun », les niveaux de kit, le choix
  d'équipe au lobby, les montures du lobby.
- Un joueur déconnecté qui ne revient pas avant la fin de la partie n'a ni
  victoire ni défaite comptée (ses stats ne sont plus en cache).
- **bStats absent** : il faudrait enregistrer TheTower sur bstats.org pour
  obtenir son propre ID de service (celui d'AntWars ne doit pas être réutilisé).
- Deux langues seulement (fr, en). Ajouter une langue = copier `en.yml`.
- La « zone de jeu » d'une arène (`Arena.getArea()`) est la boîte englobant
  spawns, piscines, zones et générateurs, **+48 blocs**. Elle sert à rattacher
  explosions/feu/chutes de sable à une partie et à nettoyer les items au sol.
  Si une map s'étend plus loin, les changements hors de cette boîte qui ne
  viennent pas d'un joueur ne seront pas régénérés.
- Restauration de la map synchrone (comme AntWars) : bref pic possible sur une
  partie très détruite ; l'arène est vide à ce moment.

---

## Style

**Messages joueurs** : uniquement des clés, `Messages.tr("clé", "var", valeur)`,
textes dans `lang/<code>.yml` (UTF-8, couleurs en `&`, variables en `%var%`).
Ajouter un message = l'ajouter dans **les deux** fichiers de langue.

**Commentaires et logs console** sans accents (encodage des consoles Windows).
Commenter le *pourquoi*, surtout chaque contournement de compatibilité.
