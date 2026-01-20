# Release Notes Skill Implementation

## Overview

Created a Claude Code skill for generating Ditto release notes following the established format and style.

## Location

- Skill directory: `.claude/skills/release-notes/`
- Main file: `.claude/skills/release-notes/SKILL.md`

## Usage

Invoke the skill with:
```
/release-notes <milestone>
```

Example:
```
/release-notes 3.9.0
```

## Features

### Automatic Data Gathering
- Fetches PRs and issues from the GitHub milestone using `gh` CLI
- Extracts PR numbers, titles, bodies, and labels
- Identifies related issues from PR bodies (Fixes #, Closes #, Resolves #)

### Release Type Detection
- **Minor releases** (x.y.0): Full release notes with features, changes, bugfixes
- **Patch releases** (x.y.z): Simplified bugfix-only format

### Consistent Formatting
- YAML frontmatter matching existing release notes
- Proper linking to GitHub issues and PRs
- Documentation links using relative paths
- Bold key phrases in changelog summaries

### Writing Style
- Matches the tone and style of existing Ditto release notes
- Uses present tense for feature descriptions
- Properly capitalizes Ditto concepts (Thing, Policy, Connection, Feature)

## Reference Files Analyzed

- `documentation/src/main/resources/pages/ditto/release_notes_380.md` (minor release example)
- `documentation/src/main/resources/pages/ditto/release_notes_381.md` (patch release example)
- `documentation/src/main/resources/pages/ditto/release_notes_370.md` (another minor release)

## Output

The skill generates release notes to:
```
documentation/src/main/resources/pages/ditto/release_notes_<VERSION>.md
```

Where `<VERSION>` is the milestone with dots removed (e.g., "3.9.0" becomes "390").

## Notes

- The skill asks the user for the release date if not provided
- It prompts for review of documentation links after generation
- It follows the IP (intellectual property) statement pattern for minor releases
