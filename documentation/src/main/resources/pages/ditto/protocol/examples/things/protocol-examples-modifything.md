---
title: Modify a Thing
keywords: examples, modify thing
search: exclude
permalink: protocol-examples-modifything.html
---

{% capture command %}{% include_relative generated/commands/modify/modifything.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifythingresponse.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/thingmodified.md %}{% endcapture %}
{{ event | markdownify }}

{% capture command %}{% include_relative generated/commands/modify/modifythingalternatives.md %}{% endcapture %}
{{ command | markdownify }}
