import {cleanupEfficiency, cleanupSemantic} from '../diff/cleanup.js'
import {type Diff, diff, DIFF_DELETE, DIFF_EQUAL, DIFF_INSERT} from '../diff/diff.js'
import {diffText1} from '../diff/diffText.js'
import {isLowSurrogate} from '../utils/surrogatePairs.js'
import {countUtf8Bytes} from '../utils/utf8Indices.js'
import {MAX_BITS} from './constants.js'
import {createPatchObject, type Patch} from './createPatchObject.js'

/**
 * Options for patch generation.
 *
 * @public
 */
export interface MakePatchOptions {
  // Chunk size for context length.
  margin: number
}

const DEFAULT_OPTS: MakePatchOptions = {
  margin: 4,
}

function getDefaultOpts(opts: Partial<MakePatchOptions> = {}): MakePatchOptions {
  return {
    ...DEFAULT_OPTS,
    ...opts,
  }
}

/**
 * Compute a list of patches to turn based on passed diffs.
 *
 * @param diffs - Array of diff tuples.
 * @param options - Options for the patch generation.
 * @returns Array of Patch objects.
 * @public
 */
export function make(diffs: Diff[], options?: Partial<MakePatchOptions>): Patch[]

/**
 * Compute a list of patches to turn textA into textB.
 *
 * @param textA - Original text.
 * @param textB - New text.
 * @param options - Options for the patch generation.
 * @returns Array of Patch objects.
 * @public
 */
export function make(textA: string, textB: string, options?: Partial<MakePatchOptions>): Patch[]

/**
 * Compute a list of patches to turn textA into textB using provided diff tuples.
 *
 * @param textA - Original text.
 * @param diffs - Diff tuples to use as base.
 * @param options - Options for the patch generation.
 * @returns Array of Patch objects.
 * @public
 */
export function make(textA: string, diffs: Diff[], options?: Partial<MakePatchOptions>): Patch[]

export function make(
  a: Diff[] | string,
  b?: Partial<MakePatchOptions> | string | Diff[],
  options?: Partial<MakePatchOptions>,
): Patch[] {
  if (typeof a === 'string' && typeof b === 'string') {
    // Method 1: textA, textB
    // Compute diffs from textA and textB.
    let diffs = diff(a, b, {checkLines: true})
    if (diffs.length > 2) {
      diffs = cleanupSemantic(diffs)
      diffs = cleanupEfficiency(diffs)
    }
    return _make(a, diffs, getDefaultOpts(options))
  }

  if (a && Array.isArray(a) && typeof b === 'undefined') {
    // Method 2: diffs
    // Compute textA from diffs.
    return _make(diffText1(a), a, getDefaultOpts(options))
  }

  if (typeof a === 'string' && b && Array.isArray(b)) {
    // Method 3: textA, diffs
    return _make(a, b, getDefaultOpts(options))
  }

  throw new Error('Unknown call format to make()')
}

