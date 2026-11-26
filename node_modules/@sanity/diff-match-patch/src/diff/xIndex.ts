import {type Diff, DIFF_DELETE, DIFF_INSERT} from './diff.js'

/**
 * loc is a location in textA, compute and return the equivalent location in
 * textB.
 * e.g. 'The cat' vs 'The big cat', 1->1, 5->8
 *
 * @param diffs - Array of diff tuples.
 * @param location - Location within textA.
 * @returns Location within textB.
 * @public
 */
export function xIndex(diffs: Diff[], location: number): number {
  let chars1 = 0
  let chars2 = 0
  let lastChars1 = 0
  let lastChars2 = 0
  let x
  for (x = 0; x < diffs.length; x++) {
    if (diffs[x][0] !== DIFF_INSERT) {
      // Equality or deletion.
      chars1 += diffs[x][1].length
    }
    if (diffs[x][0] !== DIFF_DELETE) {
      // Equality or insertion.
      chars2 += diffs[x][1].length
    }
    if (chars1 > location) {
      // Overshot the location.
      break
    }
    lastChars1 = chars1
    lastChars2 = chars2
  }
  // Was the location was deleted?
  if (diffs.length !== x && diffs[x][0] === DIFF_DELETE) {
    return lastChars2
  }
  // Add the remaining character length.
  return lastChars2 + (location - lastChars1)
}
