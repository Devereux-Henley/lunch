(ns lunch.core
  (:require
   [clojure.core.async :refer [<!!]]
   [datomic.api :as datomic]))

(comment

  "REPL requires."

  (require
    '[clojure.core.async :refer (<!!)]
    '[datomic.api :as datomic])

  "Create a connection"

  (def conn (make-conn))

  "Transact requirement schema"

  @(datomic/transact conn [{:db/ident       :requirement/name
                            :db/valueType   :db.type/string
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
        [?requirement :requirement/name "ATG001"] ;; Find a requirement such that the name == "ATG001"
        [?child :requirement/name ?child-name] ;; Find child relations such that the name is the name we are searching for.
        [?requirement :requirement/children ?child]] ;; Assert that the children we are looking for are the children of the previously selected requirement.
      db))
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
