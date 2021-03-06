(ns relational-mapper-test
  (:require [clojure.test :refer :all]
            [relational-mapper :refer :all]
            [relational-mapper.test-utils.migrate :refer [db-migrate]]
            [relational-mapper.test-utils.seed :refer [db-seed]]))

(def associations {:users {:posts :has-many
                           :attachments [:through :posts :has-many]
                           :files [:through :posts :has-many]}
                   :posts {:users :belongs-to
                           :attachments :has-many
                           :files [:through :attachments :has-many]}
                   :attachments {:users [:through :posts :belongs-to]
                                 :posts :belongs-to
                                 :files :has-one}
                   :files {:attachments :belongs-to}})


(def fields {:users {:first_name {:type :string}
                     :last_name {:type :string}}
             :posts {:title {:type :string}
                     :body {:type :text}
                     :users_id {:type :integer}}
             :attachments {:name {:type :string}
                           :posts_id {:type :integer}}
             :files {:name {:type :string}
                     :attachments_id {:type :integer}}})

(def db-config
  {:classname "org.postgresql.Driver"
   :subprotocol "postgresql"
   :subname (str "//" "localhost" ":" 5432 "/" "relational_mapper_test")
   :user "postgres"
   :password "postgres"})

(def db-initial-state
  {:config db-config
   :db-created false
   :db-migrated false
   :db-seeded false
   :associations associations
   :fields fields})

(defn create-database [db-state]
  "I assume the database is already created:
   In psql run:
   CREATE ROLE postgres LOGIN;
   then
   [backslash]password postgres
   and
   CREATE database relational_mapper_test"
  (assoc db-state :db-created true))

(defn migrate-database [db-state]
  (db-migrate db-state)
  (assoc db-state :db-migrated true))

(defn seed-database [db-state]
  "TODO: add some initial data"
  (db-seed db-state)
  (assoc db-state :db-seeded true))

(defn setup-database [db-state]
  (-> db-state
      create-database
      migrate-database
      seed-database))

(def db-state (setup-database db-initial-state))

(deftest find-all-with-has-many-association
  (let [response (find-all db-state :users #{:posts} [[:= :users.first_name "John"]])]
        (testing "returns record with associated records as an array of hashes"
          (and (is (= (-> response first :first_name) "John"))
               (is (= (-> response first :posts first :title) "Post 1"))
               (is (= (-> response first :posts second :title) "Post 2"))))))

(deftest find-all-with-belongs-to-association
  (let [response (find-all db-state :posts #{:users} [[:= :posts.title "Post 1"]])]
        (testing "returns record with associated record as a hash"
          (and (is (= (-> response first :title) "Post 1"))
               (is (= (-> response first :users :first_name) "John"))))))

(deftest find-all-with-has-many-through-association
  (let [response (find-all db-state :users #{:attachments} [[:= :users.first_name "John"]])]
        (testing "returns record with associated record as an array of hashes"
          (and (is (= (-> response first :first_name) "John"))
               (is (= (-> response first :attachments first :name) "Attachment 1 (Post 1, John)"))
               (is (= (count (-> response first :attachments)) 1))))
        (testing "includes intermediate association record as an array of hashes"
          (is (= (-> response first :posts first :title) "Post 1")))))

(deftest find-all-with-belongs-to-through-association
  (let [response (find-all db-state :attachments #{:users} [[:= :attachments.name "Attachment 1 (Post 1, John)"]])]
        (testing "returns record with associated record as a hash"
          (and (is (= (-> response first :name) "Attachment 1 (Post 1, John)"))
               (is (= (-> response first :users :first_name) "John"))))
        (testing "includes intermediate association record as a hash"
          (is (= (-> response first :posts :title) "Post 1")))))

(deftest find-all-with-has-one-association
  (let [response (find-all db-state :attachments #{:files} [[:= :attachments.name "Attachment 1 (Post 1, John)"]])]
        (testing "returns record with associated record as a hash"
          (and (is (= (-> response first :name) "Attachment 1 (Post 1, John)"))
               (is (= (-> response first :files :name) "File 1 (Attachment 1)"))))))

(deftest find-all-with-embedded-through-association
  (let [response (find-all db-state :users #{:files} [[:= :users.first_name "John"]])]
        (testing "returns record with associated record as an array of hashes"
          (and (is (= (-> response first :first_name) "John"))
               (is (= (-> response first :files first :name) "File 1 (Attachment 1)"))
               (is (= (count (-> response first :attachments)) 1))))
        (testing "includes intermediate association record as an array of hashes"
          (and (is (= (-> response first :posts first :title) "Post 1"))
               (is (= (-> response first :attachments first :name) "Attachment 1 (Post 1, John)"))))))
