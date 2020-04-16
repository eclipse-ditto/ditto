---
title: Search protocol examples
keywords: examples, search
search: exclude
permalink: protocol-examples-search.html
---

{% capture command %}{% include_relative generated/commands/create-subscription-command.md %}{% endcapture %}
{{ command | markdownify }}

{% capture command %}{% include_relative generated/commands/request-subscription-command.md %}{% endcapture %}
{{ command | markdownify }}

{% capture command %}{% include_relative generated/commands/cancel-subscription-command.md %}{% endcapture %}
{{ command | markdownify }}

{% capture event %}{% include_relative generated/events/subscription-created-event.md %}{% endcapture %}
{{ event | markdownify }}

{% capture event %}{% include_relative generated/events/subscription-has-next-event.md %}{% endcapture %}
{{ event | markdownify }}

{% capture event %}{% include_relative generated/events/subscription-complete-event.md %}{% endcapture %}
{{ event | markdownify }}

{% capture event %}{% include_relative generated/events/subscription-failed-event.md %}{% endcapture %}
{{ event | markdownify }}
