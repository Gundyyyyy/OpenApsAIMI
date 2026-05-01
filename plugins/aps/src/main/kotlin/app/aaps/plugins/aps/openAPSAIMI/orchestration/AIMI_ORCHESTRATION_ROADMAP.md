# AIMI orchestration — roadmap (tick context & pipeline)

Medical loop code must stay **behavior-identical** unless a change is explicitly reviewed. This document tracks **refactoring only** (structure, readability, test hooks), not medical logic.

**Dernière mise à jour doc :** même base P2 ; ajout **orchestration Meal Advisor** (`tryMealAdvisor` : ordre vs safety, `modesCondition`, retour immédiat). Rappel extraction T3c : **`runT3cBrittleBypassOrReturn`** après `applyLegacyMealModes`. Vérifier le dernier commit local pour l’état exact du code.

## Réalisé

### Phase 1 — Comparator gate
- Préférence `BooleanKey.OApsAIMIAimiSmbComparatorEnabled` (défaut `false`) pour désactiver le comparateur SMB sans retirer le code.
- `AimiSmbComparator` sort tôt si la préférence est fausse.

### Phase 2 — Tick context (en cours)
- **`AimiTickContext`** (`orchestration/AimiTickContext.kt`) : regroupe les paramètres d’entrée de `determine_basal` (glucose, temp, IOB[], profil, autosens, repas, flags temps, UI, debug).
- **`runEarlyDetermineBasalStages(ctx)`** dans `DetermineBasalaimiSMB2` : même séquence qu’avant — `beginInvocation`, clear caches, pulse telemetry, reset flags, bruit CGM, `extraDebug`, `hydrateMealDataIfTriggered`, flags advisor/TDD, copie **`originalProfile`**, `enterPhase(BOOTSTRAP)`.
- **`AimiDetermineBasalEarlyTickState`** : `originalProfile`, `isExplicitAdvisorRun`, `tdd7P`, `tdd7Days` (valeurs figées après bootstrap numérique TDD).
- **Prélude post-bootstrap** : lectures alignées sur `ctx` de l’autopilot gestationnel / thyroïde jusqu’au multiplicateur basal unifié, `AimiDecisionContext`, init `RT`, SOS d’urgence, puis recalcul **local** de `flatBGsDetected` (override delta) — inchangé fonctionnellement.
- **Corps principal `determine_basal`** : la majorité des lectures d’entrée utilisent désormais `ctx` (IOB profiler, PKPD précoce, BYODA G6, tube advisor, branches MAXSMB pente/ plateau, **`runT3cBrittleBypassOrReturn`** pour le chemin T3c brittle + shadow Autodrive, préparation signal / autosens, Autodrive V3 gate + état, cibles autosens, recomput PKPD, comparateur SMB, WCycle learning, auditeur, finalisation `decisionCtx` ISF). Les paramètres bruts ne subsistent que pour construire `AimiTickContext` et dans d’autres méthodes de la classe.
- **Harmonisation `ctx.currentTemp` / `ctx.mealData` / `ctx.microBolusAllowed` / `ctx.currentTime`** : tous les `setTempBasal` / `calculateRate` / comparaisons de TBR actif dans `determine_basal`, flux SMB (`enablesmb`, `finalizeAndCapSMB`, `executeSmbInstruction`), agrégats COB / pentes repas, NGR, moteur basal, PKPD meal path — sans toucher à `setTempBasal()` ni `applyLegacyMealModes()` (paramètres locaux inchangés).
- **P2 (amorcé)** : **`bootstrapPhysiologyAfterEarlyTick(ctx, tdd7Days)`** — regroupe autopilot gestationnel, résumé physiologique / IOB précoce, multiplicateurs basaux harmonisés, flag « confirmed high rise », module thyroïde ; appelé depuis `determine_basal` juste après `runEarlyDetermineBasalStages`.
- **P2 (suite)** : **`AimiTickDecisionRtBootstrap`** + **`buildDecisionContextInitRtSosAndFlatShadow(ctx)`** — construction `AimiDecisionContext`, `RT` initial, phase CONTEXT, raison `extraDebug`, SOS global, `logLearnersHealth`, reset WCycle / `lastProfile`, et **`flatBGsDetected` local** (override delta) ; `lastProfile` aligné sur **`ctx.profile`** (même référence que le paramètre `profile`).
- **P2 (suite)** : **`AimiRealtimePhysioIobBootstrap`** + **`runRealtimePhysioIobProfilerAndInsulinObserver(ctx, decisionCtx)`** — log activité RT, reset `maxSMB` / `maxSMBHB`, snapshot physio → `decisionCtx.adjustments.physiological_context`, `InsulinActionProfiler`, `iobActivityNow` membre, observateur insuline + logs PAI / PKPD_OBS.
- **P2 (suite)** : **`AimiGlucosePackLoadOutcome`** (`Abort` / `Continue`) + **`ensureWCycleAndLoadGlucoseStatusOrAbort(ctx, rT)`** — `ensureWCycleInfo()`, `glucoseStatusCalculatorAimi.compute(true)`, mêmes logs / early return / `ensurePredictionFallback` / `markFinalLoopDecisionFromRT` qu’avant ; le corps de `determine_basal` déstructure `glucoseStatus` et `AimiBgFeatures?` via un `when` exhaustif (smart-cast Kotlin).
- **P2 (suite)** : **`AimiT9PhysioPkpdTubeBootstrap`** + **`runT9PhysioEarlyPkpdAndTubeBootstrap`** — bloc T9 (log G6 lead), physio multipliers + trace, PKPD précoce + `cachedPkpdRuntime`, application physio / inflammation sur limites, echo TAP-G, tube advisor ; retourne `pumpAgeDays` + **`physioMultipliers`** (encore lus plus loin dans le tick : trajectoire / caps).
- **P2 (suite)** : **`AimiCombinedDeltaAndPeakTick`** + **`runCombinedDeltaByodaAndDynamicPeak`** — deltas récents, delta combiné, compensateur BYODA jour/nuit, `calculateDynamicPeakTime` vs `profile.peakTime` ; **volontairement avant** `Therapy` / `applyLegacyMealModes` (modes repas inchangés).
- **P2 (suite)** : **`AimiTickClockTirCarbGlucoseBootstrap`** + **`runTickClockMaxSmbTirCarbAndGlucoseCopy`** — `hourOfDay`, honeymoon, historique SMB, maxIOB/maxSMB (plateau/pente), NGR, snapshot TIR, `now`, glucides/tags, copie δ GS sur membres ; **stop avant** `Therapy` (horloges bfast/lunch/dinner/snack/highcarb inchangées).
- **P2 (suite)** : **`AimiTherapyExerciseGate`** (`Continue` / `ReturnEarly`) + **`runTherapyHydrateClocksAndExerciseLockoutGate`** — `Therapy`, CSV trim si delete, flags repas + runtimes, accélération/stabilité, `nightbis`, lockout sport → SMB 0, retours anticipés T3c / standard ; **juste avant** le bloc « MANUAL MEAL MODES » / `applyLegacyMealModes`.
- **P2 (suite)** : **`runT3cBrittleBypassOrReturn`** dans `DetermineBasalAIMI2` — extraction **mécanique** du bloc `BooleanKey.OApsAIMIT3cBrittleMode` : garde prébolus (historique bolus 20 min, lockout `internalLastSmbMillis`, plafond IOB vs `maxIob`), tick **shadow** Autodrive (DataLake / learner, sans commande), `applyAdvancedPredictions` + `applyTrajectoryAnalysis` avec **paramètres explicites** (`insulinActionState` depuis le bootstrap physio/IOB du tick, `physioMultipliers`, `pkpdRuntime`, `shortAvgDeltaAdj`, `originalProfile`, etc.), puis `executeT3cBrittleMode` comme avant. **`null`** si brittle désactivé (aucun effet). **Invariant d’ordre** : appel **immédiatement après** `applyLegacyMealModes` — le commentaire/code suppose que le prébolus legacy a déjà été traité en amont.
- **Orchestration — Meal Advisor** (`tryMealAdvisor` dans `DetermineBasalAIMI2`, pas une extraction P2 à ce stade) :
  - **Placement** : dans le pipeline « DECISION », **après** **`trySafetyStart`** (LGS / hypo / stale — si `DecisionResult.Applied`, **`return rT`** et le Meal Advisor n’est pas évalué).
  - **Si appliqué** : TBR éventuel, bolus « direct send » (plafond matériel 30 U dans le code actuel), `logDecisionFinal`, **`markFinalLoopDecisionFromRT`**, puis **`return rT`** — les étapes SMB/TBR aval **ne peuvent pas** écraser l’intention prébolus / advisor (aligné sur le commentaire inline : même principe que les modes repas explicites).
  - **Coordination modes legacy** : `modesCondition` (défini **après** `applyLegacyMealModes` / T3c) — dans `tryMealAdvisor`, la branche utile exige **`modesCondition || isExplicitTrigger`** ; si une fenêtre mode repas legacy est encore « active » au sens de ce booléen, l’advisor **auto** est ignoré (`ADVISOR_SKIP … legacy mode active`), sauf **déclencheur explicite** `BooleanKey.OApsAIMIMealAdvisorTrigger` / `isExplicitAdvisorRun` (Snap&Go).
  - **One-shot** : flag trigger consommé dans `tryMealAdvisor` quand `isExplicitTrigger` et chemin appliqué.

