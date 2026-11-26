import {isHighSurrogate, isLowSurrogate} from '../utils/surrogatePairs.js'
import {cleanupMerge} from './cleanup.js'
import {getCommonPrefix} from './commonPrefix.js'
import {getCommonSuffix} from './commonSuffix.js'
import {computeDiff} from './compute.js'

/**
 * Diff type for deleted text.
 *
 * @public
 */
export const DIFF_DELETE = -1

/**
 * Diff type for inserted text.
 *
 * @public
 */
export const DIFF_INSERT = 1

/**
 * Diff type for text that is equal.
 *
 * @public
 */
export const DIFF_EQUAL = 0

/**
 * The three different types of changes possible in a diff:
 * - `DIFF_DELETE`: a deletion of text
 * - `DIFF_INSERT`: an insertion of text
 * - `DIFF_EQUAL` : an equal text
 *
 * @public
 */
export type DiffType = typeof DIFF_DELETE | typeof DIFF_INSERT | typeof DIFF_EQUAL

/**
 * The data structure representing a diff is an array of tuples:
 * [[DIFF_DELETE, 'Hello'], [DIFF_INSERT, 'Goodbye'], [DIFF_EQUAL, ' world.']]
 * which means: delete 'Hello', add 'Goodbye' and keep ' world.'
 *
 * @public
 */
export type Diff = [DiffType, string]

/**
 * Options for generating a diff.
 *
 * @public
 */
export interface DiffOptions {
  checkLines: boolean
  timeout: number
}

/**
 * @internal
 */
export interface InternalDiffOptions {
  checkLines: boolean

  /**
   * Time when the diff should be complete by.
   */
  deadline: number
}

/**
 * Find the differences between two texts.  Simplifies the problem by stripping
 * any common prefix or suffix off the texts before diffing.
 *
 * @param textA - Old string to be diffed.
 * @param textA - New string to be diffed.
 * @returns Array of diff tuples.
 * @public
 */
export function diff(
  textA: null | string,
  textB: null | string,
  opts?: Partial<DiffOptions>,
): Diff[] {
  // Check for null inputs.
  if (textA === null || textB === null) {
    throw new Error('Null input. (diff)')
  }

  const diffs = doDiff(textA, textB, createInternalOpts(opts || {}))
  adjustDiffForSurrogatePairs(diffs)
  return diffs
}

/**
 * Find the differences between two texts.  Simplifies the problem by stripping
 * any common prefix or suffix off the texts before diffing.
 *
 * @param textA - Old string to be diffed.
 * @param textB - New string to be diffed.
 * @returns Array of diff tuples.
 * @internal
 */
export function doDiff(textA: string, textB: string, options: InternalDiffOptions): Diff[] {
  // Don't reassign fn params
  let text1 = textA
  let text2 = textB

  // Check for equality (speedup).
  if (text1 === text2) {
    return text1 ? [[DIFF_EQUAL, text1]] : []
  }

  // Trim off common prefix (speedup).
  let commonlength = getCommonPrefix(text1, text2)
  const commonprefix = text1.substring(0, commonlength)
  text1 = text1.substring(commonlength)
  text2 = text2.substring(commonlength)

  // Trim off common suffix (speedup).
  commonlength = getCommonSuffix(text1, text2)
  const commonsuffix = text1.substring(text1.length - commonlength)
  text1 = text1.substring(0, text1.length - commonlength)
  text2 = text2.substring(0, text2.length - commonlength)

  // Compute the diff on the middle block.
  let diffs = computeDiff(text1, text2, options)

  // Restore the prefix and suffix.
  if (commonprefix) {
    diffs.unshift([DIFF_EQUAL, commonprefix])
  }
  if (commonsuffix) {
    diffs.push([DIFF_EQUAL, commonsuffix])
  }
  diffs = cleanupMerge(diffs)
  return diffs
}

function createDeadLine(timeout: undefined | number): number {
  let t = 1
  if (typeof timeout !== 'undefined') {
    t = timeout <= 0 ? Number.MAX_VALUE : timeout
  }
  return Date.now() + t * 1000
}

function createInternalOpts(opts: Partial<DiffOptions>): InternalDiffOptions {
  return {
    checkLines: true,
    deadline: createDeadLine(opts.timeout || 1.0),
    ...opts,
  }
}

function combineChar(data: string, char: string, dir: 1 | -1) {
  return dir === 1 ? data + char : char + data
}

/**
 * Splits out a character in a given direction.
 */
function splitChar(data: string, dir: 1 | -1): [string, string] {
  return dir === 1
    ? [data.substring(0, data.length - 1), data[data.length - 1]]
    : [data.substring(1), data[0]]
}

/**
 * Checks if two entries of the diff has the same character in the same "direction".
 */
