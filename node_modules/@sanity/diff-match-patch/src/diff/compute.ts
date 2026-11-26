import {bisect} from './bisect.js'
import {
  type Diff,
  DIFF_DELETE,
  DIFF_EQUAL,
  DIFF_INSERT,
  doDiff,
  type InternalDiffOptions,
} from './diff.js'
import {findHalfMatch} from './halfMatch.js'
import {doLineModeDiff} from './lineMode.js'

/**
 * Find the differences between two texts.  Assumes that the texts do not
 * have any common prefix or suffix.
 *
 * @param text1 - Old string to be diffed.
 * @param text2 - New string to be diffed.
 * @param checklines - Speedup flag.  If false, then don't run a
 *     line-level diff first to identify the changed areas.
 *     If true, then run a faster, slightly less optimal diff.
 * @param deadline - Time when the diff should be complete by.
 * @returns Array of diff tuples.
 * @internal
 */
export function computeDiff(text1: string, text2: string, opts: InternalDiffOptions): Diff[] {
  let diffs: Diff[]

  if (!text1) {
    // Just add some text (speedup).
    return [[DIFF_INSERT, text2]]
  }

  if (!text2) {
    // Just delete some text (speedup).
    return [[DIFF_DELETE, text1]]
  }

  const longtext = text1.length > text2.length ? text1 : text2
  const shorttext = text1.length > text2.length ? text2 : text1
  const i = longtext.indexOf(shorttext)
  if (i !== -1) {
    // Shorter text is inside the longer text (speedup).
    diffs = [
      [DIFF_INSERT, longtext.substring(0, i)],
      [DIFF_EQUAL, shorttext],
      [DIFF_INSERT, longtext.substring(i + shorttext.length)],
    ]
    // Swap insertions for deletions if diff is reversed.
    if (text1.length > text2.length) {
      diffs[0][0] = DIFF_DELETE
      diffs[2][0] = DIFF_DELETE
    }
    return diffs
  }

  if (shorttext.length === 1) {
    // Single character string.
    // After the previous speedup, the character can't be an equality.
    return [
      [DIFF_DELETE, text1],
      [DIFF_INSERT, text2],
    ]
  }

  // Check to see if the problem can be split in two.
  const halfMatch = findHalfMatch(text1, text2)
  if (halfMatch) {
    // A half-match was found, sort out the return data.
    const text1A = halfMatch[0]
    const text1B = halfMatch[1]
    const text2A = halfMatch[2]
    const text2B = halfMatch[3]
    const midCommon = halfMatch[4]
    // Send both pairs off for separate processing.
    const diffsA = doDiff(text1A, text2A, opts)
    const diffsB = doDiff(text1B, text2B, opts)
    // Merge the results.
    return diffsA.concat([[DIFF_EQUAL, midCommon]], diffsB)
  }

  if (opts.checkLines && text1.length > 100 && text2.length > 100) {
    return doLineModeDiff(text1, text2, opts)
  }

  return bisect(text1, text2, opts.deadline)
}
