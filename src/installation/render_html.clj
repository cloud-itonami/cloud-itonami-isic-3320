(ns installation.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  This repo had NO demo page and no generator at all. This namespace
  drives the REAL actor stack -- `installation.operation` (a langgraph-clj
  StateGraph) -> `installation.advisor` -> `installation.governor` ->
  `installation.phase` -> `installation.store` -- through a scenario, and
  renders the console from what that run actually produced. Every site id,
  site name, jurisdiction, record id, rule name, violation detail,
  confidence, notice document and notifier send on the page is read back
  out of the store / the run's audit channel. Nothing on the page is
  hand-typed domain content.

  The scenario is adapted from this repo's own `installation.sim`
  (`clojure -M:dev:run`, run BEFORE this file was written to confirm it
  produces a sensible ledger against the real seeded ids `site-1`..
  `site-8` -- `installation.sim` does use ids that exist in
  `installation.store/demo-data`, so it was safe to build on) and extended
  in three ways `sim` does not cover:

    1. Governor checks 2 (`:effect-not-propose`) and 3
       (`:forbidden-action-class`) are STRUCTURAL defence-in-depth rules
       against a compromised/malfunctioning advisor (see
       `installation.governor` ns docstring). `installation.sim` never
       fires them, because the honest mock advisor never produces such a
       proposal. This scenario injects a TAMPERING advisor through
       `installation.operation/build`'s own `:advisor` seam -- a real
       `installation.advisor/Advisor` implementation, censored by the real
       governor -- so all EIGHT documented HARD checks are observed firing
       rather than merely asserted in prose.
    2. A rollout-PHASE hold (phase 0, read-only) and a phase-2 approval,
       so the page can show the difference between 'the governor rejected
       this' and 'this phase is not enabled yet' -- both arrive as
       `:governor-hold` facts and are trivially conflated.
    3. A REJECTED approval, so the human-in-the-loop gate is shown
       resolving both ways.

  Approver attribution is MEASURED here, not assumed: every approval uses
  an identity string that occurs nowhere else in the seed data or the
  actor context (`op-1`), and `approver-locations` then greps the data the
  store actually persisted for it. The console reports what that
  measurement found -- so if `installation.store/commit-record!` is later
  changed to persist the approver, the page starts saying so on its own.

  Deterministic: no timestamps, no random, no wall-clock anywhere in the
  page content; two runs against the same seed are byte-identical.

  This file is deliberately `.clj`, not `.cljc`: it is a BUILD-TIME tool
  (it writes a file), not part of the portable actor surface. The actor
  namespaces under `src/installation/*.cljc` keep this repo's no-JVM-
  interop mandate (see `deps.edn`); nothing in them requires this
  namespace, and nothing here is required by them.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [jp-go-dds.skin]
            [installation.advisor :as advisor]
            [installation.facts :as facts]
            [installation.governor :as governor]
            [installation.notify :as notify]
            [installation.operation :as operation]
            [installation.phase :as phase]
            [installation.store :as store]
            [langgraph.graph :as g]))

;; ----------------------------- scenario driver -----------------------------

(defn- supervisor
  "The injected actor context. `:actor-id` is `op-1` everywhere -- kept
  deliberately distinct from every approver identity below so that
  `approver-locations`' measurement can never hit on the actor id by
  accident."
  [ph]
  {:actor-id "op-1" :actor-role :site-supervisor :phase ph})

(defn- tampering-advisor
  "A REAL `installation.advisor/Advisor`, injected through
  `installation.operation/build`'s own `:advisor` seam, that returns the
  honest mock advisor's proposal with `tamper` applied. This is the
  'compromised/malfunctioning advisor' `installation.governor` checks 2
  and 3 exist to catch -- the governor is not stubbed or bypassed, it is
  handed exactly the input it claims to defend against."
  [tamper]
  (let [honest (advisor/mock-advisor)]
    (reify advisor/Advisor
      (-advise [_ st request] (tamper (advisor/-advise honest st request))))))

(defn- step!
  "One operation = one graph run on its own thread-id."
  [runs actor tid label ph request]
  (let [r (g/run* actor {:request request :context (supervisor ph)}
                  {:thread-id tid})]
    (swap! runs conj {:thread tid :kind :run :label label :phase ph
                      :op (:op request) :subject (:subject request) :result r})
    r))

(defn- decide!
  "A human resumes the interrupt-before gate with an approval decision."
  [runs actor tid status by]
  (let [r (g/run* actor {:approval {:status status :by by}}
                  {:thread-id tid :resume? true})]
    (swap! runs conj {:thread tid :kind :decision :status status :by by :result r})
    r))

