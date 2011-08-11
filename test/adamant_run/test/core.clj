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
                     :tries 2 :timeout 10)))

  (is (= "<!DOCTYPE html "
         (adamant-run get-15-webpage-chars []
                      :tries 2 :timeout 10000))
      "sleep but wait long enough"))


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

(defn return-a-fn []
  #(str "I am a fn"))

(deftest return-function
  (is (thrown? AssertionError 
               (adamant-run return-a-fn))
      "function returns a function leads to AssertionError"))