### Branche / build
- Travail sur `feature/aimi-phase2-tick-context`.
- Cible de compilation : `:plugins:aps:compileFullDebugKotlin`.

## Invariants à ne pas casser

1. **Références partagées** : `ctx.profile`, `ctx.mealData`, etc. pointent vers les **mêmes instances** que les paramètres de `determine_basal`. Une mutation en place du profil ou des repas reste visible via `ctx` — ce n’est pas une copie profonde.
2. **`flatBGsDetected` est ré-ombrelé** : après `val flatBGsDetected = if (...)`, le reste de la fonction doit utiliser le **`flatBGsDetected` local** (override delta), **pas** `ctx.flatBGsDetected` (valeur brute d’entrée).
3. **Ordre des effets** : tout déplacement de code doit préserver l’ordre des effets de bord (caches, telemetry, hydration repas, phases loop).
4. **T3c brittle** : ne pas déplacer **`runT3cBrittleBypassOrReturn`** avant **`applyLegacyMealModes`** (ni le fusionner avec le gate therapy) sans revue — le garde-fou prébolus et les logs restent cohérents seulement si les modes repas manuels / prébolus legacy ont déjà tourné.
5. **Meal Advisor** : garder **`trySafetyStart` avant `tryMealAdvisor`** ; si l’advisor **applique** une décision, le **`return rT`** final doit rester **après** la pose TBR / bolus intent — ne pas réordonner pour laisser passer SMB standard par-dessus. **`modesCondition`** et **`isExplicitAdvisorRun`** doivent rester alignés avec la définition actuelle des fenêtres legacy (sinon double prébolus ou advisor pendant mode manuel).