function hasSharedChar(diffs: Diff[], i: number, j: number, dir: 1 | -1): boolean {
  return dir === 1
    ? diffs[i][1][diffs[i][1].length - 1] === diffs[j][1][diffs[j][1].length - 1]
    : diffs[i][1][0] === diffs[j][1][0]
}

/**
 * Takes in a position of an EQUAL diff-type and attempts to "deisolate" the character for a given direction.
 * By this we mean that we attempt to either "shift" it to the later diffs, or bring another character next into this one.
 *
 * It's easier to understand with an example:
 *   [INSERT a, DELETE b, EQUAL cde, INSERT f, DELETE g]
 * shifting this forward will produce
 *   [INSERT a, DELETE b, EQUAL cd, INSERT ef, DELETE eg]
 *
 * This behavior is useful when `e` is actually a high surrogate character.
 *
 * Shifting it backwards produces
 *   [INSERT ac, DELETE bc, EQUAL cde, INSERT f, DELETE g]
 * which is useful when `c` is a low surrogate character.
 *
 * Note that these diffs are 100% semantically equal.
 *
 * If there's not a matching INSERT/DELETE then it's forced to insert an additional entry:
 *   [EQUAL abc, INSERT d, EQUAL e]
 * shifted forward becomes:
 *   [EQUAL ab, INSERT cd, DELETE c, EQUAL e]
 *
 * If the INSERT and DELETE ends with the same character it will instead deisolate it by
 * bring that charcter into _this_ equal:
 *   [EQUAL abc, INSERT de, DELETE df]
 * shifted forward actually becomes
 *   [EQUAL abcd, INSERT e, DELETE f]
 *
 * The original diff here is typically never produced by the diff algorithm directly,
 * but they occur when we isolate characters in other places.
 */
function deisolateChar(diffs: Diff[], i: number, dir: 1 | -1) {
  const inv = dir === 1 ? -1 : 1
  let insertIdx: null | number = null
  let deleteIdx: null | number = null

  let j = i + dir
  for (; j >= 0 && j < diffs.length && (insertIdx === null || deleteIdx === null); j += dir) {
    const [op, text] = diffs[j]
    if (text.length === 0) {
      continue
    }

    if (op === DIFF_INSERT) {
      if (insertIdx === null) {
        insertIdx = j
      }
      continue
    } else if (op === DIFF_DELETE) {
      if (deleteIdx === null) {
        deleteIdx = j
      }
      continue
    } else if (op === DIFF_EQUAL) {
      if (insertIdx === null && deleteIdx === null) {
        // This means that there was two consecutive EQUAL. Kinda weird, but easy to handle.
        const [rest, char] = splitChar(diffs[i][1], dir)
        diffs[i][1] = rest
        diffs[j][1] = combineChar(diffs[j][1], char, inv)
        return
      }
      break
    }
  }

  if (insertIdx !== null && deleteIdx !== null && hasSharedChar(diffs, insertIdx, deleteIdx, dir)) {
    // Special case.
    const [insertText, insertChar] = splitChar(diffs[insertIdx][1], inv)
    const [deleteText] = splitChar(diffs[deleteIdx][1], inv)
    diffs[insertIdx][1] = insertText
    diffs[deleteIdx][1] = deleteText
    diffs[i][1] = combineChar(diffs[i][1], insertChar, dir)
    return
  }

  const [text, char] = splitChar(diffs[i][1], dir)
  diffs[i][1] = text

  if (insertIdx === null) {
    diffs.splice(j, 0, [DIFF_INSERT, char])

    // We need to adjust deleteIdx here since it's been shifted
    if (deleteIdx !== null && deleteIdx >= j) deleteIdx++
  } else {
    diffs[insertIdx][1] = combineChar(diffs[insertIdx][1], char, inv)
  }

  if (deleteIdx === null) {
    diffs.splice(j, 0, [DIFF_DELETE, char])
  } else {
    diffs[deleteIdx][1] = combineChar(diffs[deleteIdx][1], char, inv)
  }
}

function adjustDiffForSurrogatePairs(diffs: Diff[]) {
  // Go over each pair of diffs and see if there was a split at a surrogate pair
  for (let i = 0; i < diffs.length; i++) {
    const [diffType, diffText] = diffs[i]

    if (diffText.length === 0) continue

    const firstChar = diffText[0]
    const lastChar = diffText[diffText.length - 1]

    if (isHighSurrogate(lastChar) && diffType === DIFF_EQUAL) {
      deisolateChar(diffs, i, 1)
    }

    if (isLowSurrogate(firstChar) && diffType === DIFF_EQUAL) {
      deisolateChar(diffs, i, -1)
    }
  }

  for (let i = 0; i < diffs.length; i++) {
    // Remove any empty diffs
    if (diffs[i][1].length === 0) {
      diffs.splice(i, 1)
    }
  }
}
