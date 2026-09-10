(ns webstudio.ledger
  "Audit entries for the ISCO-08 2513 community web-studio actor.

  `webstudio.store`'s docstring has always described a ledger — \"append-only
  audit trail, commit or hold\" — but no namespace built one, and the rows
  the actor wrote were assembled inline. Measured on 9e28255, before this
  namespace existed, every row carried exactly `(:disposition :record)` on
  commit and `(:disposition :verdict)` on hold. Two gaps followed.

  The first is common to the pattern. Two `:update-content` commits — one
  the governor cleared outright, one that interrupted at `:request-approval`
  on low confidence and was resumed by a human through `actor/approve!` —
  were byte-identical once the advisor's self-reported `:confidence` was
  removed. That number is the advisor's claim about itself, not evidence
  that anyone resumed the thread. Asked which published sites a human had
  signed off, the ledger could not answer.

  So an entry names its `:authorisation`:

    :governor-clear  the governor returned :ok? true; no human involved.
    :human-sign-off  the run interrupted at :request-approval and a human
                     resumed the thread. The act of resuming IS the
                     approval, so it is recorded as one.
    :governor-hold   the governor refused; nothing was committed.

  The second gap is specific to a web studio. This actor's first HARD
  invariant is licence provenance: material of unknown licensing is
  unusable at any confidence (README; `governor/hard-violations`). That is
  checked against the REGISTERED asset record, and that registration is
  mutable — `store/register-asset!` overwrites it. Measured on the same
  commit, no row mentioned a licence at all: clearing `a-logo`'s licence
  after a site had been committed left the committed row reading exactly as
  before. Under what licence was that site published? The row did not say.
  For a studio whose distinguishing invariant IS provenance, that is the
  one question the ledger is kept to answer.

  So a commit entry carries the `:basis` in force when the governor read
  the register:

    {:licences     {asset-id licence-or-nil}   registered, and what it said
     :unregistered #{asset-id}}                cited, and absent from it

  which keeps the row re-checkable on its own terms, whatever the asset
  register says later. Registered-without-a-licence and never-registered
  are kept apart: both are unusable material, but they are different
  faults with different remedies.

  Three refusals follow from carrying the basis, and they are the reason
  to carry it.

  A basis that covers only some of the cited assets is refused. A partial
  provenance record looks like a complete one, and this actor's second
  invariant exists precisely because \"it looks complete\" is never trusted.

  A `:draft-site` entry whose own basis shows unregistered or unlicensed
  material describes a commit the governor's HARD rules make unreachable —
  HARD violations route to `:hold` and cannot be overridden by sign-off,
  so no authorisation reaches `:commit`. Such a row is not a mis-keyed
  entry but a report that a HARD invariant has stopped holding.

  That refusal is deliberately scoped to `:draft-site`, and not because
  the other ops are exempt by design. Measured on 9e28255, the governor's
  asset rules are gated on `(= :draft-site op)`, so an `:update-content`
  citing unregistered AND unlicensed material returns `:ok? true` with no
  violations at all and commits. Refusing such a row here would assert a
  guarantee the governor does not provide. The ledger instead records the
  basis and lets `unlicensed-material` and `licence-checked?` report it,
  so the gap is answerable rather than silently absent. Closing it is a
  change to what the actor admits, and belongs to the governor.

  Finally, a `:publish-site` entry claiming `:governor-clear` is refused.
  Measured across confidences 0.99 to 0.30, `governor/check` returns
  `:ok? false :escalate? true` for that op at every one of them, so a
  human is its only path to `:commit`.

  `entry` is total and pure: it either returns a well-formed entry or
  throws, and it never reaches a store. Building an entry is not
  appending one."
  (:require [kotoba.lang.text :as str]))

(def authorisations #{:governor-clear :human-sign-off :governor-hold})

(def ^{:doc "Operations that can never be committed on the governor's word
  alone. `webstudio.governor`'s `risky-op?` makes `:publish-site` escalate
  unconditionally, so a human is the only path to `:commit`."}
  human-only-ops
  #{:publish-site})

(def ^{:doc "Operations whose cited material the governor actually checks
  for registration and licensing. Its asset rules are gated on
  `(= :draft-site op)`; the others are NOT exempt by design, they are
  simply unchecked. See the namespace docstring."}
  licence-checked-ops
  #{:draft-site})

(defn- licensed?
  "Did the register record a usable licence for this asset? Blank counts
  as absent — `governor/hard-violations` uses the same `str/blank?` test,
  so the ledger and the rule it reports on agree on what a licence is."
  [licence]
  (not (str/blank? (str licence))))

(defn- basis-fault
  "Why this commit entry's recorded basis fails to justify it, as a
  sentence — or nil if it does."
  [{:keys [licences unregistered] :as basis} record]
  (let [{:keys [op asset-ids]} record
        cited     (set asset-ids)
        covered   (into (set (keys licences)) unregistered)
        uncovered (remove covered cited)
        unlicensed (when (licence-checked-ops op)
                     (keep (fn [[id l]] (when-not (licensed? l) id)) licences))
        absent     (when (licence-checked-ops op)
                     (filter cited unregistered))]
    (cond
      (nil? basis)
      (str ":commit には :basis が要る — 照合したライセンスを記録しない項目は、"
           "素材の登録が書き換わった時点で読めなくなる")

      (not (map? licences))
      (str ":basis の :licences が map でない（受領: " (pr-str licences) "）")

      (seq uncovered)
      (str "引用素材 " (vec (sort uncovered)) " が :basis に載っていない"
           " — 一部だけを記録した provenance は完全なものと同じ形をしており、"
           "この actor の第 2 不変条件はまさにそれを信用しないために在る")

      (seq absent)
      (str (pr-str op) " が未登録素材 " (vec (sort absent))
           " を引いたまま commit されている — 台帳の誤記ではなく、"
           "HARD 不変条件 unknown-asset が効かなくなったという報告である")

      (seq unlicensed)
      (str (pr-str op) " がライセンス未記録の素材 " (vec (sort unlicensed))
           " を引いたまま commit されている — 台帳の誤記ではなく、"
           "HARD 不変条件 unlicensed-asset が効かなくなったという報告である"))))

(defn- fault
  "Why this entry is ill-formed, as a sentence — or nil if it is not."
  [{:keys [disposition authorisation record verdict basis]}]
  (cond
    (not (#{:commit :hold} disposition))
    (str ":disposition は :commit か :hold（受領: " (pr-str disposition) "）")

    (not (authorisations authorisation))
    (str ":authorisation が無い、または未知（受領: " (pr-str authorisation)
         "、既知: " (pr-str (sort authorisations)) "）"
         " — 台帳の項目は、その書き込みを何が許可したかを名乗らなければならない")

    (and (= :commit disposition) (= :governor-hold authorisation))
    ":commit を :governor-hold が許可することはない"

    (and (= :hold disposition) (not= :governor-hold authorisation))
    (str ":hold の :authorisation は :governor-hold のみ（受領: "
         (pr-str authorisation) "）")

    (and (= :commit disposition) (nil? record))
    ":commit には :record が要る"

    (and (= :hold disposition) (nil? verdict))
    ":hold には拒否理由としての :verdict が要る"

    (and (= :commit disposition)
         (= :governor-clear authorisation)
         (human-only-ops (:op record)))
    (str (pr-str (:op record))
         " は外部公開なので governor 単独では commit できない"
         "（README / governor の risky-op?）—"
         " :governor-clear を名乗る項目は、台帳の誤記ではなく"
         " escalation 規則が緩んだという報告である")

    (= :commit disposition)
    (basis-fault basis record)))

(defn entry
  "Build one audit entry. Throws on anything ill-formed — a ledger that
  accepts an entry it cannot interpret is worse than one that refuses,
  because the refusal is visible and the bad entry is not."
  [{:keys [disposition authorisation record verdict basis] :as m}]
  (when-let [f (fault m)]
    (throw (ex-info (str "ill-formed ledger entry: " f) {:entry m :fault f})))
  (cond-> {:disposition   disposition
           :authorisation authorisation}
    record  (assoc :record record)
    basis   (assoc :basis basis)
    verdict (assoc :verdict (select-keys verdict
                                         [:ok? :hard? :escalate? :confidence :violations]))))

(defn basis-of
  "What the asset register said about each cited asset when the governor
  read it. `lookup` is a function of asset-id to the registered asset (in
  the actor, `#(store/asset store %)`).

  Registered assets land in `:licences` mapped to whatever licence was
  recorded — including nil, which is the honest record of an asset that
  was registered without one. Assets absent from the register land in
  `:unregistered`, because \"never registered\" and \"registered with no
  licence\" are different faults with different remedies."
  [lookup asset-ids]
  (reduce (fn [b id]
            (if-let [a (lookup id)]
              (assoc-in b [:licences id] (:license a))
              (update b :unregistered conj id)))
          {:licences {} :unregistered #{}}
          (distinct asset-ids)))

(defn human-signed?
  "Did a human sign this entry off? The question the ledger exists to
  answer, asked of one entry."
  [e]
  (= :human-sign-off (:authorisation e)))

(defn authorisation-of
  "What authorised this write? nil for entries written before this
  namespace existed — which is the honest answer for them, and is
  deliberately not conflated with :governor-clear."
  [e]
  (:authorisation e))

(defn licence-checked?
  "Did the governor's HARD licence and registration rules apply to this
  entry's op? nil when the entry carries no record, so absence is not
  read as a no."
  [e]
  (when-let [op (get-in e [:record :op])]
    (contains? licence-checked-ops op)))

(defn unlicensed-material
  "Cited assets that carried no usable licence when this entry was written,
  unregistered ones included — the licence question, asked of the row
  rather than of the mutable register.

  For a `:draft-site` row this is always empty: the governor's HARD rules
  refuse that material before commit, and `entry` refuses a row claiming
  otherwise. For the ops the governor does not check, this is the answer
  that was previously unavailable — pair it with `licence-checked?` to
  tell an empty result from an unchecked one. nil when the row carries no
  basis, which is the honest answer for pre-`:basis` rows."
  [e]
  (when-let [{:keys [licences unregistered]} (:basis e)]
    (into (set unregistered)
          (keep (fn [[id l]] (when-not (licensed? l) id)) licences))))
