# ReadFlow — Rapport de Prototype Sherpa-ONNX

> **Statut :** ⬜ À faire  
> **Date cible :** 2026-07-14  
> **Objectif :** Valider la faisabilité des timestamps mot/milliseconde avec Sherpa-ONNX sur Android

---

## Objectifs du prototype

1. Intégrer Sherpa-ONNX dans un projet Android minimal
2. Charger un modèle VITS français (~60 Mo)
3. Synthétiser 10 phrases de test variées (courtes, longues, avec dialogues, nombres)
4. Mesurer la précision des timestamps mot/milliseconde
5. Mesurer le RTF (Real-Time Factor) sur au moins 2 devices

---

## Configuration de test

| Paramètre | Valeur |
|---|---|
| **Appareils** | À définir (Snapdragon, MediaTek, Tensor) |
| **Modèle** | VITS français (à sélectionner) |
| **Phrases test** | 10 phrases (voir ci-dessous) |
| **Métrique clé** | RTF < 1.0, timestamps corrects à ±50ms |

---

## Phrases de test

1. "Bonjour, comment allez-vous aujourd'hui ?"
2. "Le chat noir dort paisiblement sur le canapé du salon."
3. « Je ne pense pas que ce soit une bonne idée », murmura-t-il.
4. "M. Dupont habite au 42, rue du Dr. Martin — c'est à côté de l'église."
5. "Les oiseaux migrateurs parcourent parfois plus de dix mille kilomètres sans s'arrêter..."
6. "Il était une fois, dans une contrée lointaine, un roi qui aimait par-dessus tout les livres."
7. "La Révolution française de 1789 a profondément transformé la société de l'époque."
8. "Quand est-ce qu'on mange ? J'ai une faim de loup !"
9. "L'intelligence artificielle permet aujourd'hui de synthétiser des voix d'une qualité remarquable."
10. "N'oubliez pas d'acheter : du pain, du beurre, des œufs, du fromage et des fruits."

---

## Résultats

| Métrique | Cible | Résultat | Statut |
|---|---|---|---|
| **Chargement modèle** | < 5s | — | ⬜ |
| **RTF moyen** | < 1.0 | — | ⬜ |
| **Précision timestamps** | ±50ms | — | ⬜ |
| **Phrases correctement timées** | 10/10 | — | ⬜ |
| **Mémoire utilisée** | < 200 Mo | — | ⬜ |

---

## Problèmes rencontrés

*(À remplir pendant le test)*

---

## Conclusion

*(À remplir après le test — Go/No-Go pour Sherpa-ONNX)*

- [ ] **GO** — Sherpa-ONNX validé, poursuivre avec cette stack
- [ ] **NO-GO** — Problème bloquant, envisager Piper ou autre solution
