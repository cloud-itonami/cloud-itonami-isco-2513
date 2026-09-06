# cloud-itonami-isco-2513

**Community Web Studio** — the ISCO-08 2513 (Web and Multimedia
Developers) actor, an ISCO **Wave 0** occupation per ADR-2607121000:
pure-cognitive work, the LLM-first wave, no robotics gate.

**Maturity: `:implemented`** — WebMultimediaAdvisor ⊣
WebMultimediaGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
37 tests / 63 assertions green.

The web-studio-specific HARD invariants:

1. **License provenance** — every cited asset must be REGISTERED with
   a recorded license (the fleet's spec-basis rule, creative-work
   edition). Material of unknown licensing is unusable at any
   confidence; the remedy is clearing the license, not approving
   harder. Foreign/unregistered assets are equally held.
2. **Link integrity** — every internal link must target a page inside
   the same draft (a set-membership fact; "it looks complete" is never
   trusted).

Escalations (always human sign-off): `:publish-site` (external
publication), low confidence (< 0.6).

## The audit ledger

`webstudio.ledger` builds every row the actor appends, and refuses
malformed ones rather than storing what it cannot interpret. Each row
names its `:authorisation` — `:governor-clear`, `:human-sign-off` or
`:governor-hold` — and each commit carries the `:basis` that was in
force when the governor read the asset register:

```clojure
{:licences     {"a-logo" "CC-BY-4.0"}   ; registered, and what it said
 :unregistered #{"a-ghost"}}            ; cited, and absent from it
```

Both exist because the register is mutable. Measured on `9e28255`,
before the namespace existed, an automatic commit and one a human
signed off after a low-confidence escalation were identical once the
advisor's self-reported `:confidence` was removed, and no row mentioned
a licence at all — so clearing an asset's licence left an
already-published site reading exactly as before. For a studio whose
first invariant is provenance, that was the one question the ledger was
kept to answer.

**Known gap — the governor checks material only for `:draft-site`.**
Its asset rules are gated on the op, so an `:update-content` citing
unregistered *and* unlicensed material returns `:ok? true` with no
violations and commits. The ledger does not refuse those rows: refusing
them would assert a guarantee the governor does not give. It records
the basis and reports it through `unlicensed-material` and
`licence-checked?` instead, so the gap is answerable rather than
silently absent. Closing it changes what the actor admits and belongs
to `webstudio.governor`.

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
