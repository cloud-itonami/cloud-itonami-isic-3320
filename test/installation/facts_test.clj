(ns installation.facts-test
  (:require [clojure.test :refer [deftest is]]
            [installation.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:installation-notification-provenance (facts/spec-basis "JPN"))))
  (is (= :quantitative (:threshold-model (facts/spec-basis "JPN"))))
  (is (= 30 (:notification-lead-days (facts/spec-basis "JPN")))))

(deftest usa-is-honestly-qualitative-not-fabricated
  (is (= :qualitative (:threshold-model (facts/spec-basis "USA"))))
  (is (nil? (:notification-lead-days (facts/spec-basis "USA")))))

(deftest deu-is-honestly-qualitative-not-fabricated
  (is (= :qualitative (:threshold-model (facts/spec-basis "DEU"))))
  (is (nil? (:notification-lead-days (facts/spec-basis "DEU")))))

(deftest gbr-is-honestly-qualitative-not-fabricated
  (is (some? (facts/spec-basis "GBR")))
  (is (= :qualitative (:threshold-model (facts/spec-basis "GBR"))))
  (is (nil? (:notification-lead-days (facts/spec-basis "GBR")))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "USA"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["JPN" "USA"] (:covered-jurisdictions report)))))

(deftest coverage-includes-gbr-once-registered
  (let [report (facts/coverage ["GBR" "ATL"])]
    (is (= 1 (:covered report)))
    (is (= ["GBR"] (:covered-jurisdictions report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))))

;; ----------------------------- notification-lead-insufficient? -----------------------------

(deftest jpn-lead-time-is-a-real-numeric-recheck
  (is (true? (facts/notification-lead-insufficient? "JPN" {:installation-notice-lead-days-actual 10})))
  (is (false? (facts/notification-lead-insufficient? "JPN" {:installation-notice-lead-days-actual 30})))
  (is (false? (facts/notification-lead-insufficient? "JPN" {:installation-notice-lead-days-actual 35}))))

(deftest usa-never-gets-a-fabricated-true-false
  (is (= :qualitative (facts/notification-lead-insufficient? "USA" {:installation-notice-lead-days-actual 100})))
  (is (= :qualitative (facts/notification-lead-insufficient? "USA" {:installation-notice-lead-days-actual 0}))))

(deftest deu-never-gets-a-fabricated-true-false
  (is (= :qualitative (facts/notification-lead-insufficient? "DEU" {:installation-notice-lead-days-actual 100})))
  (is (= :qualitative (facts/notification-lead-insufficient? "DEU" {:installation-notice-lead-days-actual 0}))))

(deftest gbr-never-gets-a-fabricated-true-false
  (is (= :qualitative (facts/notification-lead-insufficient? "GBR" {:installation-notice-lead-days-actual 100})))
  (is (= :qualitative (facts/notification-lead-insufficient? "GBR" {:installation-notice-lead-days-actual 0}))))

(deftest unknown-jurisdiction-returns-nil-not-a-guess
  (is (nil? (facts/notification-lead-insufficient? "ATL" {:installation-notice-lead-days-actual 100}))))

(deftest non-numeric-actual-never-fires-a-quantitative-hold
  (is (false? (facts/notification-lead-insufficient? "JPN" {:installation-notice-lead-days-actual nil}))))

;; ----------------------------- catalog citation honesty -----------------------------

(deftest jpn-cites-real-lift-plan-and-installation-notification-law
  (let [sb (facts/spec-basis "JPN")]
    (is (re-find #"クレーン等安全規則" (:lift-plan-basis sb)))
    (is (re-find #"laws\.e-gov\.go\.jp" (:lift-plan-provenance sb)))
    (is (re-find #"労働安全衛生法" (:installation-notification-basis sb)))
    (is (re-find #"第88条" (:installation-notification-basis sb)))
    (is (re-find #"laws\.e-gov\.go\.jp" (:installation-notification-provenance sb)))))

(deftest usa-cites-real-osha-crane-and-loto-law
  (let [sb (facts/spec-basis "USA")]
    (is (re-find #"1926" (:lift-plan-basis sb)))
    (is (re-find #"osha\.gov" (:lift-plan-provenance sb)))
    (is (re-find #"1910\.147" (:installation-notification-basis sb)))
    (is (re-find #"osha\.gov" (:installation-notification-provenance sb)))))

(deftest deu-cites-real-eu-directive-and-german-instrument
  (let [sb (facts/spec-basis "DEU")]
    (is (re-find #"2006/42" (:installation-notification-basis sb)))
    (is (re-find #"eur-lex\.europa\.eu" (:installation-notification-provenance sb)))
    (is (re-find #"BetrSichV|Betriebssicherheitsverordnung" (:lift-plan-basis sb)))))

(deftest gbr-cites-real-hse-loler-law
  (let [sb (facts/spec-basis "GBR")]
    (is (re-find #"LOLER|Lifting Operations and Lifting Equipment Regulations" (:lift-plan-basis sb)))
    (is (re-find #"regulation 8" (:lift-plan-basis sb)))
    (is (re-find #"legislation\.gov\.uk" (:lift-plan-provenance sb)))
    (is (re-find #"regulation 9" (:installation-notification-basis sb)))
    (is (re-find #"legislation\.gov\.uk" (:installation-notification-provenance sb)))
    (is (re-find #"Health and Safety Executive|HSE" (:owner-authority sb)))))

(deftest uncovered-jurisdiction-has-no-fabricated-catalog-entry
  (is (nil? (facts/spec-basis "ATL"))))
