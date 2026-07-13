(ns webstudio.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [webstudio.store :as store]
            [webstudio.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Pan-ya Hana"})
    (store/register-asset! st {:asset-id "a-logo" :client-id "client-1"
                               :kind :image :license "CC-BY-4.0"})
    (store/register-asset! st {:asset-id "a-unknown-license" :client-id "client-1"
                               :kind :image :license nil})
    st))

(defn- draft [pages asset-ids]
  {:op :draft-site :effect :propose :pages pages :asset-ids asset-ids
   :confidence 0.9 :stake :low})

(def ^:private good-pages
  [{:page-id "home" :links ["menu"]}
   {:page-id "menu" :links ["home"]}])

(def ^:private req {:client-id "client-1"})

(deftest ok-on-clean-draft
  (let [st (fresh-store)
        v (governor/check req {} (draft good-pages ["a-logo"]) st)]
    (is (:ok? v))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (draft good-pages ["a-logo"]) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (draft good-pages ["a-logo"])
                                        :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-invented-asset
  (let [st (fresh-store)
        v (governor/check req {} (draft good-pages ["a-ghost"]) st)]
    (is (:hard? v))
    (is (some #(= :unknown-asset (:rule %)) (:violations v)))))

(deftest hard-on-foreign-asset
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (store/register-asset! st {:asset-id "a-theirs" :client-id "client-2"
                               :kind :image :license "MIT"})
    (let [v (governor/check req {} (draft good-pages ["a-theirs"]) st)]
      (is (:hard? v))
      (is (some #(= :asset-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unlicensed-asset
  (testing "material of unknown licensing is unusable at any confidence"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (draft good-pages ["a-unknown-license"])
                                          :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :unlicensed-asset (:rule %)) (:violations v))))))

(deftest hard-on-broken-internal-link
  (testing "a link to a page not in the draft is a set-membership fact"
    (let [st (fresh-store)
          v (governor/check req {} (draft [{:page-id "home" :links ["missing-page"]}]
                                          ["a-logo"]) st)]
      (is (:hard? v))
      (is (some #(= :broken-internal-link (:rule %)) (:violations v))))))

(deftest escalates-site-publication
  (let [st (fresh-store)
        v (governor/check req {} {:op :publish-site :effect :propose
                                  :confidence 0.9 :stake :medium} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} {:op :update-content :effect :propose
                                  :confidence 0.3 :stake :low} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
