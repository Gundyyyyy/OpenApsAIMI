# AIMI orchestration — roadmap (tick context & pipeline)

Medical loop code must stay **behavior-identical** unless a change is explicitly reviewed. This document tracks **refactoring only** (structure, readability, test hooks), not medical logic.

**Dernière mise à jour doc :** suite à l’extraction `bootstrapPhysiologyAfterEarlyTick` (P2) ; branche `feature/aimi-phase2-tick-context`. Vérifier le dernier commit local pour l’état exact du code.

## Réalisé

### Phase 1 — Comparator gate
- Préférence `BooleanKey.OApsAIMIAimiSmbComparatorEnabled` (défaut `false`) pour désactiver le comparateur SMB sans retirer le code.
- `AimiSmbComparator` sort tôt si la préférence est fausse.

### Phase 2 — Tick context (en cours)
- **`AimiTickContext`** (`orchestration/AimiTickContext.kt`) : regroupe les paramètres d’entrée de `determine_basal` (glucose, temp, IOB[], profil, autosens, repas, flags temps, UI, debug).
- **`runEarlyDetermineBasalStages(ctx)`** dans `DetermineBasalaimiSMB2` : même séquence qu’avant — `beginInvocation`, clear caches, pulse telemetry, reset flags, bruit CGM, `extraDebug`, `hydrateMealDataIfTriggered`, flags advisor/TDD, copie **`originalProfile`**, `enterPhase(BOOTSTRAP)`.
- **`AimiDetermineBasalEarlyTickState`** : `originalProfile`, `isExplicitAdvisorRun`, `tdd7P`, `tdd7Days` (valeurs figées après bootstrap numérique TDD).
- **Prélude post-bootstrap** : lectures alignées sur `ctx` de l’autopilot gestationnel / thyroïde jusqu’au multiplicateur basal unifié, `AimiDecisionContext`, init `RT`, SOS d’urgence, puis recalcul **local** de `flatBGsDetected` (override delta) — inchangé fonctionnellement.
- **Corps principal `determine_basal`** : la majorité des lectures d’entrée utilisent désormais `ctx` (IOB profiler, PKPD précoce, BYODA G6, tube advisor, branches MAXSMB pente/ plateau, T3c shadow + brittle return, préparation signal / autosens, Autodrive V3 gate + état, cibles autosens, recomput PKPD, comparateur SMB, WCycle learning, auditeur, finalisation `decisionCtx` ISF). Les paramètres bruts ne subsistent que pour construire `AimiTickContext` et dans d’autres méthodes de la classe.
- **Harmonisation `ctx.currentTemp` / `ctx.mealData` / `ctx.microBolusAllowed` / `ctx.currentTime`** : tous les `setTempBasal` / `calculateRate` / comparaisons de TBR actif dans `determine_basal`, flux SMB (`enablesmb`, `finalizeAndCapSMB`, `executeSmbInstruction`), agrégats COB / pentes repas, NGR, moteur basal, PKPD meal path — sans toucher à `setTempBasal()` ni `applyLegacyMealModes()` (paramètres locaux inchangés).
- **P2 (amorcé)** : **`bootstrapPhysiologyAfterEarlyTick(ctx, tdd7Days)`** — regroupe autopilot gestationnel, résumé physiologique / IOB précoce, multiplicateurs basaux harmonisés, flag « confirmed high rise », module thyroïde ; appelé depuis `determine_basal` juste après `runEarlyDetermineBasalStages`.

### Branche / build
- Travail sur `feature/aimi-phase2-tick-context`.
- Cible de compilation : `:plugins:aps:compileFullDebugKotlin`.

## Invariants à ne pas casser

1. **Références partagées** : `ctx.profile`, `ctx.mealData`, etc. pointent vers les **mêmes instances** que les paramètres de `determine_basal`. Une mutation en place du profil ou des repas reste visible via `ctx` — ce n’est pas une copie profonde.
2. **`flatBGsDetected` est ré-ombrelé** : après `val flatBGsDetected = if (...)`, le reste de la fonction doit utiliser le **`flatBGsDetected` local** (override delta), **pas** `ctx.flatBGsDetected` (valeur brute d’entrée).
3. **Ordre des effets** : tout déplacement de code doit préserver l’ordre des effets de bord (caches, telemetry, hydration repas, phases loop).

## Reste à faire (priorité suggérée)

| Priorité | Tâche | Risque |
|----------|--------|--------|
| P1 | Finition : revue des commentaires dupliqués / obsolètes ; pas de changement fonctionnel attendu | Très faible |
| P2 | Poursuivre **étapes nommées** après `bootstrapPhysiology…` : ex. bloc `AimiDecisionContext` + SOS + `flatBGsDetected` local + init `RT` + `CONTEXT` (une extraction à la fois) | Moyen |
| P3 | Introduire une **pipeline** typée (sealed `AimiTickPhase` ou séquence de stages) derrière la même API publique `determine_basal` | Plus élevé — à valider |
| P4 | Tests unitaires ciblés sur bootstrap + shadow flat (golden logs ou snapshots limités) | Dépend de la faisabilité dans le module |

## Hors scope immédiat

- Modifier les moteurs ML, l’advisor async, ou les garde-fous safety.
- Ajouter des dépendances inter-modules sans accord.

## Validation recommandée

- `:plugins:aps:compileFullDebugKotlin --no-daemon`
- Smoke test runtime : un cycle loop complet avec comparateur off, vérifier logs et décision SMB/basal attendus.
