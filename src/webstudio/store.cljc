(ns webstudio.store
  "SSoT for the ISCO-08 2513 community web-studio actor (itonami actor
  pattern, ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client — a registered organization (:client-id, :name)
    asset  — a registered media/content asset {:asset-id :client-id
             :kind :license}. The ONLY admissible building material —
             an asset without a recorded license is unusable, full
             stop (license provenance is the creative-work edition of
             the fleet's spec-basis rule).
    record — a committed operating record (site draft, published site,
             content update) — written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold. Entries are built
             by `webstudio.ledger/entry`, which refuses malformed ones;
             `append-ledger!` itself is a dumb sink and does not check.
             A row names what authorised it and, on commit, the licence
             basis in force when the governor read the register — the
             asset register is mutable, so a row that did not carry it
             stopped being readable the moment a licence changed."
  )

(defprotocol Store
  (client [s client-id])
  (asset [s asset-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-asset! [s a])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (asset [_ asset-id] (get-in @a [:assets asset-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-asset! [s x]
    (swap! a assoc-in [:assets (:asset-id x)] x) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :assets {} :records [] :ledger []}
                                   seed)))))
