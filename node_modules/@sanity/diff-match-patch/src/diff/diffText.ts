import {type Diff, DIFF_DELETE, DIFF_INSERT} from './diff.js'

/**
 * Compute and return the source text (all equalities and deletions).
 *
 * @param diffs - Array of diff tuples.
 * @returns Source text.
 * @private
 */
export function diffText1(diffs: Diff[]): string {
  const text: string[] = []
  for (let x = 0; x < diffs.length; x++) {
    if (diffs[x][0] !== DIFF_INSERT) {
      text[x] = diffs[x][1]
    }
  }
  return text.join('')
}

/**
 * Compute and return the destination text (all equalities and insertions).
 *
 * @param diffs - Array of diff tuples.
 * @returns Destination text.
 * @private
 */
export function diffText2(diffs: Diff[]): string {
  const text: string[] = []
  for (let x = 0; x < diffs.length; x++) {
    if (diffs[x][0] !== DIFF_DELETE) {
      text[x] = diffs[x][1]
    }
  }
  return text.join('')
}
