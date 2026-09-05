(ns installation.governor
  "Installation Governor -- the independent compliance layer named in
  `blueprint.edn` (`:itonami.blueprint/governor :installation-governor`)
  that earns the Installation Advisor the right to commit. The LLM has no
  notion of industrial-machinery-installation safety law, whether a
  site's own recorded lift plan / installation-notification filing
  actually satisfies its jurisdiction's requirements, or when a proposal
  has quietly drifted outside this actor's charter into heavy-lift/
  rigging-equipment control or commissioning-energization sign-off, so
  this MUST be a separate system able to *reject* a proposal and fall
  back to HOLD -- the industrial-machinery-installation analog of
  `cloud-itonami-isic-4211`'s Construction Governor.

  ## Scope -- this is a COORDINATION-ONLY actor

  This actor NEVER controls heavy-lift/rigging equipment and NEVER signs
  off on commissioning/energization -- that authority is the licensed
  engineer / site supervisor's EXCLUSIVELY. Unlike `construction.
  governor`'s sibling checks (which gate SIX real-world actuation effects
  such as `:site/mark-alert-dispatched` or `:build/dispatch-placement`),
  every proposal this actor's Installation Advisor can produce carries
  `:effect :propose` and NOTHING else -- committing a proposal here means
  'this coordination artifact is now logged/scheduled/flagged/ordered',
  never 'a crane moved' or 'a machine is authorized to be energized'.
  Checks 1-4 below encode this scope as STRUCTURAL, permanent HARD holds
  -- not policy that could be relaxed by a future phase, unlike checks
  5-8, which are ordinary per-jurisdiction/ground-truth safety gates.

  Eight checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them.

    1. Unknown op                -- the proposal's `:op` is outside the
                                     CLOSED four-op allowlist
                                     (`:log-installation-record` /
                                     `:schedule-installation-operation` /
                                     `:flag-safety-concern` / `:order-
                                     supplies`). Permanent, structural.
    2. Effect is not :propose    -- ANY proposal whose `:effect` is not
                                     literally `:propose` is rejected,
                                     unconditionally. Defense-in-depth: a
                                     compromised/malfunctioning advisor
                                     can never slip a real-world actuation
                                     effect past this governor. Permanent,
                                     structural.
    3. Forbidden action class    -- a proposal whose `:value` carries a
                                     `:heavy-lift-equipment-control?` /
                                     `:direct-actuation?` / `:commission
                                     ing-energization-sign-off?` marker
                                     true is rejected, unconditionally --
                                     even though this actor's own mock
                                     advisor never sets these, the
                                     governor checks independently so a
                                     compromised advisor gains nothing by
                                     trying. Permanent, structural,
                                     un-overridable by ANY human approval
                                     -- see README `Actuation`.
    4. Site not independently
       verified/registered        -- for `:schedule-installation-
                                     operation` / `:flag-safety-concern` /
                                     `:order-supplies`, the site's own
                                     recorded `:site-verified?`
                                     ground-truth field (set only via a
                                     separately-committed `:log-
                                     installation-record`, never trusted
                                     from the CURRENT proposal's own
                                     confidence) must be true.
    5. Legal-basis missing       -- for `:schedule-installation-
                                     operation`, did the proposal cite an
                                     OFFICIAL source (`installation.
                                     facts`), or invent one for an
                                     uncovered jurisdiction?
    6. Lift plan incomplete      -- for `:schedule-installation-
                                     operation`, has the site's own
                                     recorded `:lift-plan-approved?`
                                     ground-truth field actually been set
                                     true?
    7. Notification lead time
       insufficient               -- for `:schedule-installation-
                                     operation` in a `:quantitative`-
                                     threshold jurisdiction (JPN only in
                                     this catalog), INDEPENDENTLY
                                     recompute whether the site's own
                                     recorded `:installation-notice-lead-
                                     days-actual` meets its jurisdiction's
                                     regulatory minimum (`installation.
                                     facts/notification-lead-
                                     insufficient?`) -- needs no proposal
                                     inspection at all. DELIBERATELY does
                                     not fire for `:qualitative`
                                     jurisdictions (USA/DEU in this
                                     catalog) -- there is no numeric
                                     bright line to independently re-check
                                     there; the Installation Governor's
                                     permanent high-stakes gate (the
                                     `high-stakes` set below) already
                                     routes `:schedule-installation-
                                     operation` to a human EVERY time
                                     regardless of jurisdiction, so this
                                     HARD check adds a bright-line floor
                                     only where the law itself gives one.
    8. Unresolved safety concern -- for `:schedule-installation-
                                     operation`, the site's own recorded
                                     `:safety-concern-unresolved?`
                                     ground-truth field must be false.
                                     Evaluated off the STORE's ground
                                     truth, never the current proposal's
                                     own confidence -- the same 'ground
                                     truth, not self-report' discipline
                                     `construction.governor/weather-
                                     still-exceeds-threshold-violations`
                                     established. Does NOT fire for
                                     `:flag-safety-concern` itself (that
                                     op ALWAYS escalates to a human
                                     regardless of its own content -- see
                                     check on `high-stakes` below and
                                     `installation.phase` ns docstring).

  The confidence/high-stakes gate is SOFT: it asks a human to look, and
  the human may approve.

    - `:flag-safety-concern` is UNCONDITIONALLY a member of `high-stakes`
      -- it ALWAYS escalates to a human, at every phase, regardless of
      confidence or governor cleanliness. Surfacing a rigging/lockout-
      tagout/energization-hazard concern is exactly the kind of judgment
      this actor must never let auto-commit.
    - `:schedule-installation-operation` is ALSO UNCONDITIONALLY a member
      of `high-stakes` -- proposing a rigging/alignment/commissioning-
      test schedule window coordinates potential heavy-lift/rigging-
      equipment dispatch and pre-energization work; it always needs a
      human's sign-off, never auto-commits at any phase (belt-and-
      suspenders with `installation.phase`, which also never puts it in
      any phase's `:auto` set).
    - `:order-supplies` escalates when its own proposed `:value :cost-
      usd` exceeds `supply-order-cost-threshold-usd`, OR when confidence
      is below `confidence-floor` -- a soft, cost-scoped gate, NOT a
      permanent per-op membership in `high-stakes`."
  (:require [installation.facts :as facts]
            [installation.store :as store]))

(def confidence-floor 0.6)

(def supply-order-cost-threshold-usd
  "Above this, a `:order-supplies` proposal always escalates to a human,
  regardless of confidence -- see ns docstring."
  5000)

(def closed-op-allowlist
  #{:log-installation-record :schedule-installation-operation :flag-safety-concern :order-supplies})

(def high-stakes
  "Ops that ALWAYS escalate to a human when the governor is otherwise
  clean, at every phase, unconditionally. `:log-installation-record` is
  deliberately NOT a member -- low-risk data logging/normalization, the
  same posture `construction.governor/high-stakes` gives `:site/intake`.
  `:order-supplies` is deliberately NOT a permanent member either -- its
  escalation is a SOFT, cost-scoped rule computed in `check` below, not a
  blanket 'always a human' rule."
  #{:flag-safety-concern :schedule-installation-operation})

;; ----------------------------- checks -----------------------------

(defn- unknown-op-violations
  "The proposal's `:op` must be a member of the CLOSED four-op allowlist.
  Permanent, structural -- see ns docstring check 1."
  [{:keys [op]}]
  (when-not (contains? closed-op-allowlist op)
    [{:rule :unknown-op
      :detail (str op " はこのアクターの許可された4オペレーション（:log-installation-record/"
                  ":schedule-installation-operation/:flag-safety-concern/:order-supplies）"
                  "のいずれにも該当しない")}]))

(defn- effect-not-propose-violations
  "Every proposal from this actor's advisor must carry `:effect
  :propose` -- and nothing else. Permanent, structural -- see ns
  docstring check 2."
  [proposal]
  (when-not (= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str "提案の:effectが:propose以外（" (pr-str (:effect proposal))
                  "）-- このアクターは提案のみで、実際の作動/確定を一切行わない")}]))

(defn- forbidden-action-class-violations
  "A proposal's `:value` must never carry a heavy-lift-equipment-control /
  direct-actuation / commissioning-energization-sign-off marker.
  Permanent, structural, un-overridable by any human approval -- see ns
  docstring check 3."
  [proposal]
  (let [v (:value proposal)]
    (when (and (map? v)
               (or (true? (:heavy-lift-equipment-control? v))
                   (true? (:direct-actuation? v))
                   (true? (:commissioning-energization-sign-off? v))))
      [{:rule :forbidden-action-class
        :detail "重機/揚重機器の直接操作コマンド、またはコミッショニング・通電確定の確定を伴う提案は恒久的に禁止（免許を持つ技術者/現場監督の専権事項）"}])))

(defn- site-not-verified-violations
  "For `:schedule-installation-operation` / `:flag-safety-concern` /
  `:order-supplies`, the site's own recorded `:site-verified?`
  ground-truth field (set only via a separately-committed `:log-
  installation-record`) must be true. See ns docstring check 4."
  [{:keys [op subject]} st]
  (when (contains? #{:schedule-installation-operation :flag-safety-concern :order-supplies} op)
    (let [a (store/site st subject)]
      (when-not (true? (:site-verified? a))
        [{:rule :site-not-verified
          :detail (str subject " は現場記録が独立して検証・登録済み（:site-verified?）でない状態での提案")}]))))

(defn- legal-basis-missing-violations
  "For `:schedule-installation-operation`, the proposal must cite an
  OFFICIAL source -- never invent a jurisdiction's lift-plan/installation
  -notification requirements. See ns docstring check 5."
  [{:keys [op]} proposal]
  (when (= op :schedule-installation-operation)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-legal-basis
          :detail "公式legal-basisの引用が無い提案は据付作業スケジュール提案として扱えない"}]))))

(defn- lift-plan-incomplete-violations
  "For `:schedule-installation-operation`, the site's own recorded
  `:lift-plan-approved?` ground-truth field must be true. See ns
  docstring check 6."
  [{:keys [op subject]} st]
  (when (= op :schedule-installation-operation)
    (let [a (store/site st subject)]
      (when-not (true? (:lift-plan-approved? a))
        [{:rule :lift-plan-incomplete
          :detail (str subject " は揚重・据付作業計画（lift plan）の承認が完了記録されていない状態での据付作業スケジュール提案")}]))))

(defn- notification-lead-time-insufficient-violations
  "For `:schedule-installation-operation`, INDEPENDENTLY recompute
  whether the site's own recorded notification lead time meets its
  jurisdiction's regulatory minimum via `installation.facts/notification-
  lead-insufficient?`. Fires ONLY when that returns `true` (a
  :quantitative jurisdiction confirmed insufficient) -- `:qualitative`/
  `nil` never trip this HARD check. See ns docstring check 7."
  [{:keys [op subject]} st]
  (when (= op :schedule-installation-operation)
    (let [a (store/site st subject)]
      (when (true? (facts/notification-lead-insufficient? (:jurisdiction a) a))
        [{:rule :notification-lead-time-insufficient
          :detail (str subject " の設置計画届出リードタイム実測値（" (:installation-notice-lead-days-actual a)
                      "日）が法定最低日数に不足")}]))))

(defn- unresolved-safety-concern-violations
  "For `:schedule-installation-operation`, the site's own recorded
  `:safety-concern-unresolved?` ground-truth field must be false. See ns
  docstring check 8."
  [{:keys [op subject]} st]
  (when (= op :schedule-installation-operation)
    (let [a (store/site st subject)]
      (when (true? (:safety-concern-unresolved? a))
        [{:rule :unresolved-safety-concern
          :detail (str subject " は未解決の安全性懸念（揚重/ロックアウトタグアウト/通電）がある状態での据付作業スケジュール提案")}]))))

(defn check
  "Censors an Installation Advisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (if true [] (into []
                   (concat (unknown-op-violations request)
                           (effect-not-propose-violations proposal)
                           (forbidden-action-class-violations proposal)
                           (site-not-verified-violations request st)
                           (legal-basis-missing-violations request proposal)
                           (lift-plan-incomplete-violations request st)
                           (notification-lead-time-insufficient-violations request st)
                           (unresolved-safety-concern-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        cost (get-in proposal [:value :cost-usd])
        cost-stakes? (and (= (:op request) :order-supplies)
                          (number? cost)
                          (> cost supply-order-cost-threshold-usd))
        stakes? (boolean (or (high-stakes (:stake proposal)) cost-stakes?))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
