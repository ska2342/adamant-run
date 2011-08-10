(ns adamant-run.core
  (:import adamant_run.AdamantRetryException)
  (:import java.lang.RuntimeException))

;; handling decay of sleep time between successive tries
(defn- calc-next-interval [intervall decay]
  (cond
   ;; decay may be a user-supplied function accepting one arg (default identiy)
   (fn? decay) (decay intervall)

   ;; the interface also allows a whitelist of keywords representing some common
   ;; schemes
   (= decay :exponential) (* Math/E intervall)
   (= decay :double) (* 2 intervall)
   
   ;; FIXME should never come here.  Graceful or Exception?
   :else intervall))


   
;; create future object running the CALLEE
;; keep asking the future object whether it's done
;; if TIMEOUT exceeded or one of EXCEPTIONS catched
;; sleep for INTERVAL and return new closure with
;;    TRIES-LEFT dec'ed
;;    INTERVAL calculated by DECAY function
;;    
;;  
(defn arun-make-fn [callee timeout tries-left interval decay exceptions]
  ;;(println "make fn" [callee timeout tries-left interval decay exceptions])

  (fn []
    ;; testing whether we will try again one more time.
    (when-not (pos? tries-left)
      (throw (RuntimeException. "Adamant function: number of tries exceeded")))

    (try
      (let [fu (future (callee)) ;; future calculating result of function
            microsleep (long (/ timeout 10))]
        ;; loop a few times waiting for the future to finish
        (loop [timeout-iter 10]
          (Thread/sleep microsleep)

          (cond
            ;; this is the final returning of The Value (which must not be a fn,
            ;; otherwise trampoline won't work)
            (future-done? fu)
            (do
              (let [ret (deref fu)]
                (if (fn? ret)
                  (throw
                   (RuntimeException. "Adamant function: return value is fn"))
                  ret)))

           ;; we reached the maximum iterations waiting for the future
           (= 0 timeout-iter)
           ;; use our own exception to signal a retry
           (throw (AdamantRetryException. "Adamant function: timed out"))

           ;; nothing happened yet, so we try again to see whether the future
           ;; finished in the meantime
           :else
           (recur (dec timeout-iter)))))

      ;; now catch either the user-supplied exceptions or our own signal for a
      ;; retry
      (catch Throwable t
        ;; one of the expected exceptions
        (if (some #(or (isa? (type (.getCause t)) %)
                       (isa? (type t) %))
                  (conj exceptions AdamantRetryException))
          ;; this is a retry, so return a fn back to trampoline which will
          ;; repeat
          (arun-make-fn callee timeout 
                        (dec tries-left)
                        (calc-next-interval interval decay)
                        decay
                        exceptions)
          ;; this is another exception; pass it up the stack
          ;; use .getCause because we always get a concurrent Execution
          ;; exception
          (throw (.getCause t)))))))
  


(defn adamant-run
  "Run function F adamantly, finally returning its result.

This function can be used to easily retry a function which is expected
to fail with known exceptions or a timeout.

Options can be [defaults in square brackets]:
 :timeout    [1000] miliseconds to wait for the function to finish on
             another thread 
 :tries      [5] max number of tries
 :interval   [1000] wait interval between tries in miliseconds
 :decay      [identity] symbol or function for a more dynamic interval
             Must be either a function of one argument (current
             interval) returning the new interval or one of the
             allowed symbols
             :double
             :exponential
 :exceptions [[Exception]] vector of expected exeptions to catch.  All
             other exceptions will be passed through.

If the return value of the function can't be calculated a
RuntimeException will be raised.
"
  {:arglists '([f] [f [args-to-f] & options])}
  ([f]
     (adamant-run f []))

  ([f args-to-f & {:keys [timeout tries interval decay exceptions]
                   :or   {timeout    1000
                          tries      5
                          interval   1000
                          decay      identity
                          exceptions [Exception]}}]

     (trampoline
      (arun-make-fn #(apply f args-to-f)
                    timeout (dec tries) interval decay exceptions))))

