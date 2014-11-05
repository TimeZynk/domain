(ns domain.assembly-line-test
  (:require [clojure.test :refer :all]
            [com.timezynk.domain.assembly-line :as line]))

"
Assembly Lines
--------------
Assembly Line was designed to model CRUD operations,which typically consists of several
steps that differs a bit between different domain types, but still looks very similar
for the most part.


*** Stations

Assembly Lines consists of stations with one or several functions.

The functions have two parameters. The first is the environment, which is a value
used as a common ground through the process. The second one is the value >>in production<<.
This value is sent through the functions of the stations. The result of the last
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

So far we have not executed the Assembly Line. Before we do, we have to prepare
the assembly line. This is done via the prepare function. It takes a single argument,
which is the initial value in production. When prepared, we can execute the assembly line and
process this value.

The most simple way to do this is to deref the assembly line. It will then execute from the
start to the end and finally produce a result value.
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
process-number-2-validated is now defined as an assembly-line, paused and ready to
continue from the :process station.

Call execute! with no argument to finish the process, or
just deref it if you want to get the final value. Another possiblity is to add additional
stations if you want to further process the in production value.
"

(deftest deref-paused-assembly-line
  (is (= 100 @process-number-2-validated)))
