---
title: Authentication and cuthorization
keywords: authentication, authorization, auth, policy, policies
tags: [model]
permalink: basic-auth.html
---

You can integrate your solutions with Ditto in two ways:

* Via the REST API, or
* Via WebSocket.

On all APIs Ditto protects functionality and data by using

* **Authentication** to make sure the requester is the one he states
* **Authorization** to make sure the requester is allowed to see, use or change the information he wants to access

## Authentication

User authentication at the REST API

A user who calls the REST API can be authenticated by using:

* HTTP BASIC Authentication by providing username and password of users managed within for example the nginx acting as reverse proxy.
* A <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.jwt}}">JWT</a> issued by Google or other OpenID Connect providers.

## Authorization

Authorization is implemented with an <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.acl}}">ACL</a> (in API version 1) or a <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.policy}}">Policy</a> (in API version 2).
The Policy description is located at Thing level, however it can optionally grant or revoke permissions very fine-grained at any Thing sub-resource like `thing:/features/featureX/properties/propertyN`.

Find details at [ACL](basic-acl.html) and [Policies](basic-policies.html).
