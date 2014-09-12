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

(ns transit.corner-cases)

(def forms
  [nil
   true
   false
   :a
   :foo
   'f
   'foo
   (java.util.Date.)
   1/3
   \t
   "f"
   "foo"
   "~foo"
   []
   '()
   #{}
   [1 24 3]
   `(7 23 5)
   {:foo :bar}
   #{:a :b :c}
   #{true false}
   0
   42
   [9223372036854775807N 9223372036854775808N 9223372036854775809N]
   8987676543234565432178765987645654323456554331234566789
   {false nil}
   {true nil}
   {false nil true nil}
   {"a" false}
   {"a" true}
   [\"]
   {\[ 1}
   {1 \[}
   {\] 1}
   {1 \]}
   [\{ 1]
   [\[]
   {\{ 1}
   {1 \{}
   [\` \~ \^ \#]
   #uuid "2f9e540c-0591-eff5-4e77-267b2cb3951f"
   {}
   {"~#set" [1 2 3]}
   {1/2 2/5}
   "`~hello"
   {100
    1/2
    7924023966712353515692932N
    1/3}
   "^ foo"
   {10/11 :foobar 10/13 :foobar}
   ])

(def transit-json
  ["{\"~#point\":[1,2]}"
   "{\"foo\":\"~xfoo\"}"
   "{\"~/t\":null}"
   "{\"~/f\":null}"
   "{\"~#'\":\"~f-1.1E-1\"}"
   "{\"~#'\":\"~f-1.10E-1\"}"
   "[\"~#set\",[[\"~#ratio\",[\"~n4953778853208128465\",\"~n636801457410081246\"]],[\"^0\",[\"~n-8516423834113052903\",\"~n5889347882583416451\"]]]]"
   ])
