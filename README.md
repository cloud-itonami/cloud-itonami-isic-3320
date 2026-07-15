# cloud-itonami-isic-3320

Open Business Blueprint for **ISIC Rev.5 3320**: installation of industrial machinery and equipment.

This repository designs a forkable OSS business for industrial-machinery-
installation-project operations coordination: run by a qualified operator
so a community keeps its own operating records instead of renting a
closed SaaS.

## Scope -- this is a COORDINATION-ONLY actor, not equipment control

This is a safety-critical domain: heavy lifting/rigging, alignment work,
and commissioning of potentially energized equipment. **This actor does
NOT hold heavy-lift/rigging-equipment-control authority, and it does NOT
hold commissioning-energization sign-off authority.** Both are the
licensed engineer / site supervisor's exclusive authority, always. The
Installation Advisor (LLM) never issues an equipment-control command and
never signs off on energizing or commissioning a machine; the independent
**Installation Governor** HARD-blocks any proposal that even tries
(un-overridable by any human approval -- see `installation.governor` ns
docstring). This actor coordinates *potential* rigging/alignment/
commissioning-test dispatch (a proposed schedule window, a flagged
concern, a supply-order proposal) -- it never directly actuates.

Structurally, EVERY proposal this actor's advisor can produce carries
`:effect :propose`, and the Installation Governor HARD-holds any proposal
that doesn't -- this is a permanent invariant distinguishing this actor
from `cloud-itonami-isic-4211` (the robotics-premise reference this
actor follows structurally), whose sibling actuation ops DO commit
real-world effects (mail dispatch, robot placement, structure handover).
`cloud-itonami-isic-4211`'s README robotics-premise framing therefore
does NOT apply verbatim here: this actor is deliberately narrower, the
SAME coordination-only shape `cloud-itonami-isic-4311` (Demolition) and
`cloud-itonami-isic-4210` (Roads/railways construction) already
established in this fleet.

## Core Contract

```text
site/project record + independent verification
        |
        v
Advisor -> Installation Governor -> proceed (log/schedule/flag/order proposal), hold, or human approval
        |
        v
coordination artifacts (schedule proposal, safety-concern flag,
supply-order proposal) + audit ledger -- NEVER heavy-lift/rigging-
equipment dispatch, NEVER a commissioning-energization sign-off
```

No automated advice can propose a schedule the governor refuses, suppress
a safety-concern flag, or slip an equipment-control/commissioning-sign-
off marker past the governor -- and even a clean, governor-approved
proposal still always needs a human sign-off for scheduling and safety
concerns (see `Actuation` below).

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `3320`). Required capabilities:

- `:identity`
- `:forms`
- `:audit-ledger`

## Implemented slice (`src/installation`)