(defn run-demo!
  "Drives one fresh seeded store through the scenario. Returns
  `{:db :notifier :runs}` -- `:runs` is every `langgraph.graph/run*`
  result, in order, so the renderer can read the run's own audit channel
  (which carries facts, notably `:approval-granted`, that the store
  ledger never receives).

  site-1 (JPN, verified, lift plan approved, 35-day filing lead) walks a
  full lifecycle; site-2..site-6 each trip a different HARD governor
  rule; site-7 (USA) and site-8 (DEU) show the honestly-`:qualitative`
  jurisdictions; two tampered proposals trip the two structural checks
  that only a misbehaving advisor can reach."
  []
  (let [db       (store/seed-db)
        notifier (notify/mock-notifier)
        actor    (operation/build db {:notifier notifier})
        ;; Same store, same governor, same phase gate -- only the advisor differs.
        rogue-effect (operation/build
                      db {:notifier notifier
                          :advisor (tampering-advisor #(assoc % :effect :actuate))})
        rogue-marker (operation/build
                      db {:notifier notifier
                          :advisor (tampering-advisor
                                    #(assoc-in % [:value :heavy-lift-equipment-control?] true))})
        runs (atom [])]

    ;; --- site-1 full lifecycle ------------------------------------------
    (step! runs actor "t01" "現場記録の更新（揚重ハザード無しを記録）" 3
           {:op :log-installation-record :subject "site-1"
            :patch {:id "site-1" :rigging-hazard-detected? false}})

    (step! runs actor "t02" "据付作業スケジュール提案（コンベヤ据付・アライメント調整）" 3
           {:op :schedule-installation-operation :subject "site-1"
            :window {:proposed-start-date "2026-08-15" :proposed-end-date "2026-08-22"}
            :notes "コンベヤ据付・アライメント調整、その後充填機の据付"})
    (decide! runs actor "t02" :approved "sup-tanaka")

    (step! runs actor "t03" "安全性懸念のフラグ（玉掛け方法の不備の可能性）" 3
           {:op :flag-safety-concern :subject "site-1"
            :concern-type :rigging-hazard
            :concern-description "つり荷の玉掛け方法に不備の可能性、揚重計画の再確認が必要。"})
    (decide! runs actor "t03" :approved "eng-suzuki")

    ;; The actor's own committed flag is now the site's ground truth, and
    ;; the governor reads it back to block the next schedule proposal.
    (step! runs actor "t04" "懸念が未解決のまま再スケジュール提案（自分が立てた事実に阻まれる）" 3
           {:op :schedule-installation-operation :subject "site-1"
            :window {:proposed-start-date "2026-08-18" :proposed-end-date "2026-08-25"}})

    ;; Phase 2 = assisted-coordination: logging is enabled but nothing is
    ;; auto-eligible, so even this low-risk op needs a human.
    (step! runs actor "t05" "点検後、懸念解消を記録（phase 2 なので自動確定されない）" 2
           {:op :log-installation-record :subject "site-1"
            :patch {:id "site-1" :safety-concern-unresolved? false}})
    (decide! runs actor "t05" :approved "sup-ito")

    (step! runs actor "t06" "懸念解消後の据付作業スケジュール再提案" 3
           {:op :schedule-installation-operation :subject "site-1"
            :window {:proposed-start-date "2026-08-22" :proposed-end-date "2026-08-29"}
            :notes "改訂された玉掛け方法を反映した据付スケジュール"})
    (decide! runs actor "t06" :approved "sup-tanaka")

    (step! runs actor "t07" "揚重資材の発注提案（コスト閾値未満）" 3
           {:op :order-supplies :subject "site-1"
            :items ["rigging-sling-set" "alignment-shim-kit"]
            :cost-usd 1400 :vendor "Local Rigging Supply Co."})

    (step! runs actor "t08" "門型クレーン賃借の発注提案（コスト閾値超過）" 3
           {:op :order-supplies :subject "site-1"
            :items ["mobile-gantry-crane-rental"]
            :cost-usd 22000 :vendor "Heavy Rigging Rentals"})
    (decide! runs actor "t08" :approved "eng-suzuki")

    ;; --- HARD holds: never reach a human --------------------------------
    (step! runs actor "t09" "未登録法域へのスケジュール提案" 3
           {:op :schedule-installation-operation :subject "site-2" :window {}})

    (step! runs actor "t10" "未検証現場へのスケジュール提案" 3
           {:op :schedule-installation-operation :subject "site-3" :window {}})

    (step! runs actor "t11" "揚重計画が未承認の現場へのスケジュール提案" 3
           {:op :schedule-installation-operation :subject "site-4" :window {}})

    (step! runs actor "t12" "届出リードタイム不足の現場へのスケジュール提案" 3
           {:op :schedule-installation-operation :subject "site-5" :window {}})

    (step! runs actor "t13" "未解決の安全性懸念がある現場へのスケジュール提案" 3
           {:op :schedule-installation-operation :subject "site-6" :window {}})

    (step! runs actor "t14" "未検証現場への資材発注提案（check 4 はスケジュール専用ではない）" 3
           {:op :order-supplies :subject "site-3"
            :items ["anchor-bolt-set"] :cost-usd 300 :vendor "Local Rigging Supply Co."})

    (step! runs actor "t15" "許可4オペレーション外の操作要求" 3
           {:op :direct-equipment-command :subject "site-1"})

    (step! runs rogue-effect "t16" "改竄されたアドバイザー: :effect が :propose ではない提案" 3
           {:op :log-installation-record :subject "site-1"
            :patch {:id "site-1" :rigging-hazard-detected? false}})

    (step! runs rogue-marker "t17" "改竄されたアドバイザー: 重機直接操作マーカー付きの提案" 3
           {:op :order-supplies :subject "site-1"
            :items ["crane-boom-extension"] :cost-usd 900 :vendor "Heavy Rigging Rentals"})

    ;; --- the human gate resolving the other way -------------------------
    (step! runs actor "t18" "安全性懸念のフラグ（承認者が却下する）" 3
           {:op :flag-safety-concern :subject "site-7"
            :concern-type :energization-hazard
            :concern-description "Reported control-panel energization concern could not be reproduced on site."})
    (decide! runs actor "t18" :rejected "sup-casey")

    ;; --- honestly qualitative jurisdictions -----------------------------
    (step! runs actor "t19" "USA（数値リードタイム法定なし）へのスケジュール提案" 3
           {:op :schedule-installation-operation :subject "site-7"
            :window {:proposed-start-date "2026-09-01" :proposed-end-date "2026-09-10"}})
    (decide! runs actor "t19" :approved "sup-casey")

    (step! runs actor "t20" "DEU/EU（数値リードタイム法定なし）へのスケジュール提案" 3
           {:op :schedule-installation-operation :subject "site-8"
            :window {:proposed-start-date "2026-09-10" :proposed-end-date "2026-09-20"}})
    (decide! runs actor "t20" :approved "eng-mueller")

    ;; --- rollout phase 0 = read-only ------------------------------------
    (step! runs actor "t21" "phase 0（read-only）での記録更新要求" 0
           {:op :log-installation-record :subject "site-1"
            :patch {:id "site-1" :rigging-hazard-detected? false}})

    {:db db :notifier notifier :runs @runs}))

;; ----------------------------- run-derived views -----------------------------

(defn- thread-order [runs] (distinct (map :thread runs)))

(defn- thread-view
  "Per-thread rollup. The `:audit` channel accumulates across a thread's
  supersteps, so the LAST run entry for a thread carries every fact that
  thread produced -- including `:approval-granted`, which the `:commit`
  node never writes to the store ledger."
  [runs]
  (for [tid (thread-order runs)
        :let [entries  (filter #(= tid (:thread %)) runs)
              opening  (first entries)
              decision (first (filter #(= :decision (:kind %)) entries))
              final    (last entries)]]
    {:thread   tid
     :label    (:label opening)
     :phase    (:phase opening)
     :op       (:op opening)
     :subject  (:subject opening)
     :first    (get-in opening [:result :state :disposition])
     :status   (get-in opening [:result :status])
     :decision decision
     :final    (get-in final [:result :state :disposition])
     :audit    (get-in final [:result :state :audit])}))

(defn- approval-events
  "Every human decision fact the run produced, tagged with its thread."
  [threads]
  (for [{:keys [thread op subject audit]} threads
        f audit
        :when (#{:approval-granted :approval-rejected} (:t f))]
    (assoc f :thread thread :op (or (:op f) op) :subject (or (:subject f) subject))))

(defn- persisted
  "Everything the store actually kept, as data."
  [db]
  [[:installation-record-log (store/installation-record-log-history db)]
   [:schedule-proposal       (store/schedule-proposal-history db)]
   [:safety-concern-flag     (store/safety-concern-flag-history db)]
   [:supply-order-proposal   (store/supply-order-proposal-history db)]
   [:site-directory          (store/all-sites db)]
   [:audit-ledger            (store/ledger db)]])

(defn- approver-locations
  "MEASURED approver retention: which of the store's persisted
  collections actually contain `approver`. Every approver identity used
  in `run-demo!` is chosen so it appears nowhere in the seed data and is
  not the actor id, so a hit is unambiguous. Returns a (possibly empty)
  sorted vector of collection keys -- an empty vector means the store
  dropped the approver on that path, and the console says so explicitly
  rather than silently omitting the column."
  [db approver]
  (vec (sort (for [[k coll] (persisted db)
                   :when (some #(str/includes? (pr-str %) approver) coll)]
               k))))

(defn- hard-hold-facts [ledger]
  (filter #(and (= :governor-hold (:t %)) (seq (:basis %))) ledger))

(defn- phase-hold-facts [ledger]
  (filter #(and (= :governor-hold (:t %)) (empty? (:basis %))) ledger))

(defn- hard-rules [ledger]
  (vec (sort (distinct (mapcat :basis (hard-hold-facts ledger))))))

;; ----------------------------- html helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- nm [v] (if (keyword? v) (name v) (str v)))
(defn- kode [v] (str "<code>" (esc v) "</code>"))
(defn- span [cls v] (str "<span class=\"" cls "\">" (esc v) "</span>"))
(defn- dash [] "<span class=\"muted\">—</span>")
(defn- cells [& xs] (apply str (map #(str "<td>" % "</td>") xs)))
(defn- tr [& xs] (str "        <tr>" (apply str xs) "</tr>"))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>"
       (apply str (map #(str "<th>" (esc %) "</th>") headers))
       "</tr></thead>\n"
       "      <tbody>\n"
       (if (seq rows) (str (str/join "\n" rows) "\n") "")
       "      </tbody>\n"
       "    </table>\n"))

(defn- section [title lede body]
  (str "  <section class=\"card\">\n"
       "    <h2>" (esc title) "</h2>\n"
       (if lede (str "    <p class=\"muted\">" lede "</p>\n") "")
       body
       "  </section>\n"))

(defn- yes-no
  "Renders a recorded ground-truth boolean. `good` says which value is
  the safe one, so `:safety-concern-unresolved?` colours the opposite way
  from `:site-verified?` without inventing a third vocabulary."
  [v good]
  (cond
    (nil? v)     (span "muted" "未記録")
    (= v good)   (span "ok" (str v))
    :else        (span "critical" (str v))))

;; ----------------------------- sections -----------------------------

(defn- last-fact-for [ledger site-id]
  (last (filter #(= site-id (:subject %)) ledger)))

(defn- outcome-cell [f]
  (cond
    (nil? f) (span "muted" "no activity")
    (= :committed (:t f)) (span "ok" "committed")
    (= :approval-rejected (:t f)) (span "warn" "held · approver rejected")
    (and (= :governor-hold (:t f)) (seq (:basis f)))
    (str (span "critical" "HARD hold") " · "
         (kode (str/join ", " (map nm (:basis f)))))
    (= :governor-hold (:t f)) (span "warn" (str "phase hold · " (nm (:phase-reason f))))
    :else (span "muted" (nm (:t f)))))

(defn- lead-time-cell
  "The same independent recompute the governor's check 7 performs --
  `facts/notification-lead-insufficient?` is CALLED here, not copied.

  Note the three-valued function returns plain `false` in two very
  different situations: 'recorded, and confirmed to meet the statutory
  minimum', and 'nothing was recorded, so no arithmetic was possible'.
  Both are correct answers for the GOVERNOR (neither is a bright-line
  violation, so neither is a HARD hold) but they are not the same claim
  to put on a page. Rendering the second one as 'meets the minimum'
  would turn an unmeasured field into a compliance statement, so the
  unrecorded case is reported as unrecomputable instead."
  [{:keys [jurisdiction installation-notice-lead-days-actual] :as site}]
  (let [verdict   (facts/notification-lead-insufficient? jurisdiction site)
        minimum   (:notification-lead-days (facts/spec-basis jurisdiction))
        recorded? (number? installation-notice-lead-days-actual)
        actual    (if recorded?
                    (str "<span class=\"num\">" (esc installation-notice-lead-days-actual) "</span> 日")
                    (span "muted" "未記録"))]
    (str actual
         (cond
           (true? verdict)  (str " · " (span "critical" (str "法定最低 " minimum " 日に不足")))
           (and (false? verdict) recorded?)
           (str " · " (span "ok" (str "法定最低 " minimum " 日を満たす")))
           (false? verdict)
           (str " · " (span "warn" (str "実測値が無く独立再計算できない（法定最低 " minimum
                                        " 日 / ガバナーは値が無いことを違反として扱わない）")))
           (= :qualitative verdict) (str " · " (span "muted" "数値基準なし（qualitative）"))
           :else (str " · " (span "critical" "spec-basis 無し"))))))

(defn- sites-section [db ledger]
  (let [sites (store/all-sites db)]
    (section
     "設置現場ディレクトリ（installation.store/seed-db）"
     (str "8 現場すべて。真偽値は現場自身の記録（ground truth）で、"
          "提案側の自己申告ではない。<code>approved-by</code> 列は下の"
          "「承認の帰属」節で実測した保持結果をそのまま表示している。")
     (table ["Site" "名称" "法域" "site-verified?" "lift-plan-approved?"
             "届出リードタイム（独立再計算）" "safety-concern-unresolved?"
             "status" "approved-by" "この run の最終事実"]
            (for [{:keys [id name jurisdiction site-verified? lift-plan-approved?
                          safety-concern-unresolved? status approved-by] :as s} sites]
              (tr (cells (kode id)
                         (esc name)
                         (kode jurisdiction)
                         (yes-no site-verified? true)
                         (yes-no lift-plan-approved? true)
                         (lead-time-cell s)
                         (yes-no safety-concern-unresolved? false)
                         (kode (nm status))
                         (if approved-by (span "ok" approved-by) (dash))
                         (outcome-cell (last-fact-for ledger id)))))))))

(defn- jurisdictions-section [db]
  (let [used (sort (distinct (map :jurisdiction (store/all-sites db))))
        all  (sort (distinct (concat used (keys facts/catalog))))
        cov  (facts/coverage used)]
    (section
     "法域別 legal-basis カタログ（installation.facts）"
     (str "現場が実際に使っている法域と、カタログに載っている法域の和集合。"
          "カタログに無い法域には spec-basis が無く、"
          "<code>:schedule-installation-operation</code> は HARD hold になる —— 要件を推測で作らない。<br>"
          "この run の現場が使う法域: 要求 <span class=\"num\">" (:requested cov)
          "</span> / 収載 <span class=\"num\">" (:covered cov) "</span>"
          (when (seq (:missing-jurisdictions cov))
            (str " / 未収載 " (kode (str/join ", " (:missing-jurisdictions cov))))))
     (table ["法域" "現場あり" "所管当局" "threshold-model" "法定リードタイム"
             "lift-plan basis" "installation-notification basis"]
            (for [iso3 all
                  :let [sb (facts/spec-basis iso3)]]
              (tr (cells (kode iso3)
                         (if (some #{iso3} used) (span "ok" "あり") (span "muted" "なし"))
                         (if sb (esc (:owner-authority sb)) (span "critical" "spec-basis 無し"))
                         (if sb (kode (nm (:threshold-model sb))) (dash))
                         (if (:notification-lead-days sb)
                           (str "<span class=\"num\">" (esc (:notification-lead-days sb)) "</span> 日")
                           (if sb (span "muted" "法定数値なし") (dash)))
                         (if sb
                           (str (esc (:lift-plan-basis sb)) "<br><a href=\""
                                (esc (:lift-plan-provenance sb)) "\">"
                                (esc (:lift-plan-provenance sb)) "</a>")
                           (dash))
                         (if sb
                           (str (esc (:installation-notification-basis sb)) "<br><a href=\""
                                (esc (:installation-notification-provenance sb)) "\">"
                                (esc (:installation-notification-provenance sb)) "</a>")
                           (dash)))))))))

(defn- gate-label [{:keys [disposition reason]}]
  (case disposition
    :commit   (span "ok" "自動確定")
    :escalate (str (span "warn" "人間の承認") (when reason (str " · " (kode (nm reason)))))
    :hold     (str (span "critical" "不可") (when reason (str " · " (kode (nm reason)))))
    (span "muted" (nm disposition))))

(defn- action-gate-section []
  (let [ops    (sort (map nm governor/closed-op-allowlist))
        phases (sort (keys phase/phases))
        ;; Derived, not asserted: does a governor HARD hold survive every
        ;; (op, phase) pair the phase gate can be asked about?
        hold-wins? (every? (fn [[ph o]]
                             (= :hold (:disposition (phase/gate ph {:op (keyword o)} :hold))))
                           (for [ph phases o ops] [ph o]))]
    (section
     "アクション・ゲート（installation.phase/gate を実際に呼んで生成）"
     (str "各セルは <code>(phase/gate &lt;phase&gt; {:op &lt;op&gt;} :commit)</code> "
          "の実際の戻り値 —— つまり「ガバナーが clean だったとき、その phase で何が起きるか」。"
          "この表に手書きの主張は無いので、phase の定義を変えれば表も変わる。<br>"
          "ガバナーが HARD hold を出した場合に全 (op, phase) が hold になるか（実際に呼んで確認）: "
          (if hold-wins? (span "ok" "true") (span "critical" "false")))
     (table (concat ["Op" "常時 human（governor/high-stakes）"]
                    (for [ph phases] (str "phase " ph " — " (:label (get phase/phases ph)))))
            (for [o ops]
              (tr (cells (kode (str ":" o))
                         (if (contains? governor/high-stakes (keyword o))
                           (span "warn" "はい（どの phase でも自動確定しない）")
                           (span "muted" "いいえ"))
                         (str/join "</td><td>"
                                   (for [ph phases]
                                     (gate-label (phase/gate ph {:op (keyword o)} :commit)))))))))))

(defn- thresholds-section []
  (section
   "ガバナーのソフト閾値（installation.governor の実際の値）"
   "HARD ルールと違い、この3つは人間が見て承認できる。値はコードから読んでいる。"
   (table ["設定" "値" "意味"]
          [(tr (cells (kode "governor/confidence-floor")
                      (str "<span class=\"num\">" (esc governor/confidence-floor) "</span>")
                      "これを下回る confidence の提案は人間へエスカレーション"))
           (tr (cells (kode "governor/supply-order-cost-threshold-usd")
                      (str "<span class=\"num\">" (esc governor/supply-order-cost-threshold-usd) "</span> USD")
                      "これを超える :order-supplies は confidence によらず人間へ"))
           (tr (cells (kode "governor/high-stakes")
                      (kode (str/join ", " (sort (map #(str ":" (nm %)) governor/high-stakes))))
                      "どの phase でも自動確定しない op"))
           (tr (cells (kode "governor/closed-op-allowlist")
                      (kode (str/join ", " (sort (map #(str ":" (nm %)) governor/closed-op-allowlist))))
                      "これ以外の op は :unknown-op で HARD hold"))])))

(defn- scenario-section [threads]
  (section
   "この run が実行したシナリオ"
   (str "21 スレッド。各行の「初回」「最終」は <code>langgraph.graph/run*</code> が返した"
        " <code>:disposition</code> をそのまま読んだもの。"
        "<code>interrupted</code> は <code>interrupt-before #{:request-approval}</code> "
        "が人間を待って実際に停止したことを意味する。")
   (table ["Thread" "内容" "Op" "現場" "phase" "初回 disposition" "人間の判断" "最終 disposition"]
          (for [{:keys [thread label phase op subject first status decision final]} threads]
            (tr (cells (kode thread)
                       (esc label)
                       (kode (str ":" (nm op)))
                       (kode subject)
                       (str "<span class=\"num\">" (esc phase) "</span>")
                       (str (case first
                              :commit   (span "ok" "commit")
                              :escalate (span "warn" "escalate")
                              :hold     (span "critical" "hold")
                              (span "muted" (nm first)))
                            (when (= :interrupted status)
                              (str " · " (span "muted" "interrupted"))))
                       (if decision
                         (str (if (= :approved (:status decision))
                                (span "ok" "approved")
                                (span "critical" "rejected"))
                              " · " (kode (:by decision)))
                         (dash))
                       (case final
                         :commit (span "ok" "commit")
                         :hold   (span "critical" "hold")
                         (span "muted" (nm final)))))))))

(defn- holds-section [ledger]
  (let [hard  (hard-hold-facts ledger)
        phasy (phase-hold-facts ledger)
        rejected (filter #(= :approval-rejected (:t %)) ledger)
        rules (hard-rules ledger)]
    (section
     "この run で実際に発火した HOLD"
     (str "HARD ルール <span class=\"num\">" (count rules) "</span> 種 / "
          "HARD hold <span class=\"num\">" (count hard) "</span> 件 · "
          "phase による hold <span class=\"num\">" (count phasy) "</span> 件 · "
          "承認却下 <span class=\"num\">" (count rejected) "</span> 件。"
          "発火したルール名: " (kode (str/join ", " (map #(str ":" (nm %)) rules))) "。<br>"
          "この一覧は監査台帳から導出している —— ルールを消せばここから消えるので、"
          "「このルールは発火しえない」といった手書きの主張は置いていない。"
          "HARD hold は人間の承認で覆せない（<code>:request-approval</code> ノードに到達しない）。")
     (table ["種別" "Op" "現場" "ルール" "confidence" "ガバナーが返した理由"]
            (for [f ledger
                  :when (#{:governor-hold :approval-rejected} (:t f))]
              (tr (cells (cond
                           (= :approval-rejected (:t f)) (span "warn" "承認却下")
                           (seq (:basis f))              (span "critical" "HARD（覆せない）")
                           :else                          (span "warn" "phase 未開放"))
                         (kode (str ":" (nm (:op f))))
                         (kode (:subject f))
                         (if (seq (:basis f))
                           (kode (str/join ", " (map #(str ":" (nm %)) (:basis f))))
                           (if (:phase-reason f)
                             (kode (str ":" (nm (:phase-reason f))))
                             (dash)))
                         (if (some? (:confidence f))
                           (str "<span class=\"num\">" (esc (:confidence f)) "</span>")
                           (dash))
                         (if (seq (:violations f))
                           (str/join "<br>" (map #(esc (:detail %)) (:violations f)))
                           (if (:phase-reason f)
                             (esc (str "phase " (:phase f) " ("
                                       (:label (get phase/phases (:phase f))) ") "
                                       "ではこの op は書き込みできない"))
                             (span "muted" "—"))))))))))

(defn- approvals-section [db threads]
  (let [events (approval-events threads)
        granted (filter #(= :approval-granted (:t %)) events)
        rejected (filter #(= :approval-rejected (:t %)) events)
        rejector-known? (boolean (some :by rejected))
        retained (into {} (for [e granted] [(:by e) (approver-locations db (:by e))]))
        any-retained (filter (comp seq val) retained)]
    (section
     "承認の帰属（実測 —— 仮定していない）"
     (str "承認は <code>installation.operation</code> の <code>:request-approval</code> ノードで "
          "<code>{:approval {:status :approved :by &lt;id&gt;}}</code> として入る。"
          "この節は「その識別子が、ストアが実際に永続化したデータのどこかに残っているか」を"
          "<em>探索して</em>報告する。承認者 ID は seed データにもアクター ID (<code>op-1</code>) にも"
          "現れない文字列を使っているので、ヒットは曖昧にならない。<br>"
          "結果: 承認 <span class=\"num\">" (count granted) "</span> 件のうち、"
          "永続化データに識別子が残っていたのは <span class=\"num\">" (count any-retained)
          "</span> 承認者分。"
          "却下イベントに承認者 ID が含まれるか: "
          (if rejector-known? (span "ok" "含まれる") (span "critical" "含まれない"))
          " —— 却下者の身元は <code>:approval-rejected</code> 事実にも台帳にも残らない。")
     (table ["Thread" "Op" "現場" "決定" "承認者 ID" "ストアが保持した場所（実測）"]
            (for [{:keys [thread op subject t by]} events]
              (tr (cells (kode thread)
                         (kode (str ":" (nm op)))
                         (kode subject)
                         (if (= :approval-granted t) (span "ok" "approved") (span "critical" "rejected"))
                         (if by (kode by) (span "critical" "記録されていない"))
                         (let [locs (when by (approver-locations db by))]
                           (cond
                             (nil? by) (span "critical" "却下者の身元はどこにも残らない")
                             (seq locs) (str (span "ok" "保持されている") " · "
                                             (kode (str/join ", " (map #(str ":" (nm %)) locs))))
                             :else (str (span "warn" "監査事実のみ") " · "
                                        "<span class=\"muted\">"
                                        "この run の <code>:approval-granted</code> にはあるが、"
                                        "<code>store/commit-record!</code> が永続化したレコードにも監査台帳にも無い"
                                        "</span>")))))))) ))

(defn- ledger-section [ledger]
  (section
   "監査台帳（この run）"
   (str "<code>installation.store/ledger</code> の append-only 決定事実列。"
        "<span class=\"num\">" (count ledger) "</span> 件。"
        "<code>basis</code> は commit の場合は提案が引用した法令原典そのもの、"
        "hold の場合は発火した HARD ルール名。")
   (table ["#" "事実" "Op" "現場" "actor" "disposition" "basis"]
          (map-indexed
           (fn [i {:keys [t op subject actor disposition basis]}]
             (tr (cells (str "<span class=\"num\">" (inc i) "</span>")
                        (case t
                          :committed (span "ok" "committed")
                          :governor-hold (span "critical" "governor-hold")
                          :approval-rejected (span "warn" "approval-rejected")
                          (span "muted" (nm t)))
                        (kode (str ":" (nm op)))
                        (kode subject)
                        (if actor (kode actor) (dash))
                        (kode (nm disposition))
                        (if (seq basis)
                          (str/join "<br>" (map #(esc (if (keyword? %) (str ":" (name %)) %)) basis))
                          (dash)))))
           ledger))))

(defn- artifacts-section [db]
  (let [rows (for [[k coll] (take 4 (persisted db))
                   r coll]
               (tr (cells (kode (str ":" (nm k)))
                          (kode (get r "record_id"))
                          (kode (get r "kind"))
                          (kode (get r "site_id"))
                          (kode (get r "jurisdiction"))
                          (if (get r "immutable") (span "ok" "true") (span "critical" "false")))))]
    (section
     "確定した調整アーティファクト（4本の append-only 履歴）"
     (str "record_id は <code>installation.registry</code> が法域スコープの連番から組み立てた実物。"
          "このアクターは <code>:effect :propose</code> しか出さないので、"
          "「確定した」とは「調整アーティファクトが台帳に載った」という意味であり、"
          "クレーンが動いたことでも通電が承認されたことでも無い。")
     (table ["履歴" "record_id" "kind" "現場" "法域" "immutable"] rows))))

(defn- notice-section [db notifier]
  (let [docs (keep #(get % "document") (store/safety-concern-flag-history db))
        sent (notify/sent-log notifier)]
    (section
     "実際に送信された安全性懸念通知"
     (str "<code>:flag-safety-concern</code> が人間の承認を得て確定したときにだけ、"
          "<code>installation.operation</code> の <code>:commit</code> ノードが"
          "現場の <code>:safety-contacts</code> 名簿へメールと電話の両方を送る。"
          "以下は <code>installation.notify/mock-notifier</code> が実際に記録した送信ログ"
          "（<span class=\"num\">" (count sent) "</span> 件）と、"
          "<code>installation.registry/render-safety-concern-notice</code> が生成した文書本文。")
     (str (table ["#" "宛先" "チャネル" "status" "件名 / メッセージ"]
                 (map-indexed
                  (fn [i {:keys [to channel status subject message]}]
                    (tr (cells (str "<span class=\"num\">" (inc i) "</span>")
                               (kode to)
                               (kode (nm channel))
                               (span "ok" (nm status))
                               (esc (or subject message)))))
                  sent))
          (str/join "" (for [d docs]
                         (str "    <pre>" (esc d) "</pre>\n")))))))

;; ----------------------------- document -----------------------------

(defn render
  "Renders the full console from a completed `run-demo!` result."
  [{:keys [db notifier runs]}]
  (let [ledger  (vec (store/ledger db))
        threads (vec (thread-view runs))
        rules   (hard-rules ledger)]
    (str
     "<!doctype html>\n"
     "<html lang=\"ja\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-3320 · Installation of Industrial Machinery and Equipment — Operator Console</title>"
     "<style>\n" (jp-go-dds.skin/dds+skin) "\n</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>産業用機械設置（ISIC 3320）— Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample</span>\n"
     "  <span class=\"badge\">governor-gated</span>\n"
     "  <span class=\"badge\">:effect :propose only</span>\n"
     "</header>\n"
     "<main>\n"
     "  <p class=\"subtitle\">このページは "
     (kode "clojure -M:dev:render-html")
     " が本物のアクター（<code>installation.operation</code> の langgraph StateGraph → "
     "<code>installation.advisor</code> → <code>installation.governor</code> → "
     "<code>installation.phase</code> → <code>installation.store</code>）を"
     "実際に走らせて生成している。現場 ID・名称・record_id・ルール名・違反本文・confidence・"
     "通知文書は、すべてその run が返した値をそのまま読み出したもの。手書きの行は 1 つも無い。</p>\n"
     "  <p class=\"banner\">このアクターは<strong>調整（coordination）専用</strong>で、"
     "重機・揚重機器を操作せず、コミッショニング／通電のサインオフも行わない。"
     "その権限は免許を持つ技術者・現場監督の専権事項であり、"
     "ガバナーの check 1–4 がそれを構造的な HARD ルールとして固定している"
     "（<span class=\"num\">" (count rules) "</span> 種の HARD ルールがこの run で実際に発火した）。</p>\n"
     (sites-section db ledger)
     (jurisdictions-section db)
     (action-gate-section)
     (thresholds-section)
     (scenario-section threads)
     (holds-section ledger)
     (approvals-section db threads)
     (ledger-section ledger)
     (artifacts-section db)
     (notice-section db notifier)
     "</main>\n"
     "<footer>\n"
     "  <p>cloud-itonami-isic-3320 · Installation of Industrial Machinery and Equipment · AGPL-3.0-or-later<br>\n"
     "  生成: <code>installation.render-html</code>（決定論的 —— 同じ seed に対して 2 回走らせるとバイト単位で一致する）。\n"
     "  再生成: <code>clojure -M:dev:render-html</code></p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db] :as result} (run-demo!)
        ledger (vec (store/ledger db))
        holds  (filter #(= :governor-hold (:t %)) ledger)
        hard   (hard-hold-facts ledger)
        rules  (hard-rules ledger)]
    ;; Build-time invariants. A console that shows no hold would
    ;; misrepresent the governor as a rubber stamp, and one that never
    ;; touched an op would misrepresent the closed allowlist.
    (when (zero? (count holds))
      (throw (ex-info "no :governor-hold fact in this run -- the console would misrepresent the governor"
                      {:ledger-facts (count ledger)})))
    (when (zero? (count hard))
      (throw (ex-info "no HARD governor hold in this run -- every hold was a phase gate, so the console would misrepresent the governor's own rules"
                      {:holds (count holds)})))
    (when-not (some #(= :committed (:t %)) ledger)
      (throw (ex-info "no commit in this run -- the console would misrepresent the actor as unable to act" {})))
    (let [seen (set (keep :op ledger))
          missing (remove seen governor/closed-op-allowlist)]
      (when (seq missing)
        (throw (ex-info "scenario never exercised every op in governor/closed-op-allowlist"
                        {:missing (vec (sort missing))}))))
    (io/make-parents out)
    (spit out (render result))
    (println "wrote" out
             (str "(" (count ledger) " ledger facts, "
                  (count hard) " HARD holds over " (count rules) " distinct rules "
                  (pr-str rules) ", "
                  (count (phase-hold-facts ledger)) " phase holds, "
                  (count (store/installation-record-log-history db)) " installation records, "
                  (count (store/schedule-proposal-history db)) " schedule proposals, "
                  (count (store/safety-concern-flag-history db)) " safety-concern flags, "
                  (count (store/supply-order-proposal-history db)) " supply orders)"))))
