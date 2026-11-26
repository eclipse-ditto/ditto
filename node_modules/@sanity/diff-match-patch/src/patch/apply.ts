/* eslint-disable max-depth */
/* eslint-disable max-statements */
import {cleanupSemanticLossless} from '../diff/cleanup.js'
import {diff, DIFF_DELETE, DIFF_EQUAL, DIFF_INSERT} from '../diff/diff.js'
import {diffText1, diffText2} from '../diff/diffText.js'
import {levenshtein} from '../diff/levenshtein.js'
import {xIndex} from '../diff/xIndex.js'
import {match} from '../match/match.js'
import {adjustIndiciesToUcs2} from '../utils/utf8Indices.js'
import {addPadding} from './addPadding.js'
import {DEFAULT_MARGIN, MAX_BITS} from './constants.js'
import {type Patch} from './createPatchObject.js'
import {splitMax} from './splitMax.js'

/**
 * Options for applying a patch.
 *
 * @public
 */
export interface ApplyPatchOptions {
  /**
   * Chunk size for context length
   */
  margin: number

  /**
   * When deleting a large block of text (over ~64 characters),
   * how close do the contents have to be to match the expected contents.
   * (0.0 = perfection, 1.0 = very loose).
   */
  deleteThreshold: number

  /**
   * When converting indices between UTF-8 and UCS-2, certain scenarios can occur
   * where we go beyond the target offset. This can happen in particular with
   * surrogate pairs/high codepoints, when the base string we are applying the
   * patch to does not fully match the one that was used to generate the patch.
   * Defaults to `false`.
   */
  allowExceedingIndices: boolean
}

/**
 * Result of a patch application operation.
 * Index 0 contains the new text
 * Index 1 contains an array of booleans indicating which patches were applied
 *
 * @public
 */
export type PatchResult = [string, boolean[]]

/**
 * Merge a set of patches onto the text. Returns patched text, as well as a
 * list of true/false values indicating which patches were applied.
 *
 * @param patches - Array of Patch objects.
 * @param text - Old text.
 * @param opts - Optional settings for the patch application.
 * @returns Two element Array, containing the new text and an array of boolean values.
 * @public
 */
export function apply(
  patches: Patch[],
  originalText: string,
  opts: Partial<ApplyPatchOptions> = {},
): PatchResult {
  if (typeof patches === 'string') {
    throw new Error('Patches must be an array - pass the patch to `parsePatch()` first')
  }

  let text = originalText
  if (patches.length === 0) {
    return [text, []]
  }

  // Note: adjustment also deep-copies patches so that no changes are made to the originals.
  const parsed = adjustIndiciesToUcs2(patches, text, {
    allowExceedingIndices: opts.allowExceedingIndices,
  })

  const margin = opts.margin || DEFAULT_MARGIN
  const deleteThreshold = opts.deleteThreshold || 0.4

  const nullPadding = addPadding(parsed, margin)
  text = nullPadding + text + nullPadding

  splitMax(parsed, margin)
  // delta keeps track of the offset between the expected and actual location
  // of the previous patch.  If there are patches expected at positions 10 and
  // 20, but the first patch was found at 12, delta is 2 and the second patch
  // has an effective expected position of 22.
  let delta = 0
  const results: boolean[] = []
  for (let x = 0; x < parsed.length; x++) {
    const expectedLoc = parsed[x].start2 + delta
    const text1 = diffText1(parsed[x].diffs)
    let startLoc
    let endLoc = -1
    if (text1.length > MAX_BITS) {
      // patch_splitMax will only provide an oversized pattern in the case of
      // a monster delete.
      startLoc = match(text, text1.substring(0, MAX_BITS), expectedLoc)
      if (startLoc !== -1) {
        endLoc = match(
          text,
          text1.substring(text1.length - MAX_BITS),
          expectedLoc + text1.length - MAX_BITS,
        )
        if (endLoc === -1 || startLoc >= endLoc) {
          // Can't find valid trailing context.  Drop this patch.
          startLoc = -1
        }
      }
    } else {
      startLoc = match(text, text1, expectedLoc)
    }
    if (startLoc === -1) {
      // No match found.  :(
      results[x] = false
      // Subtract the delta for this failed patch from subsequent patches.
      delta -= parsed[x].length2 - parsed[x].length1
    } else {
      // Found a match.  :)
      results[x] = true
      delta = startLoc - expectedLoc
      let text2
      if (endLoc === -1) {
        text2 = text.substring(startLoc, startLoc + text1.length)
      } else {
        text2 = text.substring(startLoc, endLoc + MAX_BITS)
      }
      if (text1 === text2) {
        // Perfect match, just shove the replacement text in.
        text =
          text.substring(0, startLoc) +
          diffText2(parsed[x].diffs) +
          text.substring(startLoc + text1.length)
      } else {
        // Imperfect match. Run a diff to get a framework of equivalent indices.
        let diffs = diff(text1, text2, {checkLines: false})
        if (text1.length > MAX_BITS && levenshtein(diffs) / text1.length > deleteThreshold) {
          // The end points match, but the content is unacceptably bad.
          results[x] = false
        } else {
          diffs = cleanupSemanticLossless(diffs)
          let index1 = 0
          let index2 = 0
          for (let y = 0; y < parsed[x].diffs.length; y++) {
            const mod = parsed[x].diffs[y]
            if (mod[0] !== DIFF_EQUAL) {
              index2 = xIndex(diffs, index1)
            }
            if (mod[0] === DIFF_INSERT) {
              // Insertion
              text =
                text.substring(0, startLoc + index2) + mod[1] + text.substring(startLoc + index2)
            } else if (mod[0] === DIFF_DELETE) {
              // Deletion
              text =
                text.substring(0, startLoc + index2) +
                text.substring(startLoc + xIndex(diffs, index1 + mod[1].length))
            }
            if (mod[0] !== DIFF_DELETE) {
              index1 += mod[1].length
            }
          }
        }
      }
    }
  }
  // Strip the padding off.
  text = text.substring(nullPadding.length, text.length - nullPadding.length)
  return [text, results]
}
