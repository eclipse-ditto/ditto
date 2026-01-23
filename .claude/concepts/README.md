# Concepts

This directory contains design documents for proposed Ditto features and architectural concepts.

## Purpose

Store comprehensive design documents for:
- New feature proposals
- Architectural decisions
- API designs
- Integration patterns

## Naming Convention

Format: `<feature-name>-design.md`

Examples:
- `timeseries-design.md`
- `policy-import-additions-design.md`

## Contents

Each design document typically includes:
- Overview and goals
- Architecture diagrams
- API specifications
- Data models
- Configuration
- Implementation phases
- Open questions

## Current Documents

| Document | Description | Status |
|----------|-------------|--------|
| [policy-import-additions-design.md](policy-import-additions-design.md) | Enhance policy imports with `entriesAdditions` for subject/resource customization | Concept |
| [timeseries-design.md](timeseries-design.md) | Timeseries Facade Service for external TS database integration | Draft |

## Workflow

1. Create design document when exploring a significant new feature
2. Iterate on design based on discussion
3. Reference in GitHub issue for community feedback
4. Update status as implementation progresses
