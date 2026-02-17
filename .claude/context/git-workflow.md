# Git Workflow & Contribution Guidelines

**IMPORTANT**: All code contributions must follow Eclipse Foundation requirements.

## Main Branch

**Branch Name**: `master` (use this as the base for PRs)

## Contribution Workflow

### 1. Sign Eclipse Contributor Agreement (ECA)

Before contributing, you must:
- Obtain an Eclipse Foundation user ID at https://dev.eclipse.org/site_login/createaccount.php
- Sign the Eclipse Contributor Agreement at https://www.eclipse.org/legal/ECA.php
- Add your GitHub username to your Eclipse Foundation account

### 2. Create GitHub Issue First

**Always create a GitHub issue before starting work** to:
- Discuss the approach with maintainers
- Get feedback on implementation strategy
- Avoid wasted effort if the approach isn't suitable
- Allow other contributors to provide input

This is a project requirement per the OSS development process.

### 3. Create PR Early as DRAFT

- Create a **draft PR** early in the development process
- Get early feedback on implementation details
- Prevent implementation from going in wrong direction
- Mark as "Ready for Review" when complete

### 4. Squash Commits Before Merge

- Combine multiple commits into a single commit
- Use interactive rebase: `git rebase -i HEAD~N`
- Keep commit history clean

### 5. Get Approval

- **At least 1 approval** required from:
  - An existing Ditto committer (preferred), OR
  - An active contributor
- If no approval within 4 weeks and no objections, PR can be merged
- Code reviews should check for:
  - Correctness and functionality
  - Code style compliance
  - Test coverage
  - License headers

### 6. Commit Message Format

```
Brief description of change (50 chars or less)

More detailed explanation if needed. Wrap at 72 characters.
Explain what and why, not how. The code shows the how.

Fixes #123

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

**Key points:**
- First line: brief summary (imperative mood: "Add" not "Added")
- Blank line after first line
- Body: detailed explanation if needed
- Reference issue: `Fixes #123`, `Relates to #456`
- Co-Authored-By: If applicable (like when using Claude Code)

## Branch Naming

**Always create branches based on `master`.**

Use the following naming conventions:
- **New features**: `feature/<feature-description>` (e.g., `feature/add-mqtt-support`)
- **Bug fixes**: `bugfix/<bugfix-description>` (e.g., `bugfix/thing-deletion-error`)
- **Refactoring**: `refactor/<description>` (e.g., `refactor/policy-enforcement`)

## Claude Context Branch

The `claude` branch is used to maintain Claude Code context (`CLAUDE.md` and `.claude/` directory).

### Committing Claude Context Changes

When on the `claude` branch, use the **force flag** (`-f`) when staging `CLAUDE.md` or `.claude/` files:

```bash
# Stage Claude context files (force required as they're in .gitignore for feature branches)
git add -f CLAUDE.md .claude/

# Or update already-tracked files
git add -u CLAUDE.md .claude/context/

# Then commit with sign-off
git commit -s -m "Update Claude context documentation"
```

**Why force?** These files are excluded from modification on feature branches to keep them stable. The `claude` branch is the designated place for maintaining this context.

## Important Notes

### Feature Toggles Required

New features **which change existing behavior** MUST use feature toggles. See `feature-toggles.md` for details.

### Test Coverage

- Add tests for non-trivial features
- Ensure test suite passes: `mvn test`
- Run full verification: `mvn verify`

### Code Style

- Use Google Java Style Guide (120 char line length)
- Run formatter before committing
- Check license headers: `mvn license:check`

### What NOT to Do

- Don't create PR without GitHub issue first
- Don't push to master directly
- Don't force push after code review starts
- Don't include unrelated changes in your PR

## Release Process

Releases happen approximately quarterly (March, June, September, December):
- Minor releases: 4 times per year
- Bugfix releases: As needed for critical fixes
- Feature releases can happen more often if all contributors agree

## Resources

- **GitHub Issues**: https://github.com/eclipse-ditto/ditto/issues
- **Gitter Chat**: https://gitter.im/eclipse/ditto
- **ECA Info**: https://www.eclipse.org/legal/ECA.php
- **Contributing Guide**: See CONTRIBUTING.md in repository root
