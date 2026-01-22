---
name: cherry-pick-release
description: Cherry-pick commits from merged PRs in a GitHub milestone to a release branch. Use when preparing a bugfix/patch release. Requires the milestone name (e.g., "3.8.11") as argument.
allowed-tools: Read, Grep, Glob, Bash(gh:*), Bash(git *), AskUserQuestion
---

# Cherry-Pick Release Commits

Cherry-pick commits from merged PRs in a GitHub milestone to the corresponding release branch for bugfix/patch releases.

## Arguments

The skill expects the milestone name as `$ARGUMENTS` (e.g., "3.8.11", "3.9.2").

## Process

### Step 1: Parse Version and Determine Release Branch

From the milestone version (e.g., "3.8.11"):
- Extract major.minor version: "3.8"
- Determine release branch name: `release-3.8`

Validate that this is a patch release (z > 0 in x.y.z).

### Step 2: Verify Release Branch Exists

```bash
git fetch origin
git branch -r | grep "origin/release-<MAJOR>.<MINOR>"
```

If the release branch doesn't exist, inform the user and stop.

### Step 3: Checkout Release Branch

```bash
git checkout release-<MAJOR>.<MINOR>
git pull origin release-<MAJOR>.<MINOR>
```

### Step 4: Fetch PRs from GitHub Milestone

```bash
gh pr list --repo eclipse-ditto/ditto --search "milestone:$ARGUMENTS" --state merged --limit 200 --json number,title,mergeCommit,commits,url
```

This returns:
- `number`: PR number
- `title`: PR title
- `mergeCommit`: The merge commit SHA (to be excluded)
- `commits`: List of commits in the PR
- `url`: PR URL

### Step 5: For Each PR, Ask User for Consent

For each merged PR in the milestone:

1. Display PR information:
   - PR number and title
   - PR URL
   - List of commits (excluding merge commits) with their SHAs and messages

2. Use `AskUserQuestion` to ask the user:
   - "Cherry-pick commits from PR #<NUMBER>: <TITLE>?"
   - Options: "Yes, cherry-pick" / "Skip this PR"

3. Show the commits that will be cherry-picked:
   ```
   Commits to cherry-pick:
   - <SHA1> <commit message 1>
   - <SHA2> <commit message 2>
   ```

### Step 6: Cherry-Pick Approved Commits

For each approved PR, cherry-pick the commits in order (oldest first):

```bash
# Get commits for a specific PR (excluding merge commits)
gh pr view <PR_NUMBER> --repo eclipse-ditto/ditto --json commits --jq '.commits[].oid'

# Cherry-pick each commit
git cherry-pick <COMMIT_SHA>
```

**Important:**
- Cherry-pick commits in chronological order (oldest first)
- Skip merge commits (commits with multiple parents)
- If a cherry-pick fails due to conflicts:
  1. Inform the user about the conflict
  2. Provide instructions to resolve: `git status`, fix conflicts, `git add`, `git cherry-pick --continue`
  3. Ask if they want to skip this commit: `git cherry-pick --skip`
  4. Or abort the entire operation: `git cherry-pick --abort`

### Step 7: Summary

After processing all PRs, provide a summary:
- Number of PRs processed
- Number of commits cherry-picked
- Any PRs or commits that were skipped
- Current state of the release branch

## Example Interaction

```
Processing milestone: 3.8.11
Release branch: release-3.8

Found 3 merged PRs in milestone 3.8.11:

---
PR #2303: fix CommandTimeoutExceptions for messages requiring no response
https://github.com/eclipse-ditto/ditto/pull/2303

Commits to cherry-pick:
- abc1234 fix CommandTimeoutExceptions for messages requiring no response
- def5678 also fix sending the correct control signal to enforcer child

Cherry-pick commits from PR #2303? [Yes, cherry-pick / Skip this PR]
> Yes, cherry-pick

Cherry-picking abc1234... done
Cherry-picking def5678... done

---
PR #2297: fixed still missed out hard-coded "local ask timeouts"
https://github.com/eclipse-ditto/ditto/pull/2297

Commits to cherry-pick:
- 789abcd fixed still missed out hard-coded "local ask timeouts" of 5 seconds

Cherry-pick commits from PR #2297? [Yes, cherry-pick / Skip this PR]
> Skip this PR

Skipped PR #2297.

---
Summary:
- PRs processed: 3
- PRs cherry-picked: 2
- PRs skipped: 1
- Commits cherry-picked: 3

Release branch 'release-3.8' is ready. Don't forget to push when ready:
  git push origin release-3.8
```

## Error Handling

### Merge Conflicts

If a cherry-pick results in conflicts:
```
Cherry-picking <SHA>... CONFLICT

The cherry-pick resulted in merge conflicts.
Conflicting files:
- path/to/file1.java
- path/to/file2.java

Options:
1. Resolve conflicts manually, then run: git add . && git cherry-pick --continue
2. Skip this commit: git cherry-pick --skip
3. Abort cherry-pick operation: git cherry-pick --abort

What would you like to do?
```

### PR Already Cherry-Picked

Before cherry-picking, check if the commit already exists on the release branch:
```bash
git log --oneline release-<MAJOR>.<MINOR> | grep "<COMMIT_MESSAGE_PREFIX>"
```

If found, inform the user and offer to skip.

## Notes

- Always fetch the latest state of the release branch before starting
- The skill does NOT push automatically - the user must explicitly push after reviewing
- Commits are cherry-picked with their original commit messages
- The `-x` flag is NOT used by default (no "cherry-picked from" annotation), but can be added if requested
