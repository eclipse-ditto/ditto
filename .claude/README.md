# .claude

Context for AI coding agents working on Ditto. `AGENTS.md` in the repo root is the entry
point and holds the cross-cutting gotchas; everything here is loaded only when relevant.

| Path          | Contents                                                       | Loaded                                                                                 |
|---------------|----------------------------------------------------------------|----------------------------------------------------------------------------------------|
| `rules/`      | Detail behind the gotchas, one topic per file                  | Claude Code: automatically, when a file matching the rule's `paths:` is read or edited |
| `context/`    | Architecture, build, git workflow, deployment, troubleshooting | On demand, via "Where to look" in `AGENTS.md`                                          |
| `deep-dives/` | End-to-end explanations of flows and features                  | On demand, via `deep-dives/README.md`                                                  |
| `concepts/`   | Design documents for larger features                           | On demand                                                                              |
| `skills/`     | `release-notes`, `cherry-pick-release`, `update-agent-context` | Claude Code: as `/release-notes <milestone>` etc.                                      |

## Maintaining

- Write down what an agent can't learn from the code or wouldn't think to look up. Anything
  derivable (file lists, module trees, class names it can grep) goes stale and costs context.
- Keep `AGENTS.md` short; every line there is loaded into every session. Topic detail goes
  into a path-scoped rule, a reference file or a skill.
- State rules plainly. Emphasis (`CRITICAL`, `MUST`, capitals) makes current models
  over-apply a rule rather than follow it better; if a rule keeps being missed, make it a
  check in the build or a hook.
- When code changes invalidate a file here, update it in the same PR.
- `.claude/settings.local.json` and `CLAUDE.local.md` are for personal settings and stay
  untracked.
