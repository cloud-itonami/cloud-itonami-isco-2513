(ns webstudio.advisor
  "WebMultimediaAdvisor — proposes a web-studio operation (draft a
  site, publish a site, update content) for a registered organization.
  Swappable mock/llm; the advisor ONLY proposes — `webstudio.governor`
  checks asset licensing and link integrity independently. Modeled on
  cloud-itonami-isco-4311's advisor.

  A proposal: {:op :draft-site|:publish-site|:update-content
               :effect :propose
               :pages [{:page-id str :links [page-id ...]} ...]
               :asset-ids [str ...]
               :stake kw :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake pages asset-ids] :as request}]
  {:op op
   :effect :propose
   :pages (vec pages)
   :asset-ids (vec asset-ids)
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a web studio advisor. Given a request, propose an :op, the
   :pages with their internal :links, the cited :asset-ids, an honest
   :confidence and a :stake. Never use material whose license you have
   not seen — the governor checks provenance.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
