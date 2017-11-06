---
title: Access Control List (ACL)
keywords: authentication, authorization, auth, acl, access control
tags: [model]
permalink: basic-acl.html
---

Entries in the <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.acl}}">ACL</a> configure the permissions for subjects. A subject is identified by an arbitrary ID.

{% include note.html content="The ACL concept is only supported for Eclipse Ditto **HTTP API version 1**. <br /> 
For Eclipse Ditto **HTTP API version 2** we have introduced the Policies concept." %}


## Authorization Subject

A _subject_ can have following type of permission: read, write, administrate.

* A subject that has read permission is allowed to read all data of a Thing, i.e. its Attributes and Features and ACL.
* Write permission is required to be able to set data of the Thing or to send messages to a Thing or Feature.
* To be able to modify the ACL itself, a subject needs administrate permission.

When a Thing is initially created at least one entry of the ACL with read, write and administrate permission is mandatory 
and must be provided. If there is no ACL provided when creating a new Thing, a default ACL will be created. 
The default ACL entry consists of a subject ID (e.g. the ID of the currently logged in user), and all 3 permissions.

Also, there must always be at least one entry with all permissions. The last entry cannot be deleted or changed to not include all permissions.


## READ

Allows to read a Thing. This includes reading its ACL, Attributes, Features and their Properties.

The forwarding of messages to Subscribers is also secured according to the ACL entries on the Things themselves. 
E.g. only entities with READ permission on the Thing will be notified about changes related to that Thing and its Features.


## WRITE

Allows to write/modify a Thing.
E.g. create/update/delete Things, Attributes, Features and their Properties, execute Feature operations.

In order to send messages towards or from a Thing one would need WRITE permission on the specific Thing.


## ADMINISTRATE

Allows to modify the ACL of a Thing and to create/modify/delete its entries according to the restrictions mentioned before.


## Example for an Access Control List

The following example shows the access control list for “thingId”: “my.domain:myFirstThing”.

The first authorization subject is the ID of the default nginx user “ditto”.
The second is the ID of another user “adam” which would have an additional `ADMINISTRATE` permission compared to the other user.

```json
"acl": {
  "ditto": {
    "READ": true,
    "WRITE": false,
    "ADMINISTRATE": false
  },
  "adam": {
    "READ": true,
    "WRITE": true,
    "ADMINISTRATE": true
  }
}
```
