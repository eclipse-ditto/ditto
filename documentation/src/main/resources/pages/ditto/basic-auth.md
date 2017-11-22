---
title: Authentication and authorization
keywords: auth, authentication, authorization, policies, policy
tags: [model]
permalink: basic-auth.html
---

You can integrate your solutions with Ditto 

* via the [HTTP API](http-api-doc.html) or
* via WebSocket.

On all APIs Ditto protects functionality and data by using

* **Authentication** to make sure the requester is the one she claims to be,
* **Authorization** to make sure the requester is allowed to see, use or change the information he wants to access.

## Authentication

User authentication at the HTTP API

A user who calls the HTTP API can be authenticated using two mechanisms:

* HTTP BASIC Authentication by providing username and password of users managed within for example nginx acting as
  reverse proxy.
* A <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.jwt}}">JWT</a> issued by Google or other
  OpenID Connect providers.

## Authorization

Authorization is implemented with an <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.acl}}">ACL</a>
(in API version 1) or a <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.policy}}">Policy</a>
(in API version 2).

Please find details at [ACL](basic-acl.html) and [Policies](basic-policy.html).