function _make(textA: string, diffs: Diff[], options: MakePatchOptions): Patch[] {
  if (diffs.length === 0) {
    return [] // Get rid of the null case.
  }
  const patches: Patch[] = []

  let patch = createPatchObject(0, 0)
  let patchDiffLength = 0 // Keeping our own length var is faster in JS.
  let charCount1 = 0 // Number of characters into the textA string.
  let charCount2 = 0 // Number of characters into the textB string.
  let utf8Count1 = 0 // Number of utf-8 bytes into the textA string.
  let utf8Count2 = 0 // Number of utf-8 bytes into the textB string.

  // Start with textA (prepatchText) and apply the diffs until we arrive at
  // textB (postpatchText).  We recreate the patches one by one to determine
  // context info.
  let prepatchText = textA
  let postpatchText = textA

  for (let x = 0; x < diffs.length; x++) {
    const currentDiff = diffs[x]
    const [diffType, diffText] = currentDiff
    const diffTextLength = diffText.length
    const diffByteLength = countUtf8Bytes(diffText)

    if (!patchDiffLength && diffType !== DIFF_EQUAL) {
      // A new patch starts here.
      patch.start1 = charCount1
      patch.start2 = charCount2
      patch.utf8Start1 = utf8Count1
      patch.utf8Start2 = utf8Count2
    }

    switch (diffType) {
      case DIFF_INSERT:
        patch.diffs[patchDiffLength++] = currentDiff
        patch.length2 += diffTextLength
        patch.utf8Length2 += diffByteLength
        postpatchText =
          postpatchText.substring(0, charCount2) + diffText + postpatchText.substring(charCount2)
        break
      case DIFF_DELETE:
        patch.length1 += diffTextLength
        patch.utf8Length1 += diffByteLength
        patch.diffs[patchDiffLength++] = currentDiff
        postpatchText =
          postpatchText.substring(0, charCount2) +
          postpatchText.substring(charCount2 + diffTextLength)
        break
      case DIFF_EQUAL:
        if (diffTextLength <= 2 * options.margin && patchDiffLength && diffs.length !== x + 1) {
          // Small equality inside a patch.
          patch.diffs[patchDiffLength++] = currentDiff
          patch.length1 += diffTextLength
          patch.length2 += diffTextLength
          patch.utf8Length1 += diffByteLength
          patch.utf8Length2 += diffByteLength
        } else if (diffTextLength >= 2 * options.margin) {
          // Time for a new patch.
          if (patchDiffLength) {
            addContext(patch, prepatchText, options)
            patches.push(patch)
            patch = createPatchObject(-1, -1)
            patchDiffLength = 0
            // Unlike Unidiff, our patch lists have a rolling context.
            // http://code.google.com/p/google-diff-match-patch/wiki/Unidiff
            // Update prepatch text & pos to reflect the application of the
            // just completed patch.
            prepatchText = postpatchText
            charCount1 = charCount2
            utf8Count1 = utf8Count2
          }
        }
        break
      default:
        throw new Error('Unknown diff type')
    }

    // Update the current character count.
    if (diffType !== DIFF_INSERT) {
      charCount1 += diffTextLength
      utf8Count1 += diffByteLength
    }
    if (diffType !== DIFF_DELETE) {
      charCount2 += diffTextLength
      utf8Count2 += diffByteLength
    }
  }

  // Pick up the leftover patch if not empty.
  if (patchDiffLength) {
    addContext(patch, prepatchText, options)
    patches.push(patch)
  }

  return patches
}

/**
 * Increase the context until it is unique,
 * but don't let the pattern expand beyond MAX_BITS.
 *
 * @param patch - The patch to grow.
 * @param text - Source text.
 * @param opts
 * @internal
 */
export function addContext(patch: Patch, text: string, opts: MakePatchOptions): void {
  if (text.length === 0) {
    return
  }
  let pattern = text.substring(patch.start2, patch.start2 + patch.length1)
  let padding = 0

  // Look for the first and last matches of pattern in text.  If two different
  // matches are found, increase the pattern length.
  while (
    text.indexOf(pattern) !== text.lastIndexOf(pattern) &&
    pattern.length < MAX_BITS - opts.margin - opts.margin
  ) {
    padding += opts.margin
    pattern = text.substring(patch.start2 - padding, patch.start2 + patch.length1 + padding)
  }
  // Add one chunk for good luck.
  padding += opts.margin

  // Add the prefix.

  // Avoid splitting inside a surrogate.
  let prefixStart = patch.start2 - padding
  if (prefixStart >= 1 && isLowSurrogate(text[prefixStart])) {
    prefixStart--
  }

  const prefix = text.substring(prefixStart, patch.start2)
  if (prefix) {
    patch.diffs.unshift([DIFF_EQUAL, prefix])
  }

  const prefixLength = prefix.length
  const prefixUtf8Length = countUtf8Bytes(prefix)

  // Add the suffix.

  // Avoid splitting inside a surrogate.
  let suffixEnd = patch.start2 + patch.length1 + padding
  if (suffixEnd < text.length && isLowSurrogate(text[suffixEnd])) {
    suffixEnd++
  }

  const suffix = text.substring(patch.start2 + patch.length1, suffixEnd)
  if (suffix) {
    patch.diffs.push([DIFF_EQUAL, suffix])
  }

  const suffixLength = suffix.length
  const suffixUtf8Length = countUtf8Bytes(suffix)

  // Roll back the start points.
  patch.start1 -= prefixLength
  patch.start2 -= prefixLength
  patch.utf8Start1 -= prefixUtf8Length
  patch.utf8Start2 -= prefixUtf8Length

  // Extend the lengths.
  patch.length1 += prefixLength + suffixLength
  patch.length2 += prefixLength + suffixLength
  patch.utf8Length1 += prefixUtf8Length + suffixUtf8Length
  patch.utf8Length2 += prefixUtf8Length + suffixUtf8Length
}
