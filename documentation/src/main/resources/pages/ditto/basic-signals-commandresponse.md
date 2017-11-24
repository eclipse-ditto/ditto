---
title: Command response
keywords: signal, commandresponse, response
tags: [signal]
permalink: basic-signals-commandresponse.html
---

CommandResponses are the answer to [Commands](basic-signals-command.html) and include information about whether the
intention of changing something via a `ModifyCommand` has worked or if there was an [Error](basic-signals-errorresponse.html) 
instead.

The CommandResponse of QueryCommands contains the requested information.
