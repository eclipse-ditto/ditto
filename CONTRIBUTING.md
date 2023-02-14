# How to contribute to Eclipse Ditto
 
First of all, thanks for considering to contribute to Eclipse Ditto. We really appreciate the time and effort you want to
spend helping to improve things around here.

In order to get you started as fast as possible we need to go through some organizational issues first, though.

## Legal Requirements

Ditto is an [Eclipse IoT](https://iot.eclipse.org) project and as such is governed by the Eclipse Development process.
This process helps us in creating great open source software within a safe legal framework.

For you as a contributor, the following preliminary steps are required in order for us to be able to accept your contribution:

* Sign the [Eclipse Foundation Contributor Agreement](https://www.eclipse.org/legal/ECA.php).
In order to do so:
  * Obtain an Eclipse Foundation user ID. Anyone who currently uses Eclipse Bugzilla or Gerrit systems already has one of those.
If you don't already have an account simply [register on the Eclipse web site](https://dev.eclipse.org/site_login/createaccount.php).
  * Once you have your account, log in to the [projects portal](https://projects.eclipse.org/), select *My Account* and then the *Contributor License Agreement* tab.

* Add your GitHub username to your Eclipse Foundation account. Log in to Eclipse and go to [Edit my account](https://dev.eclipse.org/site_login/myaccount.php).

The easiest way to contribute code/patches/whatever is by creating a GitHub pull request (PR). When you do make sure that you *Sign-off* your commit records using the same email address used for your Eclipse account.

You do this by adding the `-s` flag when you make the commit(s), e.g.

    $> git commit -s -m "Shave the yak some more"

You can find all the details in the [Contributing via Git](http://wiki.eclipse.org/Development_Resources/Contributing_via_Git) document on the Eclipse web site.

## Codestyle

We use the [Google Java Style Guide](https://github.com/google/styleguide) where a formatter for Eclipse IDE is available. 

The only adjustment: use longer lines ("line split") with 120 characters instead of only 100.

## Making your Changes

* Fork the repository on GitHub
* Create a new branch for your changes
* Make your changes
* Make sure you include test cases for non-trivial features
* Make sure the test suite passes after your changes
* Please make sure to format your code with the above mentioned formatter
* Commit your changes into that branch
* Use descriptive and meaningful commit messages
* If you have more than one commit, squash your commits into a single commit 
* Make sure you use the `-s` flag when committing as explained above
* Push your changes to your branch in your forked repository

## License header

Please make sure any file you newly create contains a proper license header. Find the latest one in use here:
[src/license-header.txt](src/license-header.txt)

Adjusted for Java classes:
```java
/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
```

Adjusted for XML files:
```xml
<!--
  ~ Copyright (c) 2023 Contributors to the Eclipse Foundation
  ~
  ~ See the NOTICE file(s) distributed with this work for additional
  ~ information regarding copyright ownership.
  ~
  ~ This program and the accompanying materials are made available under the
  ~ terms of the Eclipse Public License 2.0 which is available at
  ~ http://www.eclipse.org/legal/epl-2.0
  ~
  ~ SPDX-License-Identifier: EPL-2.0
  -->
```

## Submitting the Changes

Submit a pull request via the normal GitHub UI.

## After Submitting

* Do not use your branch for any other development, otherwise further changes that you make will be visible in the PR.


# OSS development process - rules

As of 02/2023, the following additional "rules" regarding the open OSS development process were agreed on.

## Addition of new features via feature toggles

Goals:
* Reduce the risk for other Ditto users that a new feature have an impact on existing functionality and stability
* Whenever possible (and feasible), added functionality shall be added using a "feature toggle".

Ditto already has a class `FeatureToggle.java` where feature toggles are contained and providing functionality to
"secure" a feature with a method in there which throws an `UnsupportedSignalException` once a feature is used which
is disabled via feature toggle.

The toggles are then configured in `ditto-devops.conf` file and can be enabled/disabled via the contained environment variables.

## Creating GitHub issues before starting to work on code

Goals:
* Improve transparency on what is currently happening
* Openly discuss new features and whether they are a good fit for Ditto
* Reduce "time waste"

Whenever a new feature or a bugfix is being worked on, we want to create an issue in Ditto's GitHub project **beforehand**:
https://github.com/eclipse-ditto/ditto/issues

This provides the needed transparency for other contributors before much effort is put into a new topic in order to:
* Get input on the background (e.g. use case behind / "the need") of the feature/bugfix
* Provide feedback, maybe even suggesting alternatives instead
* Provide suggestions of how to implement it the most efficient way
* Maybe even find synergies when more than 1 contributing companies currently have the same topic to work on

The following situation shall be prevented:
* If no issue is created upfront, a contributing company e.g. invests 2 months of work in a new feature
* Then a PR is created with this new functionality
* Only then, a discussion with other contributors can start
* At this point, when there e.g. is a big flaw in the architecture or security or API stability of the added functionality,
  the invested 2 months could - in the worst case - be a complete waste of time
* This could easily be resolved by discussing it beforehand

## Create PullRequests early

Goals:
* Get early feedback on implementation details of new features / bugfixes
* Prevent that an implementation goes "into the wrong direction" (e.g. performance or security wise)

PullRequests should be created quite early and publicly on the Ditto project.  
If they are not yet "ready" to review/merge, they must be marked as "DRAFT" - once they are ready, they can be marked
as such and a review can be performed.

## Make use of GitHub "Projects" for showing current work for next release

Goals:
* Make transparent "who" currently works on "what"
* Make transparent what the current agenda for the next Ditto release is

The new "Projects" capabilities of GitHub look more than sufficient of what we want to achieve here:
* https://github.com/orgs/eclipse-ditto/projects/1
* https://github.com/orgs/eclipse-ditto/projects/1/views/2 (table view is especially useful, as grouping by "Milestone" is necessary)

## Establish system-tests in the OpenSource codebase

Goals:
* Provide means to run automated tests for future enhancements to Ditto
* Secure existing functionality, avoid breaking APIs and existing functionality when changes to the Ditto OSS codebase are done

The system tests for Eclipse Ditto were initiated here:  
https://github.com/eclipse-ditto/ditto-testing

The tests should be part of the validations done in a PR before a PR is approved and merged.  
In order to be able to do that, we want to clarify if the Eclipse Foundation can provide enough resources in order to 
run the system-tests in a stable way.

Currently, that seems to be quite difficult, as projects only have very limited resources in order to run their builds.  
In addition, the CI runs in an OpenShift cluster with additional restrictions, e.g. regarding the kind of Docker images
which can be run, exposing of the Docker socket, etc.

## Regular community meetings

Goals:

* Discuss upcoming topics/problems in advance
* Stay in touch via audio/video
* Build up a (contributor and adopter) community who can help each other

We want to re-establish regular community meetings/call, e.g. a meeting every 2 weeks for 1 hour.  
We can utilize the Zoom account from the Eclipse Foundation to have a "neutral" one .. or just use "Google Meet".

## Chat for (internal) exchanges

Goals:
* Have a direct channel where to reach other Ditto committers and contributors
* In order to get timely responses if e.g. a bugfix release has to be scheduled/done quickly

We can use "Gitter.IM" communities to add different rooms of which some also can be private:  
https://gitter.im/EclipseDitto/community

## Release strategy

Goals:
* Have rules of how often to do "planned feature releases"
* Have options for contributing companies to prioritize a release (e.g. if urgent bugfix or urgent feature release)

The suggestion would be to have approximately 4 planned minor releases per year, 1 each quarter (e.g. 03 / 06 / 09 / 12).  
If needed and all contributing companies agree minor releases can also happen earlier/more often.

Bugfix releases should be done immediately if a critical bug was fixed and either the contributors or the community need a quick fix release.

## Approving / merging PRs

Goals:
* PullRequests - once they are ready - shall not stay unmerged for a long time as this leads to the risk they are not 
  mergable or get outdated quickly

Approach:

* Before merging a PR at least 1 approval is required
  * Approvals shall only be issued after a code review
  * Preferably that would be 1 approval from an existing Ditto committer
  * But could also be the approval of an active contributor who does not yet have committer status
* If no approval is given for a PR within a duration of 4 weeks after declaring it "ready", a PR can also be merged without other approvals
  * Before doing so, the reasons for not approving must be found out (e.g. via the Chat / community call)
  * If the reason simply is "no time" and there are no objections against that PR, the PR can be merged without other approvals
