---
title: Command
keywords: signal, command
tags: [signal]
permalink: basic-signals-command.html
---

Commands involve the need to change or retrieve something of a **Digital Twin** managed by Ditto or an actual device
connected to Ditto.

Commands always contain an identifier of the entity they address (e.g. a `Thing ID`). 

## Modify Commands

Commands which modify a **Digital Twin** or an actual device are grouped as "Modify Commands".<br/>
In <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.cqrs}}">CQRS</a> system those are simply
referred to as _commands_.

An overview of all `Thing` related modify commands can be found in the chapter of the "Ditto Protocol":
* [Create protocol specification](protocol-specification-things-create.html) 
* [Modify protocol specification](protocol-specification-things-modify.html) 
* [Delete protocol specification](protocol-specification-things-delete.html) 

## Query Commands

Commands which only retrieve information about a **Digital Twin** or an actual device are grouped as "Query Commands".<br/>
In <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.cqrs}}">CQRS</a> system those are simply
referred to as _queries_.

An overview of all `Thing` related query commands can be found in the chapter of the "Ditto Protocol":
* [Retrieve protocol specification](protocol-specification-things-retrieve.html) 
