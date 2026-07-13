# cloud-itonami-isco-2513

**Community Web Studio** — the ISCO-08 2513 (Web and Multimedia
Developers) actor, an ISCO **Wave 0** occupation per ADR-2607121000:
pure-cognitive work, the LLM-first wave, no robotics gate.

**Maturity: `:implemented`** — WebMultimediaAdvisor ⊣
WebMultimediaGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
12 tests / 26 assertions green.

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

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
