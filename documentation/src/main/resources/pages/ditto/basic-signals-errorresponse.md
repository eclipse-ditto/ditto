---
title: Error response
keywords: signal, error, errorresponse, exception, response
tags: [signal]
permalink: basic-signals-errorresponse.html
---

If an issued [command](basic-signals-command.html) or [message](basic-messages.html) could not be applied, an 
appropriate error response conveys this [error](basic-errors.html) information back to the issuer.  
Failure of a command or message can have various reasons, starting from missing permissions to internal server errors 
during processing of the command.

The [Ditto Protocol for Errors](protocol-specification-errors.html) defines how error responses look in Ditto Protocol. 

An overview of some possible error responses can be found in the examples chapters:
* [Things error response examples](protocol-examples-errorresponses.html)
* [Policies error response examples](protocol-examples-policies-errorresponses.html)