## Reste à faire (priorité suggérée)

| Priorité | Tâche | Risque |
|----------|--------|--------|
| P1 | Finition : revue des commentaires dupliqués / obsolètes ; pas de changement fonctionnel attendu | Très faible |
| P2 | Poursuivre **étapes nommées** : ~~pack GS~~, ~~T9/PKPD/tube~~, ~~delta/BYODA/tp~~, ~~clock/TIR/carb~~, ~~Therapy + exercise gate~~, ~~extrait T3c brittle (`runT3cBrittleBypassOrReturn`)~~ ; orchestration Meal Advisor **documentée** dans ce fichier ; suite (extraction nommée advisor optionnelle, pipeline safety typée, autodrive hors shadow) par tranches | Moyen |
| P3 | Introduire une **pipeline** typée (sealed `AimiTickPhase` ou séquence de stages) derrière la même API publique `determine_basal` | Plus élevé — à valider |
| P4 | Tests unitaires ciblés sur bootstrap + shadow flat (golden logs ou snapshots limités) | Dépend de la faisabilité dans le module |

## Hors scope immédiat

- Modifier les moteurs ML, l’advisor async, ou les garde-fous safety.
- Ajouter des dépendances inter-modules sans accord.

## Validation recommandée

- `:plugins:aps:compileFullDebugKotlin --no-daemon`
- Smoke test runtime : un cycle loop complet avec comparateur off, vérifier logs et décision SMB/basal attendus.
- Smoke T3c : brittle **on** vs **off** ; avec mode repas / prébolus legacy actif, vérifier que l’ordre therapy → legacy → T3c n’a pas régressé (logs garde prébolus).
- Smoke Meal Advisor : déclencheur explicite (Snap&Go) vs passif (carbs estimés + `modesCondition` true) ; avec **mode legacy** actif, vérifier skip auto et/ou chemin explicite selon attente ; après apply, confirmer qu’aucun SMB aval ne modifie `rT`.
