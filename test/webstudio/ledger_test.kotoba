(ns webstudio.ledger-test
  "The refusals are the reason the basis is carried, so each one is asked
  for by name, and each acceptance that looks like a refusal-in-waiting is
  pinned as a control."
  (:require [kotoba.lang.text]
            [clojure.test :refer [deftest is testing]]
            [webstudio.actor :as actor]
            [webstudio.advisor :as advisor]
            [webstudio.ledger :as ledger]
            [webstudio.store :as store]))

;; ---------------------------------------------------------------- helpers

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Pan-ya Hana"})
    (store/register-asset! st {:asset-id "a-logo" :client-id "client-1"
                               :kind :image :license "CC-BY-4.0"})
    (store/register-asset! st {:asset-id "a-nolicence" :client-id "client-1"
                               :kind :image :license nil})
    st))

(defn- fixed-advisor
  "An advisor that reports exactly the confidence it is handed, so two runs
  can differ in nothing but that self-report."
  [conf]
  (reify advisor/Advisor
    (-advise [_ _store req]
      {:op (:op req) :effect :propose
       :pages (vec (:pages req)) :asset-ids (vec (:asset-ids req))
       :stake :low :confidence conf :rationale "fixed"})))

(defn- commit-entry
  "A well-formed :commit entry, before whichever key a test spoils."
  [& {:as overrides}]
  (merge {:disposition :commit
          :authorisation :governor-clear
          :record {:client-id "client-1" :op :draft-site :asset-ids ["a-logo"]}
          :basis {:licences {"a-logo" "CC-BY-4.0"} :unregistered #{}}}
         overrides))

(defn- fault-of
  "The refusal sentence `entry` throws for `m`, or nil if it accepts it."
  [m]
  (try (ledger/entry m) nil
       (catch clojure.lang.ExceptionInfo e (:fault (ex-data e)))))

(defn- refused-because
  "Did `entry` refuse `m` for the stated reason? The reason is pinned, not
  just the refusal: measured while building this suite, disabling the
  missing-basis rule left `entry` still refusing a basis-less commit -- the
  next clause caught it as a malformed :licences map -- so a test asserting
  only THAT it refused stayed green while the rule it named was gone."
  [reason m]
  (some-> (fault-of m) (kotoba.lang.text/includes? reason)))

;; ------------------------------------------------- the entry is well formed

(deftest accepts-a-well-formed-commit
  (testing "the control: nothing below is red merely because entry refuses everything"
    (let [e (ledger/entry (commit-entry))]
      (is (= :commit (:disposition e)))
      (is (= :governor-clear (:authorisation e)))
      (is (= {"a-logo" "CC-BY-4.0"} (get-in e [:basis :licences]))))))

(deftest refuses-a-disposition-that-is-neither-commit-nor-hold
  (is (refused-because ":disposition は :commit か :hold"
                       (commit-entry :disposition :maybe))))

(deftest refuses-an-entry-that-does-not-name-its-authorisation
  (testing "the gap this namespace was built for: a row that does not say what let it be written"
    (is (refused-because ":authorisation が無い、または未知"
                         (dissoc (commit-entry) :authorisation)))))

(deftest refuses-an-unknown-authorisation
  (is (refused-because ":authorisation が無い、または未知"
                       (commit-entry :authorisation :looked-fine))))

(deftest refuses-a-commit-authorised-by-a-hold
  (is (refused-because ":commit を :governor-hold が許可することはない"
                       (commit-entry :authorisation :governor-hold))))

(deftest refuses-a-hold-not-authorised-by-the-governor
  (is (refused-because ":hold の :authorisation は :governor-hold のみ"
                       {:disposition :hold :authorisation :human-sign-off
                        :verdict {:hard? true}})))

(deftest refuses-a-commit-without-a-record
  (is (refused-because ":commit には :record が要る"
                       (dissoc (commit-entry) :record))))

(deftest refuses-a-hold-without-a-verdict
  (testing "a hold that does not say why is not an audit trail"
    (is (refused-because ":hold には拒否理由としての :verdict が要る"
                         {:disposition :hold :authorisation :governor-hold}))))

(deftest accepts-a-hold-that-names-the-governor-and-its-reason
  (let [e (ledger/entry {:disposition :hold :authorisation :governor-hold
                         :verdict {:ok? false :hard? true
                                   :violations [{:rule :broken-internal-link}]}})]
    (is (= :governor-hold (:authorisation e)))
    (is (= [{:rule :broken-internal-link}] (get-in e [:verdict :violations])))))

;; ------------------------------------------------------- escalation refusal

(deftest refuses-a-publish-site-claiming-governor-clear
  (testing "measured: governor/check returns :ok? false for :publish-site at every confidence"
    (is (refused-because "escalation 規則が緩んだ"
                         (commit-entry :record {:client-id "client-1"
                                                :op :publish-site
                                                :asset-ids ["a-logo"]})))))

(deftest accepts-a-publish-site-a-human-signed
  (testing "the control for the refusal above: the op is not banned, the claim was"
    (is (nil? (fault-of (commit-entry :authorisation :human-sign-off
                                      :record {:client-id "client-1"
                                               :op :publish-site
                                               :asset-ids ["a-logo"]}))))))

;; ------------------------------------------------------------ basis refusals

(deftest refuses-a-commit-without-a-basis
  (testing "pinned to the rule's own sentence: the next clause also says :basis"
    (is (refused-because ":commit には :basis が要る"
                         (dissoc (commit-entry) :basis)))))

(deftest refuses-a-basis-that-covers-only-some-of-the-cited-assets
  (testing "a partial provenance record has the same shape as a complete one"
    (is (refused-because "が :basis に載っていない"
                         (commit-entry
                          :record {:client-id "client-1" :op :draft-site
                                   :asset-ids ["a-logo" "a-uncovered"]})))))

(deftest refuses-a-draft-whose-own-basis-shows-unregistered-material
  (testing "describes a commit the HARD rule unknown-asset makes unreachable"
    (is (refused-because "unknown-asset"
                         (commit-entry
                          :record {:client-id "client-1" :op :draft-site
                                   :asset-ids ["a-ghost"]}
                          :basis {:licences {} :unregistered #{"a-ghost"}})))))

(deftest refuses-a-draft-whose-own-basis-shows-unlicensed-material
  (testing "describes a commit the HARD rule unlicensed-asset makes unreachable"
    (is (refused-because "unlicensed-asset"
                         (commit-entry
                          :record {:client-id "client-1" :op :draft-site
                                   :asset-ids ["a-nolicence"]}
                          :basis {:licences {"a-nolicence" nil} :unregistered #{}})))))

(deftest refuses-a-draft-whose-basis-records-a-blank-licence
  (testing "blank counts as absent, the same test the governor applies"
    (is (refused-because "unlicensed-asset"
                         (commit-entry
                          :record {:client-id "client-1" :op :draft-site
                                   :asset-ids ["a-blank"]}
                          :basis {:licences {"a-blank" "  "} :unregistered #{}})))))

(deftest accepts-an-update-content-citing-material-the-governor-never-checked
  (testing "the exemption control. Measured on 9e28255, governor/check returns
            :ok? true with no violations for :update-content citing unregistered
            and unlicensed material. Refusing the row here would assert a
            guarantee the governor does not give; the ledger reports it instead"
    (let [m (commit-entry :record {:client-id "client-1" :op :update-content
                                   :asset-ids ["a-ghost" "a-nolicence"]}
                          :basis {:licences {"a-nolicence" nil}
                                  :unregistered #{"a-ghost"}})]
      (is (nil? (fault-of m)))
      (is (= #{"a-ghost" "a-nolicence"} (ledger/unlicensed-material (ledger/entry m)))
          "and it is answerable, which is the point")
      (is (false? (ledger/licence-checked? (ledger/entry m)))
          "paired with the flag that keeps it from reading as a clean bill"))))

;; ------------------------------------------------------------- reading back

(deftest basis-of-keeps-never-registered-and-registered-without-a-licence-apart
  (let [b (ledger/basis-of {"a-logo" {:license "CC-BY-4.0"}
                            "a-nolicence" {:license nil}}
                           ["a-logo" "a-nolicence" "a-ghost"])]
    (is (= {"a-logo" "CC-BY-4.0" "a-nolicence" nil} (:licences b)))
    (is (= #{"a-ghost"} (:unregistered b))
        "different faults with different remedies")))

(deftest human-signed-distinguishes-the-two-ways-a-commit-is-authorised
  (is (true? (ledger/human-signed? {:authorisation :human-sign-off})))
  (is (false? (ledger/human-signed? {:authorisation :governor-clear}))))

(deftest authorisation-of-is-nil-for-rows-written-before-this-namespace
  (testing "not conflated with :governor-clear -- nobody measured those"
    (is (nil? (ledger/authorisation-of {:disposition :commit :record {}})))))

(deftest unlicensed-material-is-nil-rather-than-empty-for-pre-basis-rows
  (is (nil? (ledger/unlicensed-material {:disposition :commit :record {}}))))

;; ------------------------------------- the properties that motivated all this

(deftest the-two-same-op-commits-are-now-distinguishable
  (testing "measured identical on 9e28255 once the advisor's self-report was removed"
    (let [st (fresh-store)
          req {:client-id "client-1" :op :update-content :stake :low
               :pages [{:page-id "home" :links []}] :asset-ids ["a-logo"]}]
      (actor/run-request! (actor/build-graph {:store st :advisor (fixed-advisor 0.95)})
                          req {} "auto")
      (let [g (actor/build-graph {:store st :advisor (fixed-advisor 0.30)})]
        (actor/run-request! g req {} "human")
        (actor/approve! g "human"))
      (let [[a b] (filterv #(= :commit (:disposition %)) (store/ledger st))]
        (is (= :governor-clear (ledger/authorisation-of a)))
        (is (= :human-sign-off (ledger/authorisation-of b)))
        (is (not= (ledger/human-signed? a) (ledger/human-signed? b))
            "the question the ledger could not answer before")))))

(deftest a-resumed-publication-is-recorded-as-human-signed
  (let [st (fresh-store)
        g (actor/build-graph {:store st})]
    (actor/run-request! g {:client-id "client-1" :op :publish-site :stake :medium}
                        {} "t")
    (actor/approve! g "t")
    (let [e (first (filter #(= :commit (:disposition %)) (store/ledger st)))]
      (is (ledger/human-signed? e)))))

(deftest a-committed-row-survives-the-asset-being-relicensed
  (testing "the headline property: register-asset! overwrites, and on 9e28255
            clearing a licence left an already-committed site reading the same"
    (let [st (fresh-store)
          g (actor/build-graph {:store st})]
      (actor/run-request! g {:client-id "client-1" :op :draft-site :stake :low
                             :pages [{:page-id "home" :links []}]
                             :asset-ids ["a-logo"]}
                          {} "t")
      (store/register-asset! st {:asset-id "a-logo" :client-id "client-1"
                                 :kind :image :license nil})
      (is (nil? (:license (store/asset st "a-logo")))
          "the register really did change under the row")
      (let [e (first (filter #(= :commit (:disposition %)) (store/ledger st)))]
        (is (= "CC-BY-4.0" (get-in e [:basis :licences "a-logo"]))
            "under what licence was that site published -- now answerable")
        (is (empty? (ledger/unlicensed-material e)))))))

(deftest a-hold-row-names-the-governor-as-its-authorisation
  (let [st (fresh-store)
        g (actor/build-graph {:store st})]
    (actor/run-request! g {:client-id "client-1" :op :draft-site :stake :low
                           :pages [{:page-id "home" :links ["nowhere"]}]
                           :asset-ids ["a-logo"]}
                        {} "t")
    (let [e (first (filter #(= :hold (:disposition %)) (store/ledger st)))]
      (is (= :governor-hold (ledger/authorisation-of e)))
      (is (seq (get-in e [:verdict :violations]))))))
