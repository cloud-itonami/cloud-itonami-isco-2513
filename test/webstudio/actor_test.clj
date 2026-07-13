(ns webstudio.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [webstudio.actor :as actor]
            [webstudio.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Pan-ya Hana"})
    (store/register-asset! st {:asset-id "a-logo" :client-id "client-1"
                               :kind :image :license "CC-BY-4.0"})
    st))

(deftest commits-a-clean-draft
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :draft-site :stake :low
                 :pages [{:page-id "home" :links []}]
                 :asset-ids ["a-logo"]}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-a-broken-link-draft
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :draft-site :stake :low
                 :pages [{:page-id "home" :links ["nowhere"]}]
                 :asset-ids ["a-logo"]}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-publishes-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :publish-site :stake :medium}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
