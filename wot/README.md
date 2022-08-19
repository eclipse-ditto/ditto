## Eclipse Ditto :: WoT

This module contains models and implementation of the W3C "Web of Things" (WoT) integration of Eclipse Ditto.

As of version `2.4.0` of Ditto, this implementation follows the 
[Web of Things (WoT) Thing Description 1.1](https://www.w3.org/TR/wot-thing-description11/).

This module is separated in 2 submodules:
* **model** (ditto-wot-model): contains a Java model of WoT (Web of Things) entities based on **ditto-json**.<br/>
  May be used in order to:
   * read a WoT "Thing Model" or "Thing Description" JSON from a String and convert it to Java objects
   * use the builder based Java API in order to create a WoT "Thing Model" or "Thing Description" from Java and e.g.
     output the JSON representation
* **integration** (ditto-wot-integration): contains interfaces and implementation of how WoT "Thing Models" are 
  converted to "Thing Descriptions" by e.g. injecting Ditto specific endpoints in the `form` definitions of the TDs 
 (Thing Descriptions)
