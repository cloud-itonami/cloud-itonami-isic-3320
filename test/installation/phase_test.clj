(ns installation.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:schedule-installation-operation`/`:flag-safety-concern`
  must NEVER be a member of any phase's `:auto` set. `:log-installation-
  record` and `:order-supplies` ARE auto-eligible at phase 3 -- see
  `installation.phase` ns docstring 'Actuation' section."
  (:require [clojure.test :refer [deftest is testing]]
            [installation.phase :as phase]))

(deftest schedule-installation-operation-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits an installation-operation schedule proposal"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :schedule-installation-operation))
          (str "phase " n " must not auto-commit :schedule-installation-operation")))))

(deftest flag-safety-concern-never-auto-at-any-phase
  (doseq [[n {:keys [auto]}] phase/phases]
    (is (not (contains? auto :flag-safety-concern))
        (str "phase " n " must not auto-commit :flag-safety-concern"))))

(deftest log-installation-record-and-order-supplies-are-auto-eligible-at-phase-3
  (is (contains? (:auto (get phase/phases 3)) :log-installation-record))
  (is (contains? (:auto (get phase/phases 3)) :order-supplies)))

(deftest write-ops-is-exactly-the-closed-four-op-allowlist
  (is (= #{:log-installation-record :schedule-installation-operation :flag-safety-concern :order-supplies}
         phase/write-ops)))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-1-only-allows-log-installation-record
  (is (= #{:log-installation-record} (:writes (get phase/phases 1)))))

(deftest phase-3-auto-set-is-exactly-log-installation-record-and-order-supplies
  (is (= #{:log-installation-record :order-supplies} (:auto (get phase/phases 3)))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :log-installation-record} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :schedule-installation-operation} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :flag-safety-concern} :commit)))))

(deftest gate-auto-commits-log-installation-record-when-clean-at-phase-3
  (is (= :commit (:disposition (phase/gate 3 {:op :log-installation-record} :commit)))))

(deftest gate-auto-commits-order-supplies-when-clean-at-phase-3
  (is (= :commit (:disposition (phase/gate 3 {:op :order-supplies} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 1 {:op :flag-safety-concern} :commit))))
  (is (= :hold (:disposition (phase/gate 0 {:op :log-installation-record} :commit)))))
