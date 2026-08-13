# Vérification Device — Pipeline EPUB v3

**Date** : 2026-08-12
**Branche** : `feature/refonte-pipeline-epub-paliers-0-1`
**Appareil cible** : Snapdragon 680 (V2206), Android 14

## Protocole

### 1. Ouverture

- [ ] EPUB complexe (images + gras/italique/liens) : ouverture < 500ms
- [ ] EPUB simple (texte seul) : ouverture < 300ms
- [ ] EPUB volumineux (> 100 chapitres) : TOC affichée sans lag

### 2. Rendu SCROLL

- [ ] Gras (`<b>`, `<strong>`) visible
- [ ] Italique (`<i>`, `<em>`) visible
- [ ] Liens (`<a href>`) affichés en bleu souligné
- [ ] Images réelles affichées (pas de placeholder gris)
- [ ] Pas de layout shift au chargement des images
- [ ] Défilement > 55 FPS (profilage GPU)
- [ ] Pas de crash de texture sur chapitre > 100 000 caractères

### 3. Rendu PAGED

- [ ] Pagination fluide (swipe horizontal)
- [ ] Batching : pas de crash sur chapitre long
- [ ] Images en mode PAGED : écart assumé (pas d'images)

### 4. TTS + Surlignage

- [ ] Surlignage mot-à-mot fonctionnel
- [ ] Pont `Sentence.blockIndex` → `globalOffsetRange` : le surlignage suit le bon bloc
- [ ] Changement de chapitre automatique en fin de chapitre
- [ ] Skip sentence : avant/arrière fluide

### 5. Fragments TOC

- [ ] Navigation vers `#section` : extraction exacte (pas d'en-tête parasite)
- [ ] Navigation vers `#prologue` : seul le prologue est affiché

### 6. Recherche FTS

- [ ] Recherche plein texte fonctionnelle
- [ ] Résultats cliquables → navigation correcte

### 7. Signets / Annotations

- [ ] Création de signet à la position courante
- [ ] Surlignage d'annotation persistant à la réouverture
- [ ] Navigation depuis la liste de signets

### 8. Accessibilité

- [ ] TalkBack annonce les titres (`heading()`)
- [ ] TalkBack annonce le contenu des paragraphes (`contentDescription`)
- [ ] TalkBack ignore les séparateurs (`invisibleToUser()`)

### 9. Performance (Palier 4)

- [ ] Zapping rapide de 10 chapitres : pas de ralentissement
- [ ] Préchargement N+1 effectif (pas de page blanche au chapitre suivant)
- [ ] Pas de crash OOM après navigation prolongée
- [ ] Fermeture du lecteur : cache LRU vidé, mémoire libérée

### 10. Non-régression

- [ ] PDF : rendu bitmap intact
- [ ] TXT : texte brut intact
- [ ] Import EPUB : pas de régression
- [ ] Mode sombre / sépia : couleurs correctes
