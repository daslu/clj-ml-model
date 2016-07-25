Interface for Machine Learning Modeling, Testing and Training
=============================================================

This library provides generalized library to train, test and use machine
learning models.  Specifically it:
* Wraps [Weka](http://www.cs.waikato.ac.nz/ml/weka/).
* Automation of any combination of classifiers and features.
* Sort and prints results in many formats and levels of detail.
* Generate Excel spreadsheet files of multiple run results.
* Two pass cross validation.

Obtaining
---------
In your `project.clj` file, add:

[![Clojars Project](https://clojars.org/com.zensols.ml/model/latest-version.svg)](https://clojars.org/com.zensols.ml/model/)

Documentation
-------------
Additional [documentation](https://plandes.github.io/clj-ml-model/codox/index.html).

Example
-------
See the [example repo](https://github.com/plandes/clj-example-nlp-ml) that
illustrates how to use this library and contains the code from where these
examples originate.  It's highly recommended to clone it and follow along as
you peruse this README.

Usage
-----
To create, validate, test and utilize a model you must do the following:

1. [Create the Corpus](#create-the-corpus)
2. [Create Features](#create-features)
3. [Create the model configuration](#create-the-model-configuration)
4. [Create the model](#create-the-model)
5. [Evaluating the model](#evaluate-the-model)
6. [Using the model](#use-the-model)
7. [Testing the model](#test-the-model)

Note that this example (like `clj-ml-dataset`) uses natural language processing
but the library was written to be general purpose and other non-NLP projects
can use it.

### Create the Corpus
Before we can do anything, we need a annotated corpus since we'll be using
supervised learning methods.  To do that, use the
[machine learning dataset library](https://github.com/plandes/clj-ml-dataset)
to pre-parse all utterances in the annotated corpus (follow the readme and
create `zensols.example.anon-db` namesapce).  You'll also need to start a
Docker instance for the Elasticsearch server as detailed in the docs.

### Create Features
First we have to generate the features that will be used in our model and train
our classifier.  We'll generate our features from details that are parsed from
English utterances for our example so we'll use the
[NLP library](https://github.com/plandes/clj-nlp-parse) to parse and generate
those features from the pre-parsed utterances stored in Elasticsearch using the
`clj-ml-dataset` library:
```clojure
(ns zensols.example.sa-feature
  (:require [zensols.nlparse.parse :as p]
            [zensols.nlparse.feature :as fe]
            [zensols.example.anon-db :as adb]
            [zensols.model.execute-classifier :refer (with-model-conf)]))

(defn create-features
  ([panon]
   (create-features panon nil))
  ([panon context]
   (let [tokens (p/tokens panon)]
     (merge (fe/verb-features (->> panon :sents first))
            (fe/token-features panon tokens)
            (fe/pos-tag-features tokens)
            (fe/dictionary-features tokens)
            (fe/tree-features panon)
            (fe/srl-features tokens)))))

(defn create-feature-sets []
  (->> (adb/anons)
       (map #(merge {:sa (:class-label %)
                     :utterance (->> % :annotation :text)}
                    (create-features (:annotation %))))))

(defn feature-metas []
  (concat (fe/verb-feature-metas)
          (fe/token-feature-metas)
          (fe/pos-tag-feature-metas)
          (fe/dictionary-feature-metas)
          (fe/tree-feature-metas)
          (fe/srl-feature-metas)))

(defn- class-feature-meta []
  [:sa ["answer" "question" "expressive"]])
```

In this example we call `adb/anons` to return the parsed corpus data (see the
annotation library documentation for how to generate the corpus cache).

### Create the Model Configuration
Next we create the model configuration (not the model yet).  The configuration
gives the framework what needs to create the feature set to generate an
[weka.core.Instances](http://weka.sourceforge.net/doc.dev/weka/core/Instances.html) used
by Weka to create, test and utilize the model.

```clojure
(defn create-model-config []
  {:name "speech-act"
   :create-feature-sets-fn create-feature-sets
   :create-features-fn create-features
   :feature-metas-fn feature-metas
   :class-feature-meta-fn class-feature-meta
   :model-return-keys #{:label :distributions :features}})
```

The model configuration is a map that refers to functions we already created
and some other metadata.

### Create the Model
Next we define our features and classifiers.

After the namesapce declaration we define `feature-sets-set`, which is a
two level hierarchy of features that have the same names as those given in
the `feature-metas` [function](#create-features).  The levels are:

1. Feature metadata sets set: list of lists with each list is iterated on while
   cross-validating to find the feature set to fit the model.
2. Feature metadata set: the list of features used to create a model for the
   current feature metadata set iteration.

We create a `classifiers` binding to store what *genera* of classifiers we want
to use.  See the [classifiers dynamic binding](http://?????) for more
information.

```clojure
(ns zensols.example.sa-eval
  (:require [zensols.model.execute-classifier :refer (with-model-conf)]
            [zensols.model.eval-classifier :as ec])
  (:require [zensols.example.sa-feature :as sf]))

(defn feature-sets-set []
  {:set-1 '((token-count))
   :set-2 '((token-count
             pos-tag-ratio-verb
             pos-tag-ratio-adverb
             pos-tag-ratio-noun
             pos-tag-ratio-adjective))
   :set-3 '((token-count stopword-count)
            (token-count
             pos-tag-ratio-noun
             pos-tag-ratio-wh
             pos-first-tag
             stopword-count))})

(def classifiers [:zeror :fast])
```

Next we add an atom to store the `weka.core.Instances` object so we can speed
up our feature/classifier configuration without having to regenerate feature
sets for each model testing iteration.

```clojure
(def instances-inst (atom nil))
```

Finally we extend the model configuration with the `Instances` atom and our
feature metadata sets.

```clojure
(defn- create-model-config []
  (merge (sf/create-model-config)
         {:instances-inst instances-inst
          :feature-sets-set (feature-sets-set)}))
```

### Evaluating the Model
While this step isn't necessary, you'll want to do it to see how well the model
performs and optimize it by changing the feature set or swapping and/or
tweaking the classifier.

Technically speaking, the actual in memory model is not yet created, but we
have now set up everything the framework needs to use it.

#### Creating an ARFF File
Let's start by writing out an
[ARFF](http://www.cs.waikato.ac.nz/ml/weka/arff.html) file:
```clojure
(with-model-conf (create-model-config)
  (ec/write-arff))
```

#### Cross Validation
Note that we have we need to wrap everything in a `with-model-conf`, which is
the way the framework receives our model configuration.  In practice you'll
wrap more than just one statement and do several things in the lexical context
of a `with-model-conf`.

Now let's invoke a cross validation and get just an
[F-measure](https://en.wikipedia.org/wiki/F1_score) score:
```clojure
(with-model-conf (create-model-config)
  (ec/terse-results classifiers :set-3 :only-stats? true))
```
This performs a ten fold cross validation using the using two feature sets:

* `token-count` and `stopword-count`
* `token-count`, `pos-tag-ratio-noun`, `pos-tag-ratio-wh`, `pos-first-tag`,
  `stopword-count`

For both models it tests with the `zeror` and `fast` classifiers.  The first
`zeror` is a majority rule classifier usually used to generate a baseline to
gauge relative performance gains.  The `fast` classifier are a group of
classifiers that train fast.  See the
[classifiers dynamic binding](http://?????) for more information on classifier
genres.


#### Cross Validation Report
You might have a large dataset and choose to use classifiers that take a long
time to train.  This is all multiplied by feature metadata set cardinally,
which drastically compoundes the run time.  In these situations you might want
to leave it running for a while and generate a spreadsheet report as output.
This report contains the feature set, classifiers used, and performance
metrics.  ```clojure (with-model-conf (create-model-config) (ec/eval-and-write
classifiers :huge-meta-set)) ```

### Persist/Save the Model
Once you're happy with the performance of your model you can save it and use it
in the same or different JVM instance.
```clojure
(with-model-conf (create-model-config)
  (->> (ec/create-model classifiers :set-best)
       ec/train-model
       ec/write-model))
```

This creates a binary model file where you've configured the
[model output directory](????classifier/model-dir).  More information on how to
configure this is in the [example project repo](????).

The information encoded in this file includes:
* The trained classifier
* The features of the model
* Performance metrics like F-measure, recall, precision, predictions
* The context (TODO: refer from here)

### Use the Model
First let's create a namespace to work with our new model and a function to
create that model:
```clojure
(ns zensols.example.sa-model
  (:require [zensols.model.execute-classifier :as exc :refer (with-model-conf)]
            [zensols.nlparse.parse :as p])
  (:require [zensols.example.sa-feature :as sf]))

(def model-inst (atom nil))

(defn- model []
  (swap! model-inst
         #(or %
              (with-model-conf (sf/create-model-config)
                (exc/prime-model (exc/read-model))))))
```

Since the details of the previous model is encoded in binary you won't be able
to look at the file to make sense of it.  However, you can output the contents
of the model (to the REPL and a file respectively) including everything
mentioned in the [previous section](#persist-model):

```clojure
(exc/print-model-info (model))
(exc/dump-model-info (model))
```
which yields:
```make
instances-total: 382.0
instances-correct: 366.0
instances-incorrct: 16.0
name: speech-act
create-time: Mon Jul 25 12:19:24 CDT 2016
accuracy: 95.81151832460733
wprecision: 0.9585456987726836
wrecall: 0.9581151832460733
wfmeasure: 0.9580599320074707
features:
(:token-count :pos-tag-ratio-noun :pos-tag-ratio-wh :pos-first-tag :pos-last-tag :stopword-count)
classifier: ...
```

Finally, we can parse an utterance and use its features to classify our speech
act:
```clojure
(->> (p/parse "when are we getting there")
     (exc/classify (model))
     pprint)
```
which yeilds:
```clojure
{:features-set
 {:pos-last-tag "RB",
  ...
  :pos-tag-ratio-wh 0},
 :label "question",
 :distributions
 {"answer" 0.033959200182016515,
   "question" 0.9306213420827363,
   "expressive" 0.03541945773524721}}
```

This gives us all the results we asked for in the `:model-return-keys` of our
`create-model-config` function in our
[feature namespace](#create-the-model-configuration), which is:
* **:features** the single instance features given to the model for classification,
  which for us was generate by the `create-features` [function](#create-features).
* **:label** is the class label for our classification, which in the case is
  correct for the utterance *when are we getting there*.
* **:distributions** is the probability distribution over the class label,
  which in our case is pretty uneven suggesting a high degree of confidence

The features are availble since there are situations where you might want to do
something with a feature after classification.  More specifically you could
even generate features as data not used by the model like an ID for an NER tag.

The probability distribution could be handy for cases where you don't want the
first choice or would like to use the distribution itself as a feature in
another model or get an idea of specific classifications' performance.

Now we can create a client friendly (to our new library) function:
```clojure
(defn classify-utterance [utterance]
  (->> (p/parse utterance)
       (exc/classify (model) anon)
       :label))
```

### Test the Model

In our [example](#use-the-model) we performed with a weighted F-measure
of 0.96, which seems pretty unbelievable.  Another way to confirm we have a
good model is to divide the dataset into a training and test set.  For this
example, let's split it right down the middle and retrain:
```clojure
user=> (adb/divide-by-set 0.5)
user=> (reset! instance-inst nil) ; invalidate the instances cache
user=> (with-model-conf (create-model-config)
		 (->> (ec/create-model classifiers :set-best)
			  ec/train-model
			  ec/write-model))
user=> (reset! model-inst nil) ; invalidate the model
user=> (exc/print-model-info)
```
yeilds:
```make
instances-total: 239.0
instances-correct: 225.0
instances-incorrct: 14.0
name: speech-act
create-time: Mon Jul 25 12:51:18 CDT 2016
accuracy: 94.14225941422595
wprecision: 0.9413853837630445
wrecall: 0.9414225941422594
wfmeasure: 0.9413703416300443
```
which is still very good and still hard to believe how well it performs.
However, now we have a better way to prove the model, which is to run it on
data we left out, which is the training data.  We'll code to invoke the model
classifier on the test data:
```clojure
(ns zensols.example.sa-model
  (:require [clj-excel.core :as excel])
  (:require [zensols.actioncli.dynamic :refer (dyn-init-var) :as dyn]
            [zensols.actioncli.log4j2 :as lu]
            [zensols.actioncli.resource :as res]
            [zensols.util.spreadsheet :as ss]
            [zensols.model.execute-classifier :as exc :refer (with-model-conf)]
            [zensols.example.anon-db :as adb]))

(def preds-inst (atom nil))

(defn- test-annotation [anon-rec]
  (let [{anon :instance label :class-label} anon-rec
        sent (:text anon)
        pred (classify-utterance sent)]
    (log/debugf "label: %s, prediction: %s" label pred)
    {:label label
     :sent sent
     :prediction pred
     :correct? (= label pred)}))

(defn- predict-test-set []
  (swap! preds-inst
         #(or %
              (let [anons (adb/anons :set-type :test)
                    results (map test-annotation anons)
                    preds (map :correct? results)]
                {:correct (filter true? preds)
                 :incorrect (filter false? preds)
                 :predictions preds
                 :results results}))))

(defn- create-prediction-report []
  (letfn [(data-sheet [anons]
            (->> anons
                 (map (fn [anon]
                        [(:class-label anon) (->> anon :instance :text)]))
                 (cons ["Label" "Utterance"])))]
   (let [out-file (res/resource-path :analysis-report "sa-predictions.xls")]
     (-> (excel/build-workbook
          (excel/workbook-hssf)
          {"Predictions on test data"
           (->> (predict-test-set)
                :results
                (map (fn [res]
                       (let [{:keys [label sent prediction correct?]} res]
                         [correct? label prediction sent])))
                (cons ["Is Correct" "Gold Label" "Prediction" "Utterance"])
                (ss/headerize))
           "Training" (data-sheet (adb/anons))
           "Test" (data-sheet (adb/anons :set-type :test))})
         (ss/autosize-columns)
         (excel/save out-file)))))

(create-prediction-report)
```
Invoking this code creates a report on the desktop with the training data and
its predictions on the first sheet and the dataset by its set type (training
and testing) on the second two tabs.  We'll still correctly classify 230 of the
238 giving a 96% accuracy.

License
--------
Copyright © 2016 Paul Landes

Apache License version 2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
