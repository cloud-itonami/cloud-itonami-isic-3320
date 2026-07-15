# Governance

`cloud-itonami-isic-3320` is an OSS open-business blueprint for
industrial-machinery-installation-project operations coordination --
coordination-only, never heavy-lift/rigging-equipment control or
commissioning-energization sign-off.

## Maintainers

Maintainers may merge changes that preserve these invariants:
- this actor never holds heavy-lift/rigging-equipment-control authority.
- this actor never holds commissioning-energization sign-off authority --
  that remains the licensed engineer / site supervisor's exclusively.
- every proposal this actor's advisor produces carries `:effect
  :propose`, and the Installation Governor remains independent of the
  advisor.
- hard policy violations (unknown op, non-`:propose` effect, forbidden
  action class, unverified site, missing legal basis, incomplete lift
  plan, insufficient notification lead time, unresolved safety concern)
  cannot be overridden by human approval.
- `:schedule-installation-operation` and `:flag-safety-concern` always
  require human sign-off, at every phase, unconditionally.
- every proposal, sign-off, log entry and notification path is
  auditable.
- sensitive operating and personal data stays outside Git.
- no JVM-only interop is added to `src/` (this build's cljs-first
  `.cljc` runtime-priority mandate) -- a real notification transport, if
  ever added, must go behind `installation.notify/Notifier` via a
  portable (cljs/nbb) HTTP client, not `java.net.http`.

## Decision Records

Architecture decisions should be documented (an ADR or equivalent) when
changing the trust model, storage contract, closed op-allowlist, business
model, operator certification or license.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, safety, audit and
data-flow review.

Certified operators can lose certification for:
- bypassing the Installation Governor's hard checks or the closed
  op-allowlist
- attempting to extend this actor's authority into heavy-lift/rigging-
  equipment control or commissioning-energization sign-off
- mishandling sensitive data
- misrepresenting certification status
- failing to respond to security or safety incidents
