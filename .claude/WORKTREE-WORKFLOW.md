# Git Worktree Workflow - Quick Reference

## Your Setup

```
~/git/
├── ditto/              → master branch (NO CLAUDE.md)
└── ditto-claude/       → claude branch (HAS CLAUDE.md)
```

## One-Time Setup

Add Claude context files to local git exclude (prevents accidentally committing them on feature branches):

```bash
cat >> ~/git/ditto/.git/info/exclude << 'EOF'

# Claude Code context files - keep locally but don't commit to feature branches
CLAUDE.md
.claude/
EOF
```

This applies to all worktrees and ensures `CLAUDE.md` and `.claude/` won't be staged on feature branches.

## When to Use Which Directory

| Task | Directory | Why |
|------|-----------|-----|
| Using Claude Code | `ditto-claude/` | Claude sees CLAUDE.md context |
| Regular development | `ditto/` | Clean master, no extra files |
| Update CLAUDE.md | `ditto-claude/` | CLAUDE.md only on claude branch |

## Quick Commands

### Start New Feature with Claude Code

```bash
cd ~/git/ditto-claude
git checkout master && git pull
git checkout -b feature/my-feature master
git checkout claude -- CLAUDE.md .claude/   # Restore Claude context files
# Code with Claude Code here
git add .
git commit -s -m "Description"
git push -u origin feature/my-feature
# Create PR: feature/my-feature → master (no CLAUDE.md included)
```

### Regular Development (without Claude)

```bash
cd ~/git/ditto
git checkout -b feature/my-feature
# Code normally
git push -u origin feature/my-feature
```

### Update CLAUDE.md Documentation

```bash
cd ~/git/ditto-claude
git checkout claude
# Edit CLAUDE.md or .claude/context/ files
git add .
git commit -s -m "Update Claude documentation"
git push origin claude
```

### Check Where You Are

```bash
pwd                           # Show directory
git branch --show-current     # Show branch
git worktree list             # Show all worktrees
```

### Sync Latest Changes

```bash
# In either directory:
git fetch origin
git checkout master
git pull origin master
```

## Common Scenarios

### Scenario 1: Start Feature with Claude Code

```bash
cd ~/git/ditto-claude
git checkout master && git pull
git checkout -b feature/add-validation master
git checkout claude -- CLAUDE.md .claude/   # Restore Claude context files
# Work with Claude Code
git commit -s -m "Add input validation"
git push -u origin feature/add-validation
```

### Scenario 2: Quick Bug Fix (no Claude)

```bash
cd ~/git/ditto
git checkout master && git pull
git checkout -b fix/null-pointer
# Fix bug manually
git commit -s -m "Fix null pointer in gateway"
git push -u origin fix/null-pointer
```

### Scenario 3: Update Documentation for Claude

```bash
cd ~/git/ditto-claude
git checkout claude
vim .claude/context/architecture.md
git add .claude/
git commit -s -m "Update architecture documentation"
git push origin claude
```

## Key Rules

✅ **DO:**
- Create feature branches from `master`
- Work in `ditto-claude/` when using Claude Code
- Keep `claude` branch for CLAUDE.md updates only

❌ **DON'T:**
- Branch from `claude` for features (use `master`)
- Merge `claude` into `master`
- Edit CLAUDE.md on feature branches

## Troubleshooting

### "CLAUDE.md is missing after creating feature branch!"

This happens because feature branches are based on `master` which doesn't have CLAUDE.md. Restore it:

```bash
git checkout claude -- CLAUDE.md .claude/
```

Make sure you've completed the one-time setup (adding to `.git/info/exclude`) so these files won't be accidentally committed.

### "I'm in the wrong directory!"

```bash
pwd  # Check where you are
cd ~/git/ditto-claude  # or ~/git/ditto
```

### "Which branch am I on?"

```bash
git branch --show-current
git status
```

### "My feature branch has CLAUDE.md!"

You branched from `claude` instead of `master`:
```bash
git checkout master
git checkout -b feature/my-feature-fixed master
git cherry-pick <your-commits>  # Only pick your work commits
git branch -D feature/my-feature-wrong  # Delete wrong branch
```

### "I accidentally committed to claude branch"

```bash
git checkout claude
git log  # Find commit to undo
git reset --soft HEAD~1  # Undo last commit, keep changes
# Move to correct branch
```

## Worktree Management

### List All Worktrees
```bash
git worktree list
```

### Remove Worktree (if needed)
```bash
cd ~/git/ditto
git worktree remove ../ditto-claude
```

### Add Worktree Again
```bash
cd ~/git/ditto
git worktree add ../ditto-claude claude
```

## IDE Setup

### Open in Claude Code / Cursor
```bash
cd ~/git/ditto-claude
cursor .
# or
code .
```

### Open in Regular Editor
```bash
cd ~/git/ditto
code .
```

## Git Aliases (Optional)

Add to `~/.gitconfig`:

```ini
[alias]
    wt = worktree
    wtl = worktree list
    wtc = !cd ~/git/ditto-claude
    wtm = !cd ~/git/ditto
```

Usage:
```bash
git wtl           # List worktrees
git wtc && pwd    # Jump to claude worktree
git wtm && pwd    # Jump to master worktree
```

## Shell Aliases (Optional)

Add to `~/.zshrc` or `~/.bashrc`:

```bash
alias cdclaude='cd ~/git/ditto-claude'
alias cdmaster='cd ~/git/ditto'
```

## Remember

🎯 **Goal**: Keep CLAUDE.md on `claude` branch, never merge to `master`

🔧 **Method**: Feature branches from `master` → PRs to `master` (clean)

📁 **Location**: Use `ditto-claude/` when you want Claude to see context

---

**Quick Check Before PR:**
```bash
git log master..HEAD --oneline    # Review commits
git diff master...HEAD -- CLAUDE.md .claude/  # Should show nothing!
```

If CLAUDE.md appears in diff, you branched from wrong place!
