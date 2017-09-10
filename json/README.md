## Eclipse Ditto :: JSON

This module contains the Eclipse Ditto JSON library.

This library is not intended to be the 101 Java JSON library - there are plenty very good JSON parsers and 
libraries out there. 

It has 2 main goals:
* Provide truly immutable JSON values (required to guarantee immutability of the messages Ditto uses in its Akka services)
* Provide concepts like `JsonFieldDefintion` in order to define a schema of how JSON objects look like, which fields
  are regular/hidden and in which `JsonSchemaVersion`s are they available. 
