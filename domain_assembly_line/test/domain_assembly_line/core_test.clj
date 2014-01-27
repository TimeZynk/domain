(ns domain-assembly-line.core-test
  (:require [clojure.test :refer :all]
            [com.timezynk.domain.assembly-line :as line]))

"
future.assembly-line
--------------------
The AssemblyLine replaces the ideas of recipes. The AssemblyLine was designed to
model CRUD operations, which typically consists of several steps that differs a
bit between different domain types, but still looks very similar for the most part.


*** Stations

The AssemblyLine consists of stations with one or several functions. They are defined
as a hashmap. Every entry is a station. The key is the name and the value
is the functions.

The functions have two parameters. The first is the environment, which is a value
used as a common ground through the process. The second one is the value >>in production<<.
This value is sent through the functions of the stations and the result of the last
function in the line will be the result of the whole line when derefed.

The stations is defined via a vector. Every other value is the name of a station
followed with its function or functions.

The environment – which is optional by the way – is added as a named parameter.
"

(def process-number (line/assembly-line [:process [*, +]]
                                        :environment 10))

(deftest create-assembly-line
  (is (satisfies? line/AssemblyLineExecute process-number))
  (is (= 10 (:environment process-number))))


"
*** Prepare, Execute and Deref

So far we have not executed the Assembly Lines. Before we do that we have to prepare
the assembly line. This is done via the prepare function. It takes a single argument.
This is the initial in production value. Then we can execute the assembly line and
process the in production value. The most simple way to do this is to deref the
assembly line. It will then execute from the start to the end and finally produce a
result value.
"

(deftest execute-assembly-line
  (is (= 430 @(line/prepare process-number 42)))
  (is (= 100 @(line/prepare process-number 9))))

"
The stations can be replaced by new stations, or you can add new stations before or after
an existing station. This is done via the function add-stations, with the signature
add-stations [assembly-line placement target-station new-stations]. >>placement<< should
have the value :replace, :after or :before. >>target-station<< is the name of the station
refered to. >>new-stations<< is a vector defining the new stations in the same format as the
stations was defined in when the line was created.

This functionality makes the assembly lines very flexible and adaptable.
"

(def process-number-2
  (-> process-number
      (line/add-stations :before :process
                         [:validate (fn [env n]
                                      (if (< env n)
                                        (throw (Exception. "Env should not be smaller than n"))
                                        n))])))

(deftest add-validation-station
  (is (= 100 @(line/prepare process-number-2 9)))
  (is (thrown? Exception @(line/prepare process-number-2 42))))


"
*** Wrapper Function

If there is need to, you can add a >>wrapper function<<. It is a function that wraps each
function in the line. With use of this, you can prepare the value in production for each step.
For example you can check if the value is a sequence or not and either call the function directly
with the in production value as an argument, or you could call it via map.

The wrapper function is added as a named parameter when the assembly line is created.
"

(defn wrapper-f [f env in-prod]
  (if (sequential? in-prod)
    (map (partial f env) in-prod)
    (f env in-prod)))

;; This assembly line can handle both numbers and sequences of numbers
(def process-number-3 (line/assembly-line [:process [*, +]]
                                          :environment 10
                                          :wrapper-f wrapper-f))

(deftest sequence-input-with-wrapper
  (is (= [20,30,40,50] @(line/prepare process-number-3 [1,2,3,4]))))





"
There is also a possibility to execute an assembly line explicitly via the execute!
function. It takes a station name as an optional argument. If a station name is given
the assembly line will walk through all stations up to the named station and halt.
If you later on deref or call execute on the paused assembly line, it will continue
from where it was.
"

(def process-number-2-validated
  (-> process-number-2
      (line/prepare 9)
      (line/execute! :process)))

(deftest paused-assembly-line
  (is (= :process (:at process-number-2-validated))))

"
process-number-4-validated is now defined as an assembly-line, paused and ready to
continue with the :process station.

Now the assembly line is paused and ready to run the last station, :present.
From here on, you could call execute! with no argument to finish the process, or
just deref it if you want to get the final value. Another possiblity is to add additional
stations if you want to further process the in production value.
"

(deftest deref-paused-assembly-line
  (is (= 100 @process-number-2-validated)))

"
*** Async

The assembly lines are asynchronous in nature."

(deftest test-long-running-assembly-lines
  (let [now (System/currentTimeMillis)
        line-a (-> process-number
                   (line/add-stations :before :process [:delay (fn [_ n]
                                                            (Thread/sleep 500)
                                                            n)])
                   (line/prepare 5)
                   (line/execute!))
        line-b (-> process-number
                   (line/add-stations :before :process [:delay (fn [_ n]
                                                            (Thread/sleep 250)
                                                            n)])
                   (line/prepare 9)
                   (line/execute!))]
    ; The lines are executing in the background
    (is (not (realized? (:in-production line-a))))
    (is (not (realized? (:in-production line-b))))
    ; Dereferencing waits for the value to become available
    (is (= 160 (+ @line-a @line-b)))
    (is (< 500 (- (System/currentTimeMillis) now)))
    (is (realized? (:in-production line-a)))
    (is (realized? (:in-production line-b)))))
