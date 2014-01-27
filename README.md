# Domain

Domain is, at its roots, a domain type declartion DSL, written in Clojure.

The type declarations can be used

* to autogenerate REST APIs
* to validate input data
* to persist data in Mongo DB

## Assembly Line

The assembly line is designed to create data workflows with different stations where the processing can be done. It can easily be extend with new stations. You can run it up to a specific station, pause it, resume it and execute it asynchronously.

## Domain Core

The core parts

## Domain Versioned Mongo

A library inspired by ClojureQL, but for MongoDB. Treats documents as immutable values.

## Domain Example

An example of Domain in action.

## News

2014-01-27
* The project was extracted from it's mother. It's in a confused state, very fragile and might not work very well yet. It's not suitable for any serious work, but expect this to change!

## License

Copyright © 2014 TimeZynk AB

Distributed under the BSD 3-Clause License.
