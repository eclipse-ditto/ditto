---
title: Create a Thing
keywords: examples, create thing
search: exclude
permalink: protocol-examples-creatething.html
---

{% capture command %}{% include_relative protocol/things/commands/modify/creatething.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative protocol/things/commands/modify/createthingresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative protocol/things/events/thingcreated.md %}{% endcapture %}
{{ event | markdownify }}

