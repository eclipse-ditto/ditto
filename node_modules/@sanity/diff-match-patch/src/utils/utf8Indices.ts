import {cloneDiff} from '../diff/clone.js'
import {type Patch} from '../index.js'

/**
 * Counts the number of bytes in a string.
 * Note that while this approach may seem heavy-handed, it is actually
 * significantly faster than both `new Blob([str]).size` and `TextEncoder`.
 *
 * @param str - String to count
 * @returns Number of bytes
 */
export function countUtf8Bytes(str: string): number {
  let bytes = 0
  for (let i = 0; i < str.length; i++) {
    const codePoint = str.codePointAt(i)
    if (typeof codePoint === 'undefined') {
      throw new Error('Failed to get codepoint')
    }
    bytes += utf8len(codePoint)
  }
  return bytes
}

/**
 * Options for the index adjustment operations.
 *
 * @public
 */
export interface AdjustmentOptions {
  /**
   * When converting indices between UTF-8 and UCS-2, certain scenarios can occur
   * where we go beyond the target offset. This can happen in particular with
   * surrogate pairs/high codepoints, when the base string we are applying the
   * patch to does not fully match the one that was used to generate the patch.
   * Defaults to `false`.
   */
  allowExceedingIndices?: boolean
}

/**
 * Takes a `patches` array as produced by diff-match-patch and adjusts the
 * `start1` and `start2` properties so that they refer to UCS-2 index instead
 * of a UTF-8 index.
 *
 * @param patches - The patches to adjust
 * @param base - The base string to use for counting bytes
 * @param options - Options for the adjustment of indices
 * @returns A new array of patches with adjusted indicies
 * @beta
 */
export function adjustIndiciesToUcs2(
  patches: Patch[],
  base: string,
  options: AdjustmentOptions = {},
): Patch[] {
  let byteOffset = 0
  let idx = 0 // index into the input.

  function advanceTo(target: number) {
    for (; byteOffset < target; ) {
      const codePoint = base.codePointAt(idx)
      if (typeof codePoint === 'undefined') {
        // Reached the end of the base string - the indicies won't be correct,
        // but we also cannot advance any further to find a closer index.
        return idx
      }

      byteOffset += utf8len(codePoint)

      // This is encoded as a surrogate pair.
      if (codePoint > 0xffff) {
        idx += 2
      } else {
        idx += 1
      }
    }

    if (!options.allowExceedingIndices && byteOffset !== target) {
      throw new Error('Failed to determine byte offset')
    }

    return idx
  }

  const adjusted: Patch[] = []
  for (const patch of patches) {
    adjusted.push({
      diffs: patch.diffs.map((diff) => cloneDiff(diff)),
      start1: advanceTo(patch.start1),
      start2: advanceTo(patch.start2),
      utf8Start1: patch.utf8Start1,
      utf8Start2: patch.utf8Start2,
      length1: patch.length1,
      length2: patch.length2,
      utf8Length1: patch.utf8Length1,
      utf8Length2: patch.utf8Length2,
    })
  }

  return adjusted
}

function utf8len(codePoint: number): 1 | 2 | 3 | 4 {
  // See table at https://en.wikipedia.org/wiki/UTF-8
  if (codePoint <= 0x007f) return 1
  if (codePoint <= 0x07ff) return 2
  if (codePoint <= 0xffff) return 3
  return 4
}