`blueprint.edn` names the governor `:installation-governor` and is now
`:implemented`. This repo implements it end-to-end -- **Installation
Advisor ⊣ Installation Governor** -- following the SAME `.cljc` actor
pattern (langgraph-clj StateGraph, mock-by-default advisor, dual
MemStore/Datomic backend, 0→3 phase rollout) every prior
`cloud-itonami-isic-*` actor in this fleet uses, structured after
[`cloud-itonami-isic-4311`](https://github.com/cloud-itonami/cloud-itonami-isic-4311)
(Demolition -- the closest structural analog: also a coordination-only
actor narrower than the `cloud-itonami-isic-4211` robotics-premise
reference), narrowed to industrial-machinery-installation/commissioning
coordination as described above.

### Closed op-allowlist (4 ops, all `:effect :propose`)

| Op | Ask | Implementation |
|---|---|---|
| `:log-installation-record` | rigging-plan / alignment / progress data logging | Normalizes and commits a patch onto the site's ground-truth fields (`:site-verified?`, `:lift-plan-approved?`, `:installation-notice-lead-days-actual`, concern resolution, etc.) and appends an immutable installation-record-log entry. No direct capital/safety risk -- MAY auto-commit at phase 3. |
| `:schedule-installation-operation` | rigging/alignment/commissioning-test scheduling proposal | Drafts a proposed schedule WINDOW (never a finalized rigging plan or commissioning-energization sign-off). ALWAYS escalates to a human at every phase -- coordinates potential heavy-lift/rigging-equipment dispatch and pre-energization work. |
| `:flag-safety-concern` | surface a rigging / lockout-tagout / energization-hazard concern | Drafts a safety-concern flag; ALWAYS escalates to a human, unconditionally. Once approved, `installation.notify` sends the notice (mail + phone, mock only -- see `Actuation`) to the site's licensed-engineer/site-supervisor contact roster. |
| `:order-supplies` | rigging-hardware / spare-parts procurement proposal | Drafts a supply-order proposal. Escalates above a cost threshold or below the confidence floor; may auto-commit at phase 3 otherwise. |

**Legal basis is data, not code** -- `src/installation/facts.cljc`'s
`catalog` is the per-jurisdiction EDN source-of-truth the governor checks
every `:schedule-installation-operation` proposal against (JPN/USA/DEU
seeded; DEU stands in for the EU, the same convention
`construction.facts`/`demolition.facts`/`aerospace.facts` use for EASA):

| Jurisdiction | Lift-plan legal basis | Installation-notification legal basis |
|---|---|---|
| 🇯🇵 Japan | クレーン等安全規則（昭和47年労働省令第34号）第74条の2（作業計画の作成義務） -- [e-Gov](https://laws.e-gov.go.jp/law/347M50002000034) | 労働安全衛生法（昭和47年法律第57号）第88条第1項（特定機械等の設置計画、工事開始の30暦日前までの届出義務） -- [e-Gov](https://laws.e-gov.go.jp/law/347AC0000000057) |
| 🇺🇸 USA | OSHA 29 CFR Part 1926 Subpart CC (Cranes and Derricks in Construction) -- [osha.gov](https://www.osha.gov/laws-regs/regulations/standardnumber/1926/1926SubpartCC) | OSHA 29 CFR 1910.147 (Control of Hazardous Energy -- Lockout/Tagout) -- [osha.gov](https://www.osha.gov/laws-regs/regulations/standardnumber/1910/1910.147) |
| 🇪🇺 EU (DEU proxy) | Betriebssicherheitsverordnung (BetrSichV) §15 -- Prüfung vor Inbetriebnahme -- [gesetze-im-internet.de](https://www.gesetze-im-internet.de/betrsichv_2015/__15.html) | Directive 2006/42/EC (Machinery Directive), Annexes VI/VII -- [EUR-Lex](https://eur-lex.europa.eu/eli/dir/2006/42/oj/eng) |

Japan has a real numeric installation-notification lead-time trigger (30
calendar days, Industrial Safety and Health Act Article 88 paragraph 1)
-- the USA and the EU deliberately do NOT for THIS specific proposal type
(an installation/relocation PLAN filing) --
`installation.facts/notification-lead-insufficient?` reports
`:qualitative` there rather than fabricating a number, and `:schedule-
installation-operation` always routes to a human regardless of
jurisdiction anyway (see `Actuation` below). See `installation.facts` ns
docstring for the full honesty discipline, including why this catalog
has only ONE `:quantitative` jurisdiction where `demolition.facts` found
two (JPN and USA) for a different proposal type.

**Governor -- eight HARD checks, ALL un-overridable by human approval:**
unknown op (outside the closed 4-op allowlist), `:effect` not `:propose`,
forbidden action class (heavy-lift/rigging-equipment-control /
direct-actuation / commissioning-energization-sign-off markers), site
not independently verified/registered, legal-basis missing, lift plan
incomplete, notification lead time insufficient (quantitative
jurisdictions only), unresolved safety concern on file. See
`installation.governor` ns docstring for the full enumeration, rationale
and real-law citations behind each.

## Actuation

This actor performs **no real-world actuation** -- every committed
record carries `:effect :propose` (see `installation.governor` ns
docstring). `:schedule-installation-operation` and `:flag-safety-concern`
NEVER auto-commit at any phase -- both always need a human sign-off, even
when the governor is completely clean (`installation.phase` ns docstring
'Actuation' section, `installation.governor`'s `high-stakes` set).
`:log-installation-record` (pure data logging) and `:order-supplies`
BELOW the cost threshold (`installation.governor/supply-order-cost-
threshold-usd`) MAY auto-commit at phase 3 when the governor is clean.

This build also deliberately ships **NO JVM-only interop anywhere in
`src/`** -- `installation.notify` ships only the deterministic mock
`Notifier` (no real Resend/Twilio transport), per this workspace's
cljs-first `.cljc` runtime-priority rule. A real transport can be added
later behind the same protocol via a portable HTTP client without
changing this actor's shape.

```bash
clojure -M:dev:run    # demo: full coordination episode + every HARD hold
clojure -M:dev:test   # test suite
clojure -M:lint       # clj-kondo, errors fail
```

## License

AGPL-3.0-or-later.
