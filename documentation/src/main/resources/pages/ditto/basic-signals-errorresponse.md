---
title: Error response
keywords: signal, error, errorresponse, exception, response
tags: [signal]
permalink: basic-signals-errorresponse.html
---

If an issued [command](basic-signals-command.html) or [message](basic-messages.html) could not be applied, an 
appropriate error response conveys this information back to the issuer.
Failure of a command or message can have various reasons, starting from missing permissions to internal server errors 
during processing of the command.

The [Ditto Protocol for Errors](protocol-specification-errors.html) defines how error responses look in Ditto Protocol. 

An overview of all Thing-related error responses can be found in the examples chapter
["Error responses"](protocol-examples-errorresponses.html) of the Ditto Protocol.
