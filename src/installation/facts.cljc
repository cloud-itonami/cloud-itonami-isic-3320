(ns installation.facts
  "Per-jurisdiction industrial-machinery-installation regulatory catalog --
  the spec-basis table the Installation Governor checks every `:schedule-
  installation-operation` proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's rigging/lift-plan and
  installation-notification requirements, or did it invent one?'). Same
  honest-coverage discipline `demolition.facts`/`construction.facts`
  established for this fleet: a jurisdiction not in this table has NO
  spec-basis, full stop -- the advisor must not fabricate one, and the
  governor holds if it tries.

  Coverage is reported HONESTLY (see `coverage`); this is a STARTING
  catalog (JPN/USA/DEU/GBR), not a from-scratch survey of all ~194
  jurisdictions. Extending coverage is additive: add one map to `catalog`,
  cite a real source, done -- never invent a jurisdiction's requirements
  to make coverage look bigger.

  `:threshold-model` mirrors the SAME honest quantitative/qualitative
  split `demolition.facts`/`construction.facts` established, applied here
  to a DIFFERENT real-world numeric trigger -- the minimum number of days
  an installation/relocation PLAN filing must precede the planned start of
  work on specified hazardous machinery:
    :quantitative -- the law itself states a fixed lead-time. Japan's
                     Industrial Safety and Health Act (労働安全衛生法)
                     Article 88 paragraph 1 requires the plan for
                     installing/relocating/materially altering the main
                     structural parts of specified machinery to be filed
                     with the Chief of the Labour Standards Inspection
                     Office no later than 30 CALENDAR days before the date
                     work begins -- a real, independently-verified,
                     numeric bright line (confirmed via jaish.gr.jp /
                     Wikibooks quoting the statute text; see `catalog`
                     `\"JPN\"` `:installation-notification-provenance`).
                     `notification-lead-insufficient?` can independently
                     recompute a HARD hold from this.
    :qualitative  -- the law imposes a documented pre-commissioning
                     inspection/hazard-assessment/rigging-plan duty before
                     installation or commissioning work starts, with NO
                     fixed jurisdiction-wide numeric lead-time this actor
                     could independently verify at the time this catalog
                     was built (USA/DEU -- see notes on each entry below).
                     This actor does NOT invent a day-count to make these
                     jurisdictions look automatable --
                     `notification-lead-insufficient?` returns
                     `:qualitative` and the Installation Governor's
                     permanent high-stakes gate on `:schedule-
                     installation-operation` (see `installation.governor`
                     ns docstring) routes the decision to a human every
                     time regardless.

  UNLIKE `demolition.facts` (which found a real numeric lead-time for both
  JPN and USA), this catalog's research found only ONE jurisdiction (JPN)
  with a confirmed fixed statutory lead-time for THIS specific proposal
  type (an installation/relocation PLAN filing). OSHA's lockout/tagout
  standard (29 CFR 1910.147, cited below as USA's `:lift-plan-basis`) is a
  real, load-bearing regulation for this domain, but it is a procedural
  completion requirement (energy-control program in place before
  servicing/commissioning), not a numeric advance-notice-days rule -- so
  USA is honestly `:qualitative` here rather than reusing demolition's
  numeric convention without a matching citation. Extending USA/DEU/GBR to
  `:quantitative` later requires a real citation for THIS proposal type,
  not a citation for a different (however real) requirement.

  DEU is used as the EU-jurisdiction proxy, the SAME convention
  `demolition.facts`/`construction.facts`/`aerospace.facts` established --
  there is no ISO-3166 alpha-3 code for the EU itself, and the EU
  Machinery Directive 2006/42/EC's installation/putting-into-service
  duties are given operational teeth in Germany via the
  Betriebssicherheitsverordnung (BetrSichV, Ordinance on Industrial
  Safety and Health), so the citation lists BOTH the EU directive and its
  German operational instrument rather than inventing an EU country
  code.

  GBR's owner authority is the Health and Safety Executive (HSE), which
  enforces the Lifting Operations and Lifting Equipment Regulations 1998
  (LOLER, SI 1998/2307, made under the Health and Safety at Work etc. Act
  1974). LOLER regulation 8 (`:lift-plan-basis`) requires every lifting
  operation involving lifting equipment -- the rigging/hoisting work
  central to installing industrial machinery -- to be properly planned by
  a competent person, appropriately supervised, and carried out in a safe
  manner. LOLER regulation 9(2) (`:installation-notification-basis`)
  requires a thorough examination by a competent person after installation
  and before the equipment is first put into service, specifically where
  the equipment's safety depends on its installation conditions -- the
  same 'verify before commissioning' duty shape as DEU's BetrSichV §15,
  confirmed directly against the current (revised) text on
  legislation.gov.uk. Neither provision states a fixed number of advance-
  notice days for an installation PLAN filing -- this catalog does not
  invent one, so GBR is honestly `:qualitative` here, the same as USA/DEU.")

(def catalog
  "iso3 -> requirement map. `:lift-plan-basis` / `:installation-
  notification-basis` / their `-provenance` pairs, plus `:owner-
  authority`, are the G2-style citation the governor requires before a
  `:schedule-installation-operation` proposal can ever commit."
  {"JPN" {:name "Japan"
          :owner-authority "厚生労働省（労働基準監督署長）"
          :lift-plan-basis "クレーン等安全規則（昭和47年労働省令第34号）第74条の2（クレーンを用いて行う作業の作業計画の作成義務 -- つり上げ荷重3トン以上のクレーン等を用いる作業について、作業の方法・使用するクレーン等の種類・能力等を定めた作業計画の作成が必要）"
          :lift-plan-provenance "https://laws.e-gov.go.jp/law/347M50002000034"
          :installation-notification-basis "労働安全衛生法（昭和47年法律第57号）第88条第1項（別表第七に掲げる特定機械等を設置し、若しくは移転し、又はこれらの主要構造部分を変更しようとするとき、その計画を当該工事の開始の日の30日前までに労働基準監督署長に届け出る義務）"
          :installation-notification-provenance "https://laws.e-gov.go.jp/law/347AC0000000057"
          :threshold-model :quantitative
          :notification-lead-days 30
          :threshold-note "工事開始の30暦日前までの計画届出義務（労働安全衛生法第88条第1項）。別表第七に掲げる特定機械等（クレーン、エレベーター、ボイラー等）の設置・移転・主要構造部分の変更が対象。"}
   "USA" {:name "United States"
          :owner-authority "Occupational Safety and Health Administration (OSHA), U.S. Department of Labor"
          :lift-plan-basis "29 CFR Part 1926 Subpart CC (Cranes and Derricks in Construction) -- rigging is performed by a qualified rigger, and a qualified person must evaluate/design the rigging configuration for complex/critical lifts before hoisting begins"
          :lift-plan-provenance "https://www.osha.gov/laws-regs/regulations/standardnumber/1926/1926SubpartCC"
          :installation-notification-basis "OSHA 29 CFR 1910.147 (Control of Hazardous Energy -- Lockout/Tagout): before an employee performs servicing, maintenance or COMMISSIONING work on installed machinery where unexpected energization/start-up/release of stored energy could cause injury, the machine must be isolated from its energy source and rendered safe under a documented energy-control program -- a real, load-bearing completion requirement, but NOT a numeric advance-notice-days rule (see ns docstring -- honestly `:qualitative` here, not reused from a different requirement)"
          :installation-notification-provenance "https://www.osha.gov/laws-regs/regulations/standardnumber/1910/1910.147"
          :threshold-model :qualitative
          :notification-lead-days nil
          :threshold-note "OSHA's lockout/tagout standard requires a documented energy-control program be in place and verified BEFORE commissioning/energization work begins, but sets no fixed federal advance-notice-days count for an installation PLAN filing -- this actor does not invent one. This actor's `:schedule-installation-operation` always routes to a human regardless (see `installation.governor` ns docstring `high-stakes`)."}
   "DEU" {:name "Germany (EU jurisdiction proxy, see ns docstring)"
          :owner-authority "Bundesministerium für Arbeit und Soziales (BMAS) / Deutsche Gesetzliche Unfallversicherung (DGUV); EU level: European Commission (Machinery Directive)"
          :lift-plan-basis "Verordnung über Sicherheit und Gesundheitsschutz bei der Verwendung von Arbeitsmitteln (Betriebssicherheitsverordnung, BetrSichV) §15 -- Prüfung vor Inbetriebnahme und vor Wiederinbetriebnahme nach prüfpflichtigen Änderungen (inspection required before initial commissioning and before recommissioning after inspection-triggering changes -- e.g. after installation/assembly of the machine); grounded in Directive 2006/42/EC (Machinery Directive) Annex VI/VII assembly-instructions and technical-documentation duties before putting into service"
          :lift-plan-provenance "https://www.gesetze-im-internet.de/betrsichv_2015/__15.html"
          :installation-notification-basis "Directive 2006/42/EC (Machinery Directive) -- assembly instructions (Annex VI) and technical documentation/conformity assessment (Annex VII) must be complete before machinery is put into service; nationally operationalized in Germany via BetrSichV's pre-commissioning inspection duty (§15). No fixed EU-wide or German federal numeric advance-notice-days count for an installation PLAN filing is imposed -- this actor does not invent one (see ns docstring)."
          :installation-notification-provenance "https://eur-lex.europa.eu/eli/dir/2006/42/oj/eng"
          :threshold-model :qualitative
          :notification-lead-days nil
          :threshold-note "EU/ドイツの機械設置関連法令は据付・試運転前の技術文書/適合性確認義務（指令2006/42/EC）とコミッショニング前検査義務（BetrSichV §15）を課すのみで、日本の労働安全衛生法第88条のような固定日数の計画届出リードタイムはEU全域では法定されていない -- ここで数値を創作しない。"}
   "GBR" {:name "United Kingdom"
          :owner-authority "Health and Safety Executive (HSE) -- statutory regulator under the Health and Safety at Work etc. Act 1974"
          :lift-plan-basis "The Lifting Operations and Lifting Equipment Regulations 1998 (LOLER), SI 1998/2307, regulation 8 (Organisation of lifting operations): 'Every employer shall ensure that every lifting operation involving lifting equipment is-- (a) properly planned by a competent person; (b) appropriately supervised; and (c) carried out in a safe manner.' HSE's own LOLER guidance confirms lifting equipment is in most cases also work equipment, so the Provision and Use of Work Equipment Regulations 1998 (PUWER) inspection/maintenance duties apply alongside LOLER."
          :lift-plan-provenance "https://www.legislation.gov.uk/uksi/1998/2307/regulation/8"
          :installation-notification-basis "LOLER regulation 9(2) (Thorough examination and inspection): 'Every employer shall ensure that, where the safety of lifting equipment depends on the installation conditions, it is thoroughly examined-- (a) after installation and before being put into service for the first time; and (b) after assembly and before being put into service at a new site or in a new location, to ensure that it has been installed correctly and is safe to operate.' A real, load-bearing pre-commissioning verification duty for installed lifting equipment, but NOT a numeric advance-notice-days rule (see ns docstring -- honestly `:qualitative` here, the same shape as DEU's BetrSichV §15 duty, not reused from JPN's different (and real) numeric rule)."
          :installation-notification-provenance "https://www.legislation.gov.uk/uksi/1998/2307/regulation/9"
          :threshold-model :qualitative
          :notification-lead-days nil
          :threshold-note "LOLER 1998 requires a competent-person lift plan (reg 8) and a pre-use thorough examination after installation (reg 9(2)), but Great Britain has no fixed statutory advance-notice-days count for an installation PLAN filing comparable to Japan's Industrial Safety and Health Act Article 88 -- this actor does not invent one. This actor's `:schedule-installation-operation` always routes to a human regardless (see `installation.governor` ns docstring `high-stakes`)."}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any `:schedule-installation-operation`
  proposal that tries to cite one."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-3320 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `installation.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn notification-lead-insufficient?
  "Independently recompute whether `site`'s own recorded
  `:installation-notice-lead-days-actual` (the site's own permanent
  recorded field -- days between the installation-plan filing and the
  planned start of work) falls SHORT of `iso3`'s regulatory minimum lead
  time.

  Three-valued, deliberately (the same shape `demolition.facts/
  notification-lead-insufficient?`/`construction.facts/weather-threshold-
  exceeded?` established):
    true         -- a :quantitative jurisdiction (Japan) whose own numeric
                    minimum lead time is independently confirmed NOT met
                    by the site's own recorded actual -- a bright-line
                    legal violation. The Installation Governor turns this
                    into a HARD, un-overridable hold on `:schedule-
                    installation-operation`.
    false        -- a :quantitative jurisdiction confirmed sufficient.
    :qualitative -- a jurisdiction with NO fixed numeric lead-time (USA/
                    DEU/EU in this catalog). This actor cannot
                    independently confirm 'sufficient' or 'insufficient'
                    by arithmetic alone -- the law itself requires a
                    documented energy-control/pre-commissioning-inspection
                    judgment call. Never fabricate a lead-time here. The
                    Installation Governor relies on its permanent
                    high-stakes gate for `:schedule-installation-
                    operation` (ALWAYS escalates to a human, at every
                    phase) rather than a HARD numeric rule in this case.
    nil          -- no spec-basis at all for `iso3` (a jurisdiction not in
                    `catalog`)."
  [iso3 {:keys [installation-notice-lead-days-actual]}]
  (when-let [{:keys [threshold-model notification-lead-days]} (spec-basis iso3)]
    (case threshold-model
      :quantitative
      (boolean (and (number? installation-notice-lead-days-actual)
                    (< installation-notice-lead-days-actual notification-lead-days)))
      :qualitative
      :qualitative
      nil)))
