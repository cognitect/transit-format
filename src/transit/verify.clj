;; Copyright 2014 Rich Hickey. All Rights Reserved.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;      http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS-IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns transit.verify
  "Provides tools for testing all transit implementations at
  once. Tests each implementaion with several sources of data:
  exemplar files, problem EDN data, problem transit data and generated
  data. In addition, it can capture comparative timing results.

  From the REPL, the main entry point is `verify-all` which takes an
  options map as an argument. With an empty map it will test each
  encoding for all implementations located in sibling project
  directories which have a `bin/roundtrip` script. Options can be used
  to control which project is tested, which encoding to test, turn on
  generative testing and collect timing information."
  (:require [clojure.java.io :as io]
            [clojure.walk :as walk]
            [cognitect.transit :as t]
            [transit.generators :as gen]
            [transit.corner-cases :as cc]
            [clojure.pprint :as pp])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream
            BufferedInputStream BufferedOutputStream FileInputStream]
           [org.apache.commons.codec.binary Hex]
           [java.util Calendar TimeZone]))

(def ^:dynamic *style* false)

(def styles {:reset "[0m"
             :red "[31m"
             :green "[32m"
             :bright "[1m"})

(defn with-style [style & strs]
  (let [s (apply str (interpose " " strs))]
    (if (and *style* (not= style :none))
      (str \u001b (style styles) s \u001b (:reset styles))
      s)))

(def TIMEOUT
  "Timeout for roundtrip requests to native implementation"
  20000)

(defn read-bytes
  "Read the contents of the passed file into a byte array and return
  the byte array."
  [file]
  (assert (.exists file) "file must exist")
  (assert (.isFile file) "file must actually be a file")
  (let [size (.length file)
        bytes (make-array Byte/TYPE size)
        in (BufferedInputStream. (FileInputStream. file))]
    (loop [n (.read in bytes 0 size)
           total 0]
      (if (or (= n -1) (= (+ n total) size))
        bytes
        (let [offset (+ n total)
              size (- size offset)]
          (recur (.read in bytes offset size) offset))))))

(defn write-transit
  "Given an object and an encoding, return a byte array containing the
  encoded value of the object."
  [o encoding]
  (let [out (ByteArrayOutputStream.)
        w (t/writer out encoding)]
    (t/write w o)
    (.toByteArray out)))

