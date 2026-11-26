# @sanity/diff-match-patch

[![npm version](https://img.shields.io/npm/v/@sanity/diff-match-patch.svg?style=flat-square)](https://www.npmjs.com/package/@sanity/diff-match-patch)[![npm bundle size](https://img.shields.io/bundlephobia/minzip/@sanity/diff-match-patch?style=flat-square)](https://bundlephobia.com/result?p=@sanity/diff-match-patch)[![npm weekly downloads](https://img.shields.io/npm/dw/@sanity/diff-match-patch.svg?style=flat-square)](https://www.npmjs.com/package/@sanity/diff-match-patch)

A TypeScript fork of the JavaScript version of [google/diff-match-patch](https://github.com/google/diff-match-patch), that includes a few long-standing pull requests, fixing [certain bugs](#significant-changes-in-fork) and with an API more familiar to the JavaScript ecosystem.

## Installing

```
npm install --save @sanity/diff-match-patch
```

## What is diff-match-patch?

The Diff Match and Patch libraries offer robust algorithms to perform the operations required for synchronizing plain text.

1. Diff:
   - Compare two blocks of plain text and efficiently return a list of differences.
   - [Diff Demo](https://neil.fraser.name/software/diff_match_patch/demos/diff.html)
2. Match:
   - Given a search string, find its best fuzzy match in a block of plain text. Weighted for both accuracy and location.
   - [Match Demo](https://neil.fraser.name/software/diff_match_patch/demos/match.html)
3. Patch:
   - Apply a list of patches onto plain text. Use best-effort to apply patch even when the underlying text doesn't match.
   - [Patch Demo](https://neil.fraser.name/software/diff_match_patch/demos/patch.html)

Originally built in 2006 to power Google Docs, this library is now available in C++, C#, Dart, Java, JavaScript, Lua, Objective C, and Python.

## API

### Creating and applying patches

```ts
import {makePatches, applyPatches, stringifyPatches, parsePatches} from '@sanity/diff-match-patch'

// Make array of diffs in internal array format, eg tuples of `[DiffType, string]`
const patches = makePatches('from this', 'to this')

// Make unidiff-formatted (string) patch
const patch = stringifyPatches(patches)

// Apply the patch (array representation)
const [newValue] = applyPatches(patches, 'from this')

// Apply the patch (unidiff-formatted)
const [alsoNewValue] = applyPatches(parsePatch(patch), 'from this')
```

### Creating diffs

```ts
import {makeDiff} from '@sanity/diff-match-patch'

// Make an array of diff tuples, eg `[DiffType, string]`
const diff = makeDiff('from this', 'to this')
```

### Matching text

```ts
import {match} from '@sanity/diff-match-patch'

// Find position in text for the given pattern, at the approximate location given
const position = match('some text to match against', 'match', 9)
```

### Applying patches

```ts
import {applyPatches} from '@sanity/diff-match-patch'

const [newValue, results] = applyPatches(patch, 'source text')
const matches = results.filter((matched) => matched === true).length
const misses = results.length - matches
console.log('Patch applied with %d matches and %d misses', matches, misses)
console.log('New value: %s', newValue)
```

## Migrating from `diff-match-patch`

The original library that this is a fork of has a different API, meaning this fork is not a drop-in replacement. Here's a breakdown of the most common operations and their API differences:

### Creating diffs

```diff
-import {diff_match_patch as DiffMatchPatch} from 'diff-match-patch'
-const dmp = new DiffMatchPatch()
-const diffs = dmp.diff_main('from this', 'to this')
-dmp.diff_cleanupSemantic(diffs)
+import {makeDiff, cleanupSemantic} from '@sanity/diff-match-patch'
+const diffs = cleanupSemantic(makeDiff('from this', 'to this'))
```

### Make patches

```diff
-import {diff_match_patch as DiffMatchPatch} from 'diff-match-patch'
-const dmp = new DiffMatchPatch()
-const rawPatch = dmp.patch_make('from this', 'to this')
-const patch = rawPatch.map(p => p.toString()).join('')
+import {makePatches, stringifyPatches} from '@sanity/diff-match-patch'
+const patch = stringifyPatches(makePatches('from this', 'to this'))
```

### Apply patches

```diff
-import {diff_match_patch as DiffMatchPatch} from 'diff-match-patch'
-const dmp = new DiffMatchPatch()
-const patch = dmp.patch_fromText('some-text-patch')
-const [newValue] = dmp.patch_apply(patch, 'source text')
+import {applyPatches, parsePatch} from '@sanity/diff-match-patch'
+const [newValue] = applyPatches(parsePatch('some-text-patch'), 'source text')
```

## Algorithms

This library implements [Myer's diff algorithm](https://neil.fraser.name/writing/diff/myers.pdf) which is generally considered to be the best general-purpose diff. A layer of [pre-diff speedups and post-diff cleanups](https://neil.fraser.name/writing/diff/) surround the diff algorithm, improving both performance and output quality.

This library also implements a [Bitap matching algorithm](https://neil.fraser.name/writing/patch/bitap.ps) at the heart of a [flexible matching and patching strategy](https://neil.fraser.name/writing/patch/).

## Significant changes in fork

This fork has a few modifications to [the original](https://github.com/google/diff-match-patch):

- Written in TypeScript, and shipped with CommonJS and ES module builds
- API has changed - individual methods are exposed as importable functions instead of being attached to a `DiffMatchPatch` prototype. Enables better tree-shaking.
- Includes a fix for surrogate pair handling
- Includes a fix for indicies calculated using UCS-2 instead of UTF-8
- Uses modern tooling for code compilation and testing

## License

Apache-2.0
Copyright 2018 The diff-match-patch Authors
https://github.com/google/diff-match-patch
