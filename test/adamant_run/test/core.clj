(ns adamant-run.test.core
  (:use [adamant-run.core])
  (:use [clojure.test])
  (:require [clojure.java.io :as io])
  (:import java.net.URL
           [java.io FileReader File FileNotFoundException]))


;;; basic tests
(deftest no-options-all-ok
  (is (= "OK" (adamant-run (fn [] "OK"))) "Just constant string")
  (is (= 23   (adamant-run #(+ 20 3)))    "Anon fn with addition")
  (is (= 3    (adamant-run + [1 2]))      "+ with additional args"))


;;; testing timeout handling
(defn test-timeout [millis]
  (Thread/sleep (* 2 millis))
  millis)

(defn get-15-webpage-chars []
  (with-open [s (io/input-stream "http://www.clojure-buch.de/")]
    (apply str (map char (take 15 (repeatedly #(.read s)))))))

(deftest timeout
  (is (thrown-with-msg?
        RuntimeException #"tries exceeded"
        (adamant-run test-timeout [100]
                     :tries 2 :timeout 10))
      "sleep and expect timeout")

  (is (= 100 (adamant-run test-timeout [100]
                          :tries 2 :timeout 500))
      "sleep but wait long enough")

  (is (thrown-with-msg?
        RuntimeException #"tries exceeded"
        (adamant-run get-15-webpage-chars []
                     :tries 1 :timeout 1))
      "get a webpage too fast")

  ;; this is probably a bad idea since the test relies on a working internet
  ;; connection 
  ;; (is (= "<!DOCTYPE html "
  ;;        (adamant-run get-15-webpage-chars []
  ;;                     :tries 2 :timeout 10000))
  ;;     "sleep but wait long enough")
  )


;;; testing the exception handling
(defn test-arith-exception []
  (throw (new ArithmeticException "Just a test")))

(deftest exception-catching
  (is (thrown-with-msg?
        RuntimeException #"tries exceeded"
        (adamant-run test-arith-exception []
                     :exceptions [ArithmeticException]))
      "catching explicit ArithExcept")

  (is (thrown-with-msg?
        RuntimeException #"tries exceeded"
        (adamant-run #(FileReader. (File. "./file/not/found"))
                     ))
      "catched by implicit Exception")

  (is (thrown-with-msg?
        RuntimeException #"tries exceeded"
        (adamant-run #(FileReader. (File. "./file/not/found"))
         []
         :exceptions [FileNotFoundException]))
      "catching FileNotFound")

  (is (thrown?
       FileNotFoundException
       (adamant-run #(FileReader. (File. "./file/not/found"))
        []
        :exceptions [ArithmeticException]))
      "trying to catch ArithEx but get concurrent.ExecEx"))

;; must not return a function
(defn return-a-fn []
  #(str "I am a fn"))

(deftest return-function
  (is (thrown? AssertionError 
               (adamant-run return-a-fn))
      "function returns a function leads to AssertionError"))

;; testing whether futures are canceled or performed for side effects 
(def side-effect (atom 0))
(defn slow-side-effecter [slp]
  (Thread/sleep slp)
  (swap! side-effect inc))

(deftest side-effects
  (is (thrown-with-msg?
        RuntimeException #"tries exceeded"
        (adamant-run slow-side-effecter [100] :timeout 10))
      "calling too slow, must be canceled")
  (is (= 0 (deref side-effect))
      "testing no side effect occured")
  (swap! side-effect (constantly 0))
  (is (= 1 (adamant-run slow-side-effecter [10] :timeout 100))
      "calling fast enough function, must have side effect")
  (is (=  (deref side-effect))
      "testing side effect occured"))
