## Eclipse Ditto :: Model

This module contains the domain model of Eclipse Ditto.

It consists of following sub-modules which are explained here briefly:
* **base** - components which are required in all domain model modules
* **devops** - model for "devops" purposes like changing the log level during runtime
* **messages** - model of `Message`s from/to `Thing`s
* **policies** - model for defining fine grained access for `Policy`s, `Thing`s, `Message`s
* **policies-enforcers** - algorithms for enforcing `Policy`s including JMH benchmark setup for checking performance of different `PolicyEnforcer`s + unit tests for various scenarios
* **things** - model for the heart of Ditto - the `Thing` and its `Feature`s 
* **thingsearch** - model for searching for `Thing`s
* **thingsearch-parser** - model for the query language (RQL) of the thingsearch
