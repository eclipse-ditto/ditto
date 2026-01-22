---
name: release-notes
description: Generate Ditto release notes for a GitHub milestone. Use when writing release notes for a new Ditto version. Requires the milestone name (e.g., "3.9.0") as argument.
allowed-tools: Read, Grep, Glob, Bash(gh:*), WebFetch
---

# Ditto Release Notes Generator

Generate release notes for Eclipse Ditto following the established format and writing style.

## Arguments

The skill expects the milestone name as `$ARGUMENTS` (e.g., "3.9.0", "3.8.2").

## Process

### Step 1: Determine Release Type

- **Major release** (x.0.0): New major version with breaking changes
- **Minor release** (x.y.0): New features, enhancements, and bugfixes
- **Patch release** (x.y.z where z > 0): Bugfixes only, no new features

### Step 2: Gather Information from GitHub

Fetch data from the GitHub milestone for `eclipse-ditto/ditto`:

```bash
# List all PRs in the milestone
gh pr list --repo eclipse-ditto/ditto --search "milestone:$ARGUMENTS" --state merged --limit 200 --json number,title,body,labels,url

# List all issues in the milestone
gh issue list --repo eclipse-ditto/ditto --search "milestone:$ARGUMENTS" --state closed --limit 200 --json number,title,body,labels,url
```

For each PR, extract:
- PR number and title
- Related issue (look for "Fixes #", "Closes #", "Resolves #" in PR body)
- Labels (to categorize as feature, bugfix, enhancement, etc.)

### Step 3: Categorize Items

Group PRs/issues into categories based on labels and content:
- **New features**: PRs with `enhancement` label or significant new functionality
- **Changes**: Non-functional improvements (performance, refactoring, dependency updates)
- **Bugfixes**: PRs with `bug` label or fixing issues
- **Helm Chart**: Changes to the Helm chart configuration

### Step 4: Generate Release Notes

Create the file at: `documentation/src/main/resources/pages/ditto/release_notes_<VERSION>.md`

Where `<VERSION>` is the milestone with dots removed (e.g., "3.9.0" becomes "390").

### Step 5: Update the Sidebar

Add the new release to the documentation sidebar at `documentation/src/main/resources/_data/sidebars/ditto_sidebar.yml`.

The new release entry must be added as the **first item** under the "Release Notes" `folderitems` section, so it appears at the top of the list.

Add an entry in this format:
```yaml
          - title: <VERSION>
            url: /release_notes_<VERSION_NODOTS>.html
            output: web
```

For example, for version 3.8.11, add:
```yaml
          - title: 3.8.11
            url: /release_notes_3811.html
            output: web
```

## Release Notes Format

### For Minor Releases (x.y.0)

```markdown
---
title: Release notes <VERSION>
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version <VERSION> of Eclipse Ditto, released on <DATE>"
permalink: release_notes_<VERSION_NODOTS>.html
---

[Opening paragraph - vary the wording, examples:]
- "The Ditto team is happy to announce the availability of Eclipse Ditto <VERSION>."
- "We are happy to announce the availability of **Eclipse Ditto <VERSION>**."
- "After [timeframe], we are happy to announce the availability of **Eclipse Ditto <VERSION>**."

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip)
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly
investigated."


## Changelog

Eclipse Ditto <VERSION> focuses on the following areas:

* **Bold key phrase** describing feature 1
* **Bold key phrase** describing feature 2
* ...

The following non-functional work is also included:

* **Bold key phrase** describing change 1
* ...

The following notable fixes are included:

* **Bold key phrase** describing fix 1
* ...


### New features

#### <Feature Title>

Issue [#<ISSUE>](https://github.com/eclipse-ditto/ditto/issues/<ISSUE>) / PR [#<PR>](https://github.com/eclipse-ditto/ditto/pull/<PR>)
<brief description of the feature - 1-3 paragraphs explaining what it does and why it's useful>

The documentation of the feature can be found [here](<documentation-link>.html).

[Repeat for each feature]


### Changes

#### <Change Title>

PR [#<PR>](https://github.com/eclipse-ditto/ditto/pull/<PR>) <description of the change>

[Repeat for each change]


### Bugfixes

#### <Bugfix Title>

[If has related issue:]
Issue [#<ISSUE>](https://github.com/eclipse-ditto/ditto/issues/<ISSUE>) / PR [#<PR>](https://github.com/eclipse-ditto/ditto/pull/<PR>)
<description of what was fixed>

[If no related issue:]
PR [#<PR>](https://github.com/eclipse-ditto/ditto/pull/<PR>) <description of what was fixed>

[Repeat for each bugfix]


### Helm Chart

The Helm chart was enhanced with the configuration options of the added features of this release.
[Add any specific Helm-related notes if applicable]


## Migration notes

[If no migration needed:]
No known migration steps are required for this release.

[If migration needed, list the steps]
```

### For Patch Releases (x.y.z where z > 0)

```markdown
---
title: Release notes <VERSION>
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version <VERSION> of Eclipse Ditto, released on <DATE>"
permalink: release_notes_<VERSION_NODOTS>.html
---

This is a bugfix release, no new features since [<PREVIOUS_VERSION>](release_notes_<PREVIOUS_NODOTS>.html) were added.

## Changelog

Compared to the latest release [<PREVIOUS_VERSION>](release_notes_<PREVIOUS_NODOTS>.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A<VERSION>).

#### <Bugfix Title>

PR [#<PR>](https://github.com/eclipse-ditto/ditto/pull/<PR>) <description of what was fixed>

[Repeat for each bugfix]
```

## Writing Style Guidelines

1. **Link format for issues and PRs**:
   - With issue: `Issue [#123](https://github.com/eclipse-ditto/ditto/issues/123) / PR [#456](https://github.com/eclipse-ditto/ditto/pull/456)`
   - PR only: `PR [#456](https://github.com/eclipse-ditto/ditto/pull/456)`
   - Multiple PRs: `PR [#456](https://github.com/eclipse-ditto/ditto/pull/456) and [#457](https://github.com/eclipse-ditto/ditto/pull/457)`

2. **Feature descriptions**: Write in present tense, explain what the feature does and why it's useful

3. **Documentation links**: Use relative links like `[here](feature-name.html)` or `[documentation](basic-feature.html)`

4. **Bold emphasis**: Use `**bold**` for key phrases in the changelog summary bullets

5. **Conciseness**: Keep descriptions brief but informative. 1-3 paragraphs for features, 1 paragraph for bugfixes

6. **Consistent terminology**: Use "Thing", "Policy", "Connection", "Feature" (capitalized) when referring to Ditto concepts

## Reference

For style reference, read existing release notes:
- Minor release example: `documentation/src/main/resources/pages/ditto/release_notes_380.md`
- Patch release example: `documentation/src/main/resources/pages/ditto/release_notes_381.md`

## Output

After generating the release notes:
1. Show the generated content to the user for review
2. Ask if there are any documentation links that need to be added or corrected
3. Ask for the release date if not provided
4. Create the file in the documentation directory
5. Add the release to the sidebar in `documentation/src/main/resources/_data/sidebars/ditto_sidebar.yml` as the first entry under "Release Notes"
