(ns installation.phase
  "Phase 0->3 staged rollout -- the industrial-machinery-installation-
  coordination analog of `demolition.phase`/`construction.phase`.

    Phase 0  read-only              -- no writes, still governor-gated.
    Phase 1  assisted-logging       -- `:log-installation-record` allowed,
                                       every write needs human approval.
    Phase 2  assisted-coordination  -- adds `:flag-safety-concern` and
                                       `:order-supplies` writes, still
                                       approval.
    Phase 3  supervised-coordination -- adds `:schedule-installation-
                                       operation`; governor-clean,
                                       high-confidence `:log-installation-
                                       record` (pure data logging, no
                                       capital/safety risk) AND `:order-
                                       supplies` BELOW the cost threshold
                                       may auto-commit.

  ## Actuation (there is none -- read this before changing this file)

  This actor performs NO real-world actuation. Every proposal it can ever
  produce carries `:effect :propose` (see `installation.governor` ns
  docstring checks 1-4) -- 'committing' a proposal here means only that a
  coordination artifact (an installation-record-log entry, a schedule
  PROPOSAL, a safety-concern flag, a supply-order PROPOSAL) is now logged
  in the SSoT + audit ledger. It never dispatches heavy-lift/rigging
  equipment, never signs off on commissioning/energization -- that
  authority is the licensed engineer / site supervisor's exclusively.

  `:schedule-installation-operation` and `:flag-safety-concern` are
  DELIBERATELY ABSENT from every phase's `:auto` set, including phase 3
  -- a permanent structural fact, not a rollout milestone still to come.
  Proposing a rigging/alignment/commissioning-test schedule window
  coordinates potential heavy-lift/rigging-equipment dispatch and
  pre-energization work, and surfacing a rigging/lockout-tagout/
  energization-hazard concern is exactly the judgment this actor must
  never let auto-commit; both are always a human's call. `installation.
  governor`'s `high-stakes` set enforces the same invariant independently
  -- two layers, not one, agree on this (see `installation.governor` ns
  docstring).

  `:log-installation-record` (rigging-plan/alignment/progress DATA
  LOGGING, no direct capital or safety risk) and `:order-supplies` BELOW
  the cost threshold (see `installation.governor/supply-order-cost-
  threshold-usd`) ARE members of phase 3's `:auto` set -- but the
  governor's own cost-threshold/confidence-floor check can still force
  `:order-supplies` to escalate even when it's `:auto`-eligible, the same
  'phase says maybe, governor decides' layering `construction.phase`
  established for `:actuation/dispatch-alert`.")

(def read-ops  #{})
(def write-ops #{:log-installation-record :schedule-installation-operation
                 :flag-safety-concern :order-supplies})

;; NOTE the invariant: `:schedule-installation-operation`/`:flag-safety-
;; concern` are members of `write-ops` (governor-gated like any write) but
;; are NEVER members of any phase's `:auto` set below. Do not add them
;; there -- see ns docstring 'Actuation' section above before changing
;; this.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"                 :writes #{}                                            :auto #{}}
   1 {:label "assisted-logging"          :writes #{:log-installation-record}                     :auto #{}}
   2 {:label "assisted-coordination"     :writes #{:log-installation-record :flag-safety-concern
                                                    :order-supplies}                              :auto #{}}
   3 {:label "supervised-coordination"   :writes write-ops
      :auto #{:log-installation-record :order-supplies}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:schedule-installation-operation`/`:flag-safety-concern` are never
    auto-eligible at any phase, so they always escalate once the governor
    clears them (or hold if the governor doesn't). `:log-installation-
    record` and `:order-supplies` MAY auto-commit at phase 3 -- see ns
    docstring."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map an Installation Governor verdict to a base disposition before the
  phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
