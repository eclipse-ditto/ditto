// Diff
export {cleanupEfficiency, cleanupSemantic} from './diff/cleanup.js'
export {
  type Diff,
  DIFF_DELETE,
  DIFF_EQUAL,
  DIFF_INSERT,
  type DiffOptions,
  type DiffType,
  diff as makeDiff,
} from './diff/diff.js'

// Match
export {match, type MatchOptions} from './match/match.js'

// Patch
export {apply as applyPatches, type ApplyPatchOptions, type PatchResult} from './patch/apply.js'
export {type Patch} from './patch/createPatchObject.js'
export {make as makePatches, type MakePatchOptions} from './patch/make.js'
export {parse as parsePatch} from './patch/parse.js'
export {stringifyPatch, stringify as stringifyPatches} from './patch/stringify.js'

// UCS-2 utils (beta)
export {adjustIndiciesToUcs2, type AdjustmentOptions} from './utils/utf8Indices.js'

// other utils
export {xIndex} from './diff/xIndex.js'