(defn read-transit
  "Given a byte array containing an encoded value and the encoding used,
  return the decoded object."
  [bytes encoding]
  (try
    (let [in (ByteArrayInputStream. bytes)
          r (t/reader in encoding)]
      (t/read r))
    (catch Throwable e
      (println ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>" encoding)
      (println (.getMessage e))
      (.printStackTrace e)
      (println (String. bytes))
      (println ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
      ::read-error)))

(defn start-process
  "Given a command and enconding, start a process which can roundtrip
  transit data. The command is the path to any program which will
  start a roundtrip process. The enconding must be either `:json`,
  `:json-verbose` or `:msgpack`. Returns a map (process map) with keys
  `:out` the output stream which can be used to send data to the
  process, `:p` the process and `:reader` a transit reader."
  [command encoding]
  (let [p (.start (ProcessBuilder. [command (name encoding)]))
        out (BufferedOutputStream. (.getOutputStream p))
        in (BufferedInputStream. (.getInputStream p))]
    ;; for JSON, t/reader does not return until data starts to flow
    ;; over input stream
    {:out out :p p :reader (future (t/reader in encoding))}))

(defn stop-process
  "Given a process, stop the process started by
  `start-process`. Sends Ctrl-C (SIGINT) to the process before
  attempting to destroy the process."
  [proc]
  (try
    (.destroy (:p proc))
    (catch Throwable e
      (println "WARNING! Exception while stopping process.")
      (println (.toString e)))))

(defn write-to-stream
  "Given a process and a byte array of transit data, write this
  data to the output stream. Throws an exception of the output stream
  is closed."
  [proc transit-data]
  (try
    (.write (:out proc) transit-data 0 (count transit-data))
    (.flush (:out proc))
    (catch Throwable e
      (throw (ex-info "Disconnected" {:status :disconnected :cause e})))))

(defn read-response
  "Given a process and a timeout in milliseconds, attempt to read
  a transit value.  If the read times out, returns `::timeout`.`"
  [proc timeout-ms]
  (let [f (future (t/read @(:reader proc)))
        result (try
                 (deref f timeout-ms ::timeout)
                 (catch Throwable e
                   (throw (ex-info "Read error" {:status :read-error :cause e}))))]
    (if (= result ::timeout)
      (throw (ex-info "Response timeout" {:status :timeout}))
      result)))

(defn special-number? [node]
  (and (number? node)
       (or (.equals node Double/NaN)
           (.equals node Double/POSITIVE_INFINITY)
           (.equals node Double/NEGATIVE_INFINITY))))

(def special-number-symbols
  {:NaN (gensym)
   :Infinity (gensym)
   :-Infinity (gensym)})

(defn replace-special-number [number]
  (special-number-symbols
   (cond (.equals number Double/NaN) :Nan
         (.equals number Double/POSITIVE_INFINITY) :Infinity
         (.equals number Double/NEGATIVE_INFINITY) :-Infinity)))

(defn equalize [data]
  (walk/prewalk (fn [node]
                  (cond (and (number? node)
                             (not (ratio? node))
                             (not (special-number? node)))    (.stripTrailingZeros (bigdec node))
                        (special-number? node)                (replace-special-number node)
                        (instance? java.util.Map$Entry node)  node
                        (sequential? node)                    (seq node)
                        (instance? Character node)            (str node)
                        :else
                        node))
                data))

(defn roundtrip
  "Given a process and an input data map, roundtrip the transit data
  and return a map of test results."
  [proc {:keys [edn encoded encoding] :as data}]
  (try
    (let [start (System/nanoTime)]
      (write-to-stream proc encoded)
      (let [data-in (read-response proc TIMEOUT)
            end (System/nanoTime)
            data-out (read-transit encoded encoding)]
        {:data data
         :transit-string (String. encoded)
         :data-expected data-out
         :data-actual data-in
         :nano-time (- end start)
         :status (if (= (equalize data-out) (equalize data-in))
                   :success
                   :error)}))
    (catch Throwable e
      (if-let [ed (ex-data e)]
        (merge ed {:data data :message (.getMessage e)})
        {:status :exception
         :message "Generic Exception"
         :cause e
         :data data}))))

(defn test-each
  "Given a process and a collection of test inputs, roundtrip each
  input and return a sequence of results."
  [proc data]
  (loop [data data
         prev nil
         ret []]
    (if-let [input (first data)]
      (let [result (assoc (roundtrip proc input) :prev-data prev)]
        (if (contains? #{:success :error} (:status result))
          (recur (rest data) input (conj ret result))
          (conj ret result)))
      ret)))

(defn test-timing
  "Given a process and a collection of test inputs, record the time in
  milliseconds that it takes to roundtrip all of the inputs."
  [proc data]
  (dotimes [x 200]
    (mapv #(roundtrip proc %) data))
  (quot (reduce + (map :nano-time
                       (mapv #(roundtrip proc %) data)))
        1000000))

(defn edn->test-input [form encoding]
  (try
    (let [encoded (write-transit form encoding)]
      {:edn form
       :encoded encoded
       :hex (Hex/encodeHexString encoded)
       :encoding encoding})
    (catch Throwable e
      (println (with-style :red "Error! transit-clj Could not encode edn form"))
      (println (pr-str form))
      (.printStackTrace e))))

(defn transit->test-input [transit encoding]
  {:edn (read-transit transit encoding)
   :encoded transit
   :hex (Hex/encodeHexString transit)
   :encoding encoding})

(def extension {:json         ".json"
                :json-verbose ".verbose.json"
                :msgpack      ".mp"})

(defn exemplar-transit
  "Given an encoding, return a collection of test inputs in that
  encoding. The inputs are loaded from the exemplar files in the
  `transit` repository."
  [encoding]
  (map #(transit->test-input (read-bytes %) encoding)
       (filter #(and (.isFile %) (.endsWith (.getName %) (extension encoding)))
               (file-seq (io/file "../transit-format/examples/0.8/simple")))))

(defn filter-tests
  "Given a process, an encoding and options provided by the user,
  return a sequcnes of tests to run. Each test is represented as a map
  with `:path` and `:test` keys. The value at `:path` is the path into
  the results where the test results are to be stored. The value at
  `:test` is a no argument function which will run a single test."
  [proc encoding opts]
  (let [transit-exemplars (exemplar-transit encoding)]
    (filter #((:pred %) proc encoding opts)
            [{:pred (constantly true)
              :desc "exemplar file"
              :input transit-exemplars
              :test-name :exemplar-file
              :test #(test-each proc %)}
             {:pred (constantly true)
              :desc "EDN corner case"
              :input (mapv #(edn->test-input % encoding) cc/forms)
              :test-name :corner-case-edn
              :test #(test-each proc %)}
             {:pred (fn [_ e _] (contains? #{:json :json-verbose} e))
              :desc "JSON transit corner case"
              :input (mapv #(transit->test-input (.getBytes %) encoding) cc/transit-json)
              :test-name :corner-case-transit-json
              :test #(test-each proc %)}
             {:pred (fn [_ _ o] (:gen o))
              :desc "generated EDN"
              :input (mapv #(edn->test-input % encoding) (:generated-forms opts))
              :test-name :generated-edn
              :test #(test-each proc %)}
             {:pred (fn [_ _ o] (:time o))
              :desc "timing"
              :input transit-exemplars
              :test-name :time
              :test #(let [ms (test-timing proc %)]
                       {:ms ms
                        :count (count transit-exemplars)
                        :encoding encoding})}])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Running Tests

(defn verify-impl-encoding
  "Given a command string, an encoding and user provided options, run
  a collection of tests against a specific implementation of
  transit. The tests which are run are determined by the provided
  options and the encoding. The implementation which is tested is
  determined by the provided command string."
  [command encoding opts]
  (assert (contains? extension encoding)
          (str "encoding must be one of " (keys extension)))
  (let [proc (start-process command encoding)
        results {:command command
                 :encoding encoding
                 :tests []}]
    (try
      (let [tests (filter-tests proc encoding opts)
            results (reduce (fn [r {:keys [test-name test desc input]}]
                              (println (format "running \"%s\" test for %s encoding..."
                                               desc
                                               (name encoding)))
                              (update-in r [:tests] conj
                                         {:test-name test-name :result (test input)}))
                            results
                            tests)]
        (stop-process proc)
        results)
      (catch Throwable e
        (stop-process proc)
        (.printStackTrace e)))))

(declare report)

(defn- run-test
  "Given a project name, an encoding and user provided options, run
  tests against this project."
  [project encoding opts]
  (println (format "testing %s's %s encoding skills..."
                   project
                   (name encoding)))
  (let [command (str "../" project "/bin/roundtrip")]
    (report (-> (verify-impl-encoding command encoding opts)
                (assoc :project project)))))

(defn verify-encodings
  "Given a project name like 'transit-java', 'transit-clj' or
  'transit-ruby', and user provided options, run tests for each
  encoding specified in the options. Encoding can be either `:json`,
  `:json-verbose` or `:msgpack`."
  [project {:keys [enc] :as opts}]
  (doseq [e (if enc [enc] [:json :json-verbose :msgpack])]
    (run-test project e opts)))

(defn verify
  "Given user provided options, run all tests specified by the
  options. If options is an empty map then run all tests against all
  implementations for both encodings."
  [{:keys [impls] :as opts}]
  (let [root (io/file "../")
        testable-impls (keep #(let [script (io/file root (str % "/bin/roundtrip"))]
                                (when (.exists script) %))
                             (.list root))
        ;; Generate n random forms. Use the same set for all tests.
        forms (when-let [n (:gen opts)]
                (take n (repeatedly gen/ednable)))]
    (doseq [impl testable-impls]
      (when (or (not impls)
                (contains? impls impl))
        (verify-encodings impl (assoc opts :generated-forms forms))))))

(defn read-options
  "Given a sequence of strings which are the command line arguments,
  return an options map."
  [args]
  (reduce (fn [a [[k] v]]
            (case k
              "-impls" (assoc a :impls (set (mapv #(str "transit-" %) v)))
              "-enc" (assoc a :enc (keyword (first v)))
              "-gen" (assoc a :gen (Integer/valueOf (first v)))
              "-time" (assoc a :time true)
              a))
          {}
          (partition-all 2 (partition-by #(.startsWith % "-") args))))

(defn -main [& args]
  (binding [*style* true]
    (verify (read-options args))
    (shutdown-agents)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Reporting

(defn error-report [message errors]
  (doseq [error errors]
    (println (with-style :red message))
    (when (:cause error)
      (println (with-style :red "Cause: " (.toString (:cause error)))))
    (println (with-style :red "Error when sending:"))
    (println (pr-str (-> error :data :edn)))
    (println (with-style :red "Previous sent form was:"))
    (println (pr-str (-> error :prev-data :edn)))))

(defmulti print-report (fn [type results] type))

(defmethod print-report :timeout [_ results]
  (error-report "Response Timeout" results))

(defmethod print-report :disconnected [_ results ]
  (error-report "Disconnected" results))

(defmethod print-report :read-error [_ results]
  (error-report "Read Error" results))

(defn plural [things w]
  (if (= (count things) 1) w (str w "s")))

(defmethod print-report :success [_ results]
  (println (with-style :green (format "%s successful %s"
                                      (count results)
                                      (plural results "roundtrip")))))

(defmethod print-report :error [_ results]
  (println (with-style :red (format "%s roundtrip %s"
                                    (count results)
                                    (plural results "error"))))
  (doseq [error results]
    (println "Sent edn:    " (pr-str (-> error :data :edn)))
    (println "     bytes:  " (-> error :data :hex))
    (println "     string: " (pr-str (:transit-string error)))
    (println "Expected:    " (pr-str (:data-expected error)))
    (println (with-style :red "Actual:      " (pr-str (:data-actual error))))
    (println (with-style :red "--------------------"))))

(defmethod print-report :default [type results]
  (pp/pprint results))

(defn report [{:keys [project command encoding tests] :as results}]
  (println (with-style :bright "Project: " project "/" (name encoding)))
  (println "Command: " command)
  (doseq [{:keys [test-name result]} tests]
    (if (= test-name :time)
      (println (with-style :bright
                 (format "Time: %s ms to roundtrip %s %s exemplar files"
                         (:ms result)
                         (:count result)
                         (name (:encoding result)))))
      (do (println "Results for test:" (name test-name))
          (doseq [[type result-seq] (group-by :status result)]
            (print-report type result-seq))))))
