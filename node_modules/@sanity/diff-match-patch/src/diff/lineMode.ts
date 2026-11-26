import {charsToLines} from './charsToLines.js'
import {cleanupSemantic} from './cleanup.js'
import {
  type Diff,
  DIFF_DELETE,
  DIFF_EQUAL,
  DIFF_INSERT,
  doDiff,
  type InternalDiffOptions,
} from './diff.js'
import {linesToChars} from './linesToChars.js'

/**
 * Do a quick line-level diff on both strings, then rediff the parts for
 * greater accuracy.
 * This speedup can produce non-minimal diffs.
 *
 * @param textA - Old string to be diffed.
 * @param textB - New string to be diffed.
 * @param options - Options for the differ.
 * @returns Array of diff tuples.
 * @internal
 */
export function doLineModeDiff(textA: string, textB: string, opts: InternalDiffOptions): Diff[] {
  // Don't reassign fn params
  let text1 = textA
  let text2 = textB

  // Scan the text on a line-by-line basis first.
  const a = linesToChars(text1, text2)
  text1 = a.chars1
  text2 = a.chars2
  const linearray = a.lineArray

  let diffs = doDiff(text1, text2, {
    checkLines: false,
    deadline: opts.deadline,
  })

  // Convert the diff back to original text.
  charsToLines(diffs, linearray)
  // Eliminate freak matches (e.g. blank lines)
  diffs = cleanupSemantic(diffs)

  // Rediff any replacement blocks, this time character-by-character.
  // Add a dummy entry at the end.
  diffs.push([DIFF_EQUAL, ''])
  let pointer = 0
  let countDelete = 0
  let countInsert = 0
  let textDelete = ''
  let textInsert = ''
  while (pointer < diffs.length) {
    switch (diffs[pointer][0]) {
      case DIFF_INSERT:
        countInsert++
        textInsert += diffs[pointer][1]
        break
      case DIFF_DELETE:
        countDelete++
        textDelete += diffs[pointer][1]
        break
      case DIFF_EQUAL:
        // Upon reaching an equality, check for prior redundancies.
        if (countDelete >= 1 && countInsert >= 1) {
          // Delete the offending records and add the merged ones.
          diffs.splice(pointer - countDelete - countInsert, countDelete + countInsert)
          pointer = pointer - countDelete - countInsert
          const aa = doDiff(textDelete, textInsert, {
            checkLines: false,
            deadline: opts.deadline,
          })
          for (let j = aa.length - 1; j >= 0; j--) {
            diffs.splice(pointer, 0, aa[j])
          }
          pointer += aa.length
        }
        countInsert = 0
        countDelete = 0
        textDelete = ''
        textInsert = ''
        break
      default:
        throw new Error('Unknown diff operation.')
    }
    pointer++
  }
  diffs.pop() // Remove the dummy entry at the end.

  return diffs
}
