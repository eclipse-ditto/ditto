---
title: Create a Thing
keywords: examples, create thing
search: exclude
permalink: protocol-examples-creatething.html
---

{% capture command %}{% include_relative generated/commands/modify/creatething.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/createthingresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/thingcreated.md %}{% endcapture %}
{{ event | markdownify }}

{% capture command %}{% include_relative generated/commands/modify/createthingalternatives.md %}{% endcapture %}
{{ command | markdownify }}
