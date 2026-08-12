## La Cible : Un Pipeline basé sur un AST (Abstract Syntax Tree)

Le problème actuel est que l'application extrait du *texte brut* (`String`). Un livre n'est pas une longue chaîne de caractères : c'est un document structuré (DOM).

La cible consiste à transformer le HTML du livre en un modèle de données natif Kotlin (l'AST), totalement indépendant d'Android, que Compose pourra ensuite lire pour dessiner l'écran.

### 1. Couche Domain (Le Modèle Indépendant)

Le `DocumentModel` ne doit plus être une simple liste de phrases. Il doit utiliser des `sealed classes` pour représenter la sémantique du livre, bloc par bloc.

* **Le Squelette :** Le livre est une liste de chapitres. Un chapitre est une liste de `BookBlock`.
* **Les Blocs (Structure) :**
* `ParagraphBlock` : Contient du texte riche.
* `HeadingBlock` : Titres de chapitres (h1, h2...).
* `ImageBlock` : Contient l'URI relative de l'image.
* `SeparatorBlock` : Les sauts de scène (ex: `***`).


* **Le Texte Riche (Inline) :** Au sein d'un `ParagraphBlock`, le texte n'est pas un simple `String`. C'est un objet qui contient le texte brut ET une liste de `Span` (décalages). Ex: "Le texte est **important**" devient `texte = "Le texte est important"` avec un attribut `Style(type = BOLD, start = 13, end = 22)`.

> **Pourquoi c'est vital :** La couche Domain ne connaît ni Readium, ni HTML, ni `AnnotatedString`. C'est du pur Kotlin, hautement testable unitairement.

### 2. Couche Data (Le Parseur Sémantique et Paresseux)

C'est ici que Readium intervient, mais **uniquement comme gestionnaire d'archives**. Readium ouvre le ZIP, lit la table des matières (Spine), et fournit les flux (Streams) des fichiers XHTML.

* **L'Extraction par Chapitre :** Lorsqu'on demande le chapitre 5, le parseur récupère le fichier XHTML exact via Readium.
* **Le Parsing DOM :** Au lieu d'utiliser un extracteur de texte brut, vous utilisez un parseur DOM (comme Jsoup, très standard et léger en Java/Kotlin). Le parseur traverse l'arbre HTML nœud par nœud :
* Il croise un `<p>` ? Il crée un `ParagraphBlock`.
* Il croise un `<b>` ou `<em>` dans ce `<p>` ? Il ajoute un `Span` de style au bloc en cours.
* Il croise une `<img>` ? Il crée un `ImageBlock` et capture l'attribut `src`.


* **Résolution du bug "Prologue" :** Un vrai parseur DOM comprend la hiérarchie. S'il y a des ancres (fragments `#prologue`), le parseur sait où commence et où s'arrête exactement le fragment demandé dans le fichier, évitant de répéter l'en-tête du document parent.

### 3. Couche Presentation (Le Rendu Compose)

L'UI devient extrêmement "bête" et réactive. Le `ReaderViewModel` demande le chapitre courant, reçoit la liste des `BookBlock` de la couche Domain, et la mappe pour Jetpack Compose.

* **Mapper UI :** Les `Span` du Domain sont convertis en `AnnotatedString.Builder` (spécifique à Compose).
* **La LazyColumn :** Elle itère simplement sur les blocs :
* Si c'est un `ParagraphBlock` -> Affiche un composable `Text`.
* Si c'est un `ImageBlock` -> Affiche un composable `AsyncImage` (qui ira chercher l'image dans l'archive via un intercepteur Coil branché sur Readium).



---

## La Séquence d'Exécution Cible (UX / Performance)

Avec cette architecture, voici ce qui se passe quand l'utilisateur ouvre un livre complexe (images + formatage) :

1. **T0 :** Ouverture du livre. Readium parse le manifest (instantané).
2. **T+100ms :** L'UI s'affiche avec un skeleton loader (`isLoading = true`). Le ViewModel demande **uniquement** le chapitre courant (issu de la sauvegarde Room) à la couche Data.
3. **T+300ms :** Le Data layer utilise Jsoup pour convertir le XHTML du chapitre en `BookBlock`.
4. **T+500ms :** Le texte riche et les images du chapitre s'affichent. L'utilisateur peut lire.
5. **En arrière-plan :** Le ViewModel lance le traitement (parsing Jsoup + tokenisation des phrases pour le TTS) des chapitres suivants et précédents.


