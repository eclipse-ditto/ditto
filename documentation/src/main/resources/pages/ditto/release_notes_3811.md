---
title: Release notes 3.8.11
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.11 of Eclipse Ditto, released on 22.01.2026"
permalink: release_notes_3811.html
---

This is a bugfix release, no new features since [3.8.10](release_notes_3810.html) were added.

## Changelog

Compared to the latest release [3.8.10](release_notes_3810.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.8.11).

#### Fix CommandTimeoutExceptions for messages requiring no response

PR [#2303](https://github.com/eclipse-ditto/ditto/pull/2303) fixes CommandTimeoutExceptions that occurred for messages
which did not require a response, such as outbox messages and events.

#### Fix hard-coded local ask timeouts not respecting recovery phase

PR [#2297](https://github.com/eclipse-ditto/ditto/pull/2297) fixes an issue where hard-coded "local ask timeouts" of
5 seconds were not adjusted during recovery.  
Now, during recovery, much higher ask timeouts are used, and after recovery completes, the timeouts fall back to the
configured values instead of hard-coded ones.

### Helm Chart

#### Make nginx htpasswd secret configurable

PR [#2310](https://github.com/eclipse-ditto/ditto/pull/2310) makes the nginx htpasswd secret configurable in the Helm chart.
This allows using a custom Secret resource, which could be one automatically managed by external-secrets operator.

#### Condition-based maxUnavailable pod disruption budget

PR [#2301](https://github.com/eclipse-ditto/ditto/pull/2301) adds conditions to avoid setting `minAvailable` and
`maxUnavailable` together in pod disruption budgets, which is not allowed per Kubernetes spec.  
An explicit check was added to fail if someone tries to set both values, and empty default values for `maxUnavailable`
were added so existing deployments don't start failing.

#### Add possibility to add custom annotations to service account

PR [#2300](https://github.com/eclipse-ditto/ditto/pull/2300) adds the possibility to add custom annotations to the
service account.  
GitOps tools like ArgoCD or Flux may cause problems on first install when creating pre-upgrade hooks, as the job needs
a service account that doesn't exist yet. This change allows adding sync-wave annotations in ArgoCD to ensure the
service account is present when creating Ditto for the first time.
