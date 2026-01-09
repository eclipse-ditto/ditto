# .claude Directory

This directory contains context and configuration for Claude Code when working with the Eclipse Ditto codebase.

## Directory Structure

```
.claude/
├── README.md              # This file - overview of .claude structure
├── WORKTREE-WORKFLOW.md   # Git worktree workflow guide
└── context/               # General Ditto knowledge and patterns
```

## 📁 context/

General knowledge about Ditto's architecture, patterns, and development practices.

**Purpose**: Provide Claude Code with essential information to work effectively with the codebase.

**Contents**:
- `architecture.md` - System architecture, microservices, Pekko patterns
- `build-and-test.md` - Build commands, testing, local development
- `code-patterns.md` - Coding conventions, immutable models, tests
- `deployment.md` - Helm, Docker Compose, Kubernetes deployments
- `documentation-sources.md` - Guide to in-repo docs, OpenAPI, schemas, ADRs
- `feature-toggles.md` - Feature toggle system (CRITICAL for new features)
- `git-workflow.md` - Eclipse Foundation contribution requirements
- `modules.md` - Module structure, public API compatibility
- `troubleshooting.md` - Common issues and solutions

**When to use**: Always - these files provide foundational knowledge for working with Ditto.

## 📄 WORKTREE-WORKFLOW.md

Quick reference guide for using git worktrees to keep CLAUDE.md on a separate branch.

**Purpose**: Enable working on feature branches (targeting master) while having CLAUDE.md context available in a separate worktree.

**Setup**:
- `~/git/ditto/` - Main worktree on master (no CLAUDE.md)
- `~/git/ditto-claude/` - Claude worktree on claude branch (has CLAUDE.md)

**When to use**: Reference this when setting up worktrees or when unsure which directory to use.

## How Claude Code Uses This

1. **On startup**: Claude Code reads CLAUDE.md which references files in `context/`
2. **During development**: Claude uses `context/` files to understand Ditto patterns
3. **For workflow**: WORKTREE-WORKFLOW.md guides git worktree usage

## File Organization Principles

### context/ Files
- **General knowledge** applicable to all development
- **Always relevant** regardless of task
- **Foundational** understanding of Ditto
- **Comprehensive** coverage of architecture, patterns, and workflows

## Maintenance

### Adding New Context
Create new files in `context/` when:
- Adding a new major system component
- Documenting a new development pattern
- Capturing institutional knowledge
- New workflows or processes are established

## Keeping Files Updated

**When to update**:
- Architecture changes (update `context/architecture.md`)
- New build commands (update `context/build-and-test.md`)
- Process changes (update `context/git-workflow.md`)
- New deployment patterns (update `context/deployment.md`)

**How to update**:
```bash
cd ~/git/ditto-claude
git checkout claude
# Edit files in .claude/
git add .claude/
git commit -s -m "Update Claude documentation"
git push origin claude
```

## Integration with Main CLAUDE.md

The main `CLAUDE.md` file in the repository root serves as an index:
- Quick reference to all documentation
- Links to `context/` files for detailed info
- Getting started guide for new developers

Flow:
```
CLAUDE.md (entry point)
    ↓
context/ (foundational knowledge)
```

## Size Guidelines

Keep files focused and scannable:
- Context files: 100-400 lines
- Expert files: 200-500 lines
- README files: Under 200 lines

If a file grows too large, consider splitting it into focused sub-files.

## Notes for Contributors

- These files live on the `claude` branch only
- They never merge to `master`
- Use git worktrees to work with them (see WORKTREE-WORKFLOW.md)
- Keep content accurate and up-to-date
- Focus on "why" and "how", not just "what"
