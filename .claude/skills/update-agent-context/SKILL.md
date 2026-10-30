---
name: update-agent-context
description: Check AGENTS.md and the .claude rules, context, deep-dives and concepts against a code change and update what the change made stale. Use before opening a PR that changes behavior, config, module layout or conventions, or when asked to review the agent context.
argument-hint: "[base-ref, default origin/master]"
allowed-tools: Read, Grep, Glob, Edit, Bash(git diff:*), Bash(git log:*), Bash(rg:*), Bash(fd:*)
disable-model-invocation: true
---

# Update agent context

Bring `AGENTS.md` and `.claude/` in line with a code change, so the next agent doesn't learn
something that is no longer true.

## 1. Collect the change

Base ref: `$ARGUMENTS` if given, otherwise `origin/master`.

```bash
git diff --name-only <base>...HEAD; git diff --name-only HEAD   # committed + uncommitted
git diff <base>...HEAD -- <path>                                 # details where needed
```

Skip changes that only touch `.claude/` or `AGENTS.md` themselves.

## 2. Find the files that describe the changed code

- **Rules**: every `.claude/rules/*.md` whose `paths:` frontmatter matches a changed file.
- **Routing**: the "Where to look" list in `AGENTS.md` names the context file or deep dive
  for each area.
- **References**: `rg -l` for the changed class names, config keys, module names, file paths
  and HTTP paths in `AGENTS.md` and `.claude/` (excluding `.claude/skills/`).
- **Gotchas**: does the change add or remove a Java 8 module, a feature toggle, a config key,
  a Helm wiring step, or a public API convention? Those live in `AGENTS.md` and the rules.

## 3. Update

For each file found, read the relevant section next to the new code and fix what is now
wrong: names, paths, steps, defaults, which module does what. Then decide whether the change
introduces something a future agent needs and can't find by reading the code — a new step
that is easy to miss, a non-obvious constraint, a new area worth routing to. Only that gets
added.

Keep the files the way they are meant to be:

- `AGENTS.md` stays short — every line is loaded in every session. New detail goes into a
  rule (and a new glob into its `paths:` when it covers new code), a context file or a deep
  dive.
- Rules and context files stay under ~300 words; split by topic rather than growing one.
- No change logs or "as of version X" notes — describe the current state.
- Plain wording, no `CRITICAL`/`MUST` emphasis; markdown tables with aligned columns.
- A new deep dive or concept gets a row in its directory's `README.md`; a new routing target
  gets a line in `AGENTS.md` "Where to look".

## 4. Stale references

Check the whole context for references the code no longer has, not only the ones near the
change:

```bash
# file paths that no longer exist (only paths starting with an existing top-level dir)
rg -o --no-filename '`/?[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)+/?`' AGENTS.md .claude \
  | tr -d '`' | sed 's|^/||' | grep -v '[{*…]' | grep -vF '...' | sort -u \
  | while read -r p; do [ -d "${p%%/*}" ] && [ ! -e "$p" ] && echo "missing path: $p"; done

# CamelCase names without a Java/Scala type of that name (JDK/Pekko types are expected noise)
comm -23 \
  <(rg -o --no-filename '`[A-Z][a-z]+[A-Z][A-Za-z0-9]+`' AGENTS.md .claude/rules .claude/context .claude/deep-dives \
      | tr -d '`' | sort -u) \
  <(fd -e java -e scala -E target -E node_modules . | xargs -n1 basename | sed 's/\.[a-z]*$//' | sort -u)
```

Fix the hits that are stale, using the current name from the code.

## 5. Report

List each file changed with a one-line reason, then every inconsistency between the context
and the code that you didn't fix, and why (it needs a decision, or the code looks wrong rather
than the doc). Leave committing to the user.
