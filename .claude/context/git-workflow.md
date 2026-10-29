# Git workflow & contribution

Ditto is an Eclipse Foundation project; `CONTRIBUTING.md` in the repo root is authoritative.
`.claude/deep-dives/Ditto-OSS-Process.md` covers the committer side (releases, CI, IP
process).

## Before the first commit

- The author needs an Eclipse account with the signed
  [Eclipse Contributor Agreement](https://www.eclipse.org/legal/ECA.php), and the GitHub
  username added to it. The ECA check on the PR fails otherwise.
- Every commit is signed off: `git commit -s` (the `Signed-off-by` email must match the
  Eclipse account).

## Flow

1. For features and larger changes, open (or find) a GitHub issue and agree on the approach
   there first. Small, directly fixable bugfixes and maintenance changes (docs, build,
   tooling) can skip the issue — the PR description then explains the bug and its impact.
2. Branch from `master`: `feature/<desc>`, `bugfix/<desc>` or `refactor/<desc>`.
3. Open a draft PR early; mark it ready for review when done.
4. One approval from a committer (or an active contributor) is needed. Without objections, a
   PR can be merged after four weeks without review.
5. Keep the PR focused and squash it into a single commit before merge
   (`git reset --soft origin/master && git commit -s` works without an interactive rebase).

## Commit message

```
Add namespace-scoped entries to policies

Explain what changed and why; the diff shows how. Wrap at 72 characters.

Fixes #2325
```

Imperative subject (≤ 50 characters), blank line, body, issue reference (`Fixes #…`,
`Relates to #…`). AI-assisted commits carry a `Co-Authored-By:` trailer.

## Releases

Minor releases roughly quarterly, bugfix releases as needed from `release-<major>.<minor>`
branches (fixes are cherry-picked from `master`). The Helm chart is versioned and released
independently of the application. The `release-notes` and `cherry-pick-release` skills in
`.claude/skills/` automate the routine parts.
