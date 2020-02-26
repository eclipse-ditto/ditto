---
title: Delete a Thing
keywords: examples, delete thing
search: exclude
permalink: protocol-examples-deletething.html
---

{% capture command %}{% include_relative generated/commands/modify/deletething.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/deletethingresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/thingdeleted.md %}{% endcapture %}
{{ event | markdownify }}

