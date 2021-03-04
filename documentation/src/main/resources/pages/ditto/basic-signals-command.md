---
title: Command
keywords: command, modify, query, signal
tags: [signal]
permalink: basic-signals-command.html
---

Commands involve the need to change or retrieve something of a **digital twin** managed by Ditto or an actual device
connected to Ditto.

Commands always contain an identifier of the entity they address (e.g. a `Thing ID`). 

## Modify Commands

Commands which modify a **digital twin** or an actual device are grouped as "Modify Commands".<br/>
In <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.cqrs}}">CQRS</a> system those are simply
referred to as *commands*.

An overview of all Thing related modify commands can be found in the appropriate chapter of the Ditto Protocol:
* [Create/Modify protocol specification](protocol-specification-things-create-or-modify.html), 
* [Merge protocol specification](protocol-specification-things-merge.html), 
* [Delete protocol specification.](protocol-specification-things-delete.html) 

## Query Commands

Commands which only retrieve information about a **digital twin** or an actual device are grouped as "Query Commands".<br/>
In <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.cqrs}}">CQRS</a> system those are simply
referred to as *queries*.

An overview of all Thing related query commands can be found in the chapter
["Retrieve protocol specification"](protocol-specification-things-retrieve.html) of the Ditto Protocol.
