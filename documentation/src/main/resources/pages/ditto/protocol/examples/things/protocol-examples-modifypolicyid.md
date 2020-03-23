---
title: Modify the policy ID of a Thing
keywords: examples, modify policy id
search: exclude
permalink: protocol-examples-modifypolicyid.html
---

{% capture command %}{% include_relative generated/commands/modify/modifypolicyid.md %}{% endcapture %}
{{ command | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifypolicyidresponsemodified.md %}{% endcapture %}
{{ response | markdownify }}

{% capture response %}{% include_relative generated/commands/modify/modifypolicyidresponsecreated.md %}{% endcapture %}
{{ response | markdownify }}

{% capture event %}{% include_relative generated/events/policyidcreated.md %}{% endcapture %}
{{ event | markdownify }}

{% capture event %}{% include_relative generated/events/policyidmodified.md %}{% endcapture %}
{{ event | markdownify }}
