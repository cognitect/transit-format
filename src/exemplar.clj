;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns transit.exemplar
  "Generate a set of increasingly complex values and use transit to
marshal them to files for edn, json, json-verbose, and msgpack."
  (:require [cognitect.transit :as t]
            [clojure.java.io :as io])
  (:import [java.net URI]
           [java.io File FileOutputStream ByteArrayInputStream
                    ByteArrayOutputStream OutputStreamWriter]))

(defn range-centered-on
  ([n] (range-centered-on n 5))
  ([n m] (mapv
          #(if (<= % Long/MAX_VALUE) (long %) %)
          (range (- n m) (+ n m 1)))))

(defn vector-of-keywords
  [n m]
  "Return a m length vector consisting of cycles of n keywords"
  (mapv #(keyword (format "key%04d" %)) (take m (cycle (range n)))))

(defn map-of-size [n]
  (let [nums (range 0 n)]
    (apply
      sorted-map
      (interleave (map #(keyword (format "key%04d" %)) nums) nums))))

(def map-simple {:a 1 :b 2 :c 3})
(def map-mixed {:a 1 :b "a string" :c true})
(def map-nested {:simple map-simple, :mixed map-mixed})

(def vector-simple  [1 2 3])
(def vector-mixed  [0 1 2.0 true false "five" :six 'seven "~eight" nil])
(def vector-nested [vector-simple vector-mixed])

(def small-strings  ["" "a" "ab" "abc" "abcd" "abcde" "abcdef"])

(def powers-two
  [1 2 4 8 16 32 64 128 256 512 1024 2048 4096 8192 16384
   32768 65536 131072 262144 524288 1048576 2097152 4194304
   8388608 16777216 33554432 67108864 134217728 268435456
   536870912 1073741824 2147483648 4294967296 8589934592
   17179869184 34359738368 68719476736 137438953472
   274877906944 549755813888 1099511627776 2199023255552
   4398046511104 8796093022208 17592186044416 35184372088832
   70368744177664 140737488355328 281474976710656
   562949953421312 1125899906842624 2251799813685248
   4503599627370496 9007199254740992 18014398509481984
   36028797018963968 72057594037927936 144115188075855872
   288230376151711744 576460752303423488 1152921504606846976
   2305843009213693952 4611686018427387904 9223372036854775808
   18446744073709551616 36893488147419103232])

(def interesting-ints
  (vec (apply concat (map #(range-centered-on % 2) powers-two))))

(def uuids [#uuid "5a2cbea3-e8c6-428b-b525-21239370dd55"
            #uuid "d1dc64fa-da79-444b-9fa4-d4412f427289"
            #uuid "501a978e-3a3e-4060-b3be-1cf2bd4b1a38"
            #uuid "b3ba141a-a776-48e4-9fae-a28ea8571f58"])

(def uris [(URI. "http://example.com")
           (URI. "ftp://example.com")
           (URI. "file:///path/to/file.txt")
           (URI. "http://www.詹姆斯.com/")])

(def symbols ['a 'ab 'abc 'abcd 'abcde 'a1 'b2 'c3 'a_b])

(defn write-description [file-name description vals]
  (println "##" description)
  (println "* Files:"
           (str file-name ".edn")
           (str file-name ".json")
           (str file-name ".verbose.json")
           (str file-name ".mp"))
  (println "* Value (EDN)")
  (println)
  (doseq [item vals] (println "    " (pr-str item)))
  (println))

(defn write-transit [dir file-name & vals]
  (doseq [format [{:type :json, :suffix ".json"}
                  {:type :json-verbose, :suffix ".verbose.json"}
                  {:type :msgpack :suffix ".mp"}]]
    (with-open [os (io/output-stream (str dir "/" file-name (:suffix format)))]
      (let [jsw (t/writer os (:type format))]
        (doseq [item vals] (t/write jsw item))))))

(defn write-exemplar [dir file-name description & vals]
  (write-description file-name description vals)
  (with-open [w (io/writer (str dir "/" file-name ".edn"))]
    (binding [*out* w] (apply pr vals)))
  (apply write-transit dir file-name vals))

(defn write-exemplars [dir]
  (binding [*out* (io/writer (str dir "/README.md"))]
    (println "# Example transit files.\n\n")
    (println "There are four files for each value: An EDN file and three transit files,")
    (println "one encoded in JSON, one in the verbose version of JSON and one in MessagePack\n\n")
    (println "Note: The example transit files in this directory are *generated*.")
    (println "See https://github.com/cognitect/transit-clj/blob/master/test/exemplar.clj\n\n")

    (write-exemplar dir "nil" "The nil/null/ain't there value" nil)
    (write-exemplar dir "true" "True" true)
    (write-exemplar dir "false" "False" false)
    (write-exemplar dir "zero" "Zero (integer)" 0)
    (write-exemplar dir "one" "One (integer)" 1)
    (write-exemplar dir "one_string" "A single string" "hello")
    (write-exemplar dir "one_keyword" "A single keyword" :hello)
    (write-exemplar dir "one_symbol" "A single symbol" 'hello)
    (write-exemplar dir "one_date" "A single date" (java.util.Date. 946728000000))

    (write-exemplar dir "vector_simple" "A simple vector" vector-simple)
    (write-exemplar dir "vector_empty" "An empty vector" [])
    (write-exemplar dir "vector_mixed" "A ten element vector with mixed values" vector-mixed)
    (write-exemplar dir "vector_nested" "Two vectors nested inside of an outter vector" vector-nested)

    (write-exemplar dir "small_strings" "A vector of small strings" small-strings)

    (write-exemplar dir "strings_tilde" "A vector of strings starting with ~" (mapv #(str "~" %) small-strings))

    (write-exemplar dir "strings_hash" "A vector of strings starting with #" (mapv #(str "#" %) small-strings))

    (write-exemplar dir "strings_hat" "A vector of strings starting with ^" (mapv #(str "^" %) small-strings))

    (write-exemplar dir "small_ints" "A vector of eleven small integers" (range-centered-on 0))

    (write-exemplar dir "ints", "vector of ints" (vec (range 128)))

    (write-exemplar
      dir
      "ints_interesting"
      "A vector of possibly interesting positive integers"
      interesting-ints)

    (write-exemplar
      dir
      "ints_interesting_neg"
      "A vector of possibly interesting negative integers"
      (mapv #(* -1 %) interesting-ints))

    (write-exemplar
      dir
      "doubles_small"
      "A vector of eleven doubles from -5.0 to 5.0"
      (mapv #(double %) (range-centered-on 0)))

    (write-exemplar
      dir
      "doubles_interesting"
      "A vector of interesting doubles"
      [-3.14159 3.14159 4E11 2.998E8 6.626E-34])

    (write-exemplar dir "one_uuid" "A single UUID" (first uuids))

    (write-exemplar
      dir
      "uuids"
      "A vector of uuids"
      uuids)


    (write-exemplar dir "one_uri" "A single URI" (first uris))

    (write-exemplar dir "uris" "A vector of URIs" uris)

    (def dates (mapv #(java.util.Date. %) [-6106017600000 0 946728000000 1396909037000]))

    (write-exemplar
      dir
      "dates_interesting"
      "A vector of interesting dates: 1776-07-04, 1970-01-01, 2000-01-01, 2014-04-07"
      dates)

    (write-exemplar dir "symbols" "A vector of symbols" symbols)
    (write-exemplar dir "keywords" "A vector of keywords" (mapv keyword symbols))

    (write-exemplar dir "list_simple" "A simple list" (apply list vector-simple))
    (write-exemplar dir "list_empty" "An empty list" '())
    (write-exemplar dir "list_mixed" "A ten element list with mixed values" (apply list vector-mixed))
    (write-exemplar dir "list_nested" "Two lists nested inside an outter list"
      (list (apply list vector-simple) (apply list vector-mixed)))

    (write-exemplar dir "set_simple" "A simple set" (set vector-simple))
    (write-exemplar dir "set_empty" "An empty set" #{})
    (write-exemplar dir "set_mixed" "A ten element set with mixed values" (set vector-mixed))
    (write-exemplar dir "set_nested" "Two sets nested inside an outter set"
      (set [(set vector-simple) (set vector-mixed)]))

    (write-exemplar dir "map_simple" "A simple map" map-simple)
    (write-exemplar dir "map_mixed" "A mixed map" map-mixed)
    (write-exemplar dir "map_nested" "A nested map" map-nested)

    (write-exemplar dir "map_string_keys" "A map with string keys" {"first" 1, "second" 2, "third" 3})

    (write-exemplar dir "map_numeric_keys" "A map with numeric keys" {1 "one", 2 "two"})

    (write-exemplar dir "map_vector_keys" "A map with vector keys" {[1 1] "one", [2 2] "two"})

    (write-exemplar dir "map_10_items" "10 item map"  (map-of-size 10))

    (doseq [i [10 1935 1936 1937]]
      (write-exemplar
        dir
        (str "map_" i "_nested")
        (str "Map of two nested " i " item maps")
        {:f (map-of-size i) :s (map-of-size i)}))

    (write-exemplar
      dir
      "maps_two_char_sym_keys"
      "Vector of maps with identical two char symbol keys"
      [{:aa 1 :bb 2} {:aa 3 :bb 4} {:aa 5 :bb 6}])

    (write-exemplar
      dir
      "maps_three_char_sym_keys"
      "Vector of maps with identical three char symbol keys"
      [{:aaa 1 :bbb 2} {:aaa 3 :bbb 4} {:aaa 5 :bbb 6}])

    (write-exemplar
      dir
      "maps_four_char_sym_keys"
      "Vector of maps with identical four char symbol keys"
      [{:aaaa 1 :bbbb 2} {:aaaa 3 :bbbb 4} {:aaaa 5 :bbbb 6}])

    (write-exemplar
      dir
      "maps_two_char_string_keys"
      "Vector of maps with identical two char string keys"
      [{"aa" 1 "bb" 2} {"aa" 3 "bb" 4} {"aa" 5 "bb" 6}])

    (write-exemplar
      dir
      "maps_three_char_string_keys"
      "Vector of maps with identical three char string keys"
      [{"aaa" 1 "bbb" 2} {"aaa" 3 "bbb" 4} {"aaa" 5 "bbb" 6}])

    (write-exemplar
      dir
      "maps_four_char_string_keys"
      "Vector of maps with identical four char string keys"
      [{"aaaa" 1 "bbbb" 2} {"aaaa" 3 "bbbb" 4} {"aaaa" 5 "bbbb" 6}])

    (write-exemplar
      dir
      "maps_unrecognized_keys"
      "Vector of maps with keys with unrecognized encodings"
      [(t/tagged-value "abcde" :anything)
       (t/tagged-value "fghij" :anything-else)])

    (write-exemplar
      dir
      "map_unrecognized_vals"
      "Map with vals with unrecognized encodings"
      {:key "~Unrecognized"})

    (write-exemplar
      dir
      "vector_1935_keywords_repeated_twice"
      "Vector of 1935 keywords, repeated twice"
      (vector-of-keywords 1935 3870))

    (write-exemplar
      dir
      "vector_1936_keywords_repeated_twice"
      "Vector of 1936 keywords, repeated twice"
      (vector-of-keywords 1936 3872))

    (write-exemplar
      dir
      "vector_1937_keywords_repeated_twice"
      "Vector of 1937 keywords, repeated twice"
      (vector-of-keywords 1937 3874))

    (write-exemplar
      dir
      "vector_unrecognized_vals"
      "Vector with vals with unrecognized encodings"
      ["~Unrecognized"])

    (write-exemplar
     dir
     "vector_special_numbers"
     "Vector with special numbers"
     [java.lang.Double/NaN
      java.lang.Double/POSITIVE_INFINITY
      java.lang.Double/NEGATIVE_INFINITY])))

(defn -main [& args]
  (write-exemplars (or (first args) "./simple-examples")))
