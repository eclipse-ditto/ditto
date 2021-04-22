---
title: Announcement
keywords: announcement, signal
tags: [signal]
permalink: basic-signals-announcement.html
---

Announcements are special signals which are published in order to announce something before it actually happens.  
For example, before an [event](basic-signals-event.html) is created and published, an announcement could signal that
the event will happen soon.

Announcements have the following characteristics:
* they are **not** persisted/appended into any data store
* they are published to interested and authorized parties via the [WebSocket API](httpapi-protocol-bindings-websocket.html) 
  as well as [connection targets](basic-connections.html#targets) via [change notifications](basic-changenotifications.html).
