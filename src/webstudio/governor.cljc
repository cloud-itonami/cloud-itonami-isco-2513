(ns webstudio.governor
  "WebMultimediaGovernor — the independent safety/traceability layer
  for the ISCO-08 2513 community web-studio actor (itonami actor
  pattern, ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-4311's bookkeeping.governor. Web-studio twists:
  every cited asset must be REGISTERED WITH A LICENSE (provenance for
  creative material — the spec-basis rule, creative edition), and the
  draft's internal link graph is checked DETERMINISTICALLY for broken
  targets — 'it looks complete' is never trusted.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. asset provenance  — every cited asset must be REGISTERED and
                           belong to this client (no invented or
                           borrowed material).
    4. license basis     — every cited asset must carry a recorded
                           :license. Material of unknown licensing is
                           unusable at any confidence; the remedy is
                           clearing the license, not approving harder.
    5. link integrity    — every internal link in the draft's pages
                           must target a page in the SAME draft. A
                           broken link set is a set-membership fact.
  ESCALATION invariants (:escalate? true, human sign-off):
    6. :op :publish-site (external publication).
    7. low confidence (< `confidence-floor`)."
  (:require [kotoba.lang.text :as str]
            [webstudio.store :as store]))

(def confidence-floor 0.6)

(defn- hard-violations [{:keys [request proposal]} client-record store]
  (let [{:keys [op pages asset-ids]} proposal
        draft? (= :draft-site op)
        resolved (when draft? (mapv #(vector % (store/asset store %)) asset-ids))
        unknown (when draft? (keep (fn [[id a]] (when (nil? a) id)) resolved))
        foreign (when draft?
                  (keep (fn [[id a]]
                          (when (and a (not= (:client-id a) (:client-id request))) id))
                        resolved))
        unlicensed (when draft?
                     (keep (fn [[id a]]
                             (when (and a (= (:client-id a) (:client-id request))
                                        (str/blank? (str (:license a))))
                               id))
                           resolved))
        page-ids (when draft? (set (map :page-id pages)))
        broken (when draft?
                 (for [p pages
                       target (:links p)
                       :when (not (contains? page-ids target))]
                   [(:page-id p) target]))]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and draft? (seq unknown))
      (conj {:rule :unknown-asset
             :detail (str "未登録素材: " (vec unknown) "（出所不明素材の使用禁止）")})

      (and draft? (seq foreign))
      (conj {:rule :asset-wrong-client :detail (str "別 client の素材: " (vec foreign))})

      (and draft? (seq unlicensed))
      (conj {:rule :unlicensed-asset
             :detail (str "ライセンス未記録の素材: " (vec unlicensed)
                          "（是正はライセンスの確認と記録 — 承認の強行ではない）")})

      (and draft? (seq broken))
      (conj {:rule :broken-internal-link
             :detail (str "リンク切れ: " (vec (take 5 broken))
                          "（内部リンクは同一 draft 内のページを指すこと）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `webstudio.store/Store`. Pure — never mutates
  the store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        hard (hard-violations {:request request :proposal proposal}
                              client-record store)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (= :publish-site (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
