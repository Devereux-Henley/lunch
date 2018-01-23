(ns lunch.core
  (:require
   [clojure.core.async :refer [<!!]]
   [datomic.api :as datomic]))

(comment

  "REPL requires."

  (require
    '[clojure.core.async :refer (<!!)]
    '[datomic.api :as datomic])

  (def conn (make-conn))

  "Transact requirement schema"

  @(datomic/transact conn [{:db/ident       :requirement/name
                            :db/valueType   :db.type/string
                            :db/unique      :db.unique/value
                            :db/cardinality :db.cardinality/one
                            :db/doc         "The name of a requirement."}
                           {:db/ident       :requirement/description
                            :db/valueType   :db.type/string
                            :db/cardinality :db.cardinality/one
                            :db/doc         "The text description of a requirement."}
                           {:db/ident       :requirement/children
                            :db/isComponent true
                            :db/valueType   :db.type/ref
                            :db/cardinality :db.cardinality/many
                            :db/doc "Represents child nodes of a requirement tree."}])

  "Transact data into the system"

  @(datomic/transact conn [{:requirement/name "ATG001"
                            :requirement/description "The application must compile"
                            :requirement/children [{:requirement/name "ATG002"
                                                    :requirement/description "The application must not have type errors at compile time"}
                                                   {:requirement/name "ATG003"
                                                    :requirement/description "The application must not have preprocessor errors at compile time"}
                                                   {:requirement/name "ATG004"
                                                    :requirement/description "The application must not have undefined symbols at compile time"}]}])

  "Find all children of first requirement"
  (let [db (datomic/db conn)]
    (datomic/q
      '[:find ?child-name
        :in $
        :where
        [?requirement :requirement/name "ATG001"]    ;; Find a requirement such that the name == "ATG001"
        [?child :requirement/name ?child-name]       ;; Find child relations such that the name is the name we are searching for.
        [?requirement :requirement/children ?child]] ;; Assert that the children we are looking for are the children of the previously selected requirement.
      db))

  "Attributes are attached to entities, represented by a unique identifier"
  (def requirement-entity-id
    (let [db (datomic/db conn)]
      (datomic/q
        '[:find ?requirement-entity-id .
          :in $
          :where [?requirement-entity-id :requirement/name "ATG001"]]
        db)))

  "Graphs support free association"
  @(datomic/transact conn [{:db/ident :requirement/priority
                            :db/valueType :db.type/ref
                            :db/cardinality :db.cardinality/one
                            :db/doc "Priority that a requirement is met."}
                           {:db/ident :priority/high}
                           {:db/ident :priority/medium}
                           {:db/ident :priority/low}])

  "Database/Transaction functions"
  @(datomic/transact conn [{:db/ident :requirement/cycle-priority
                            :db/fn (datomic/function '{:lang :clojure
                                                       :params [db e]
                                                       :code
                                                       [[:db/add
                                                         e
                                                         :requirement/priority
                                                         (let [priority (datomic.api/q
                                                                          '[:find ?priority .
                                                                            :in $ ?eid
                                                                            :where
                                                                            [?priority-entity :db/ident ?priority]
                                                                            [?eid :requirement/priority ?priority-entity]]
                                                                          db e)]
                                                           (case priority
                                                             :priority/low    :priority/medium
                                                             :priority/medium :priority/high
                                                             :priority/high   :priority/low
                                                             :priority/low))]]})}])

  "Can target values using either entity id or ident"

  @(datomic/transact conn [{:db/id requirement-entity-id
                            :requirement/priority :priority/high
                            :requirement/children [{:db/id [:requirement/name "ATG002"]
                                                    :requirement/priority :priority/low}
                                                   {:db/id [:requirement/name "ATG003"]
                                                    :requirement/priority :priority/medium}
                                                   {:db/id [:requirement/name "ATG004"]
                                                    :requirement/priority :priority/low}]}])

  @(datomic/transact conn [[:db/add [:requirement/name "ATG001"] :requirement/priority :priority/low]])

  (let [db (datomic/db conn)]
    (datomic/q '[:find [?requirement-name ?requirement-priority]
                 :in $ ?requirement-entity-id
                 :where
                 [?requirement-entity-id :requirement/name ?requirement-name]
                 [?priority-entity :db/ident ?requirement-priority]
                 [?requirement-entity-id :requirement/priority ?priority-entity]]
      db
      requirement-entity-id))

  "Pull API => declarative graph queries"

  (let [db (datomic/db conn)]
    (datomic/pull db [{:requirement/children [:requirement/name :requirement/priority]}] requirement-entity-id))

  "Entity API => ORM equivalent"

  (def requirement-entity
    (let [db (datomic/db conn)]
      (datomic/entity db requirement-entity-id)))

  requirement-entity

  (:requirement/name requirement-entity)

  (:requirement/children requirement-entity)

  (map :requirement/name (:requirement/children requirement-entity))

  (datomic/touch requirement-entity)

  "Time travel"

  (def ye-olden-times (java.util.Date.))

  (let [db (datomic/as-of (datomic/db conn) ye-olden-times)]
    (datomic/pull db [{:requirement/priority [:db/ident]}] requirement-entity-id))

  @(datomic/transact conn [[:requirement/cycle-priority requirement-entity-id]])

  (datomic/pull (datomic/db conn) [{:requirement/priority [:db/ident]}] requirement-entity-id)

  (sort-by
    second
    (datomic/q
      '[:find ?priority ?inst
        :in $ ?eid
        :where
        [?eid ?a ?v ?tx true]
        [?tx :db/txInstant ?inst]
        [?eid :requirement/priority ?priority-eid]
        [?priority-eid :db/ident ?priority]]
      (datomic/history (datomic/db conn)) requirement-entity-id))

  )

(defonce database-uri
  "datomic:free://localhost:4334/Lunch")

(defn create-database
  []
  (datomic/create-database database-uri))

(defn delete-database
  []
  (datomic/delete-database database-uri))

(defn make-conn
  []
  (datomic/connect database-uri))
