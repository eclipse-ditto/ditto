## Eclipse Ditto :: Signals

This module provides the so called `Signal`s of Eclipse Ditto which are further divided into:
* **base** - components which are required in all signal modules
* **commands** - containing `Command` which backing persistence services understand + the `CommandResponse`s
  those services send back as a result
* **events** - containing the `Event`s which are persisted into the backing datastore + emitted cluster internally
  when a Command was successfully applied

All `Signal`s can be serialized to a JSON representation and can be deserialized back into a Java object 
from a JSON representation.
