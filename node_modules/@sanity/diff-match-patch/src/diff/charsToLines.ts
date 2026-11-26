import {type Diff} from './diff.js'

/**
 * Rehydrate the text in a diff from a string of line hashes to real lines of text.
 *
 * @param diffs - Array of diff tuples.
 * @param lineArray - Array of unique strings.
 * @internal
 */
export function charsToLines(diffs: Diff[], lineArray: string[]): void {
  for (let x = 0; x < diffs.length; x++) {
    const chars = diffs[x][1]
    const text: string[] = []
    for (let y = 0; y < chars.length; y++) {
      text[y] = lineArray[chars.charCodeAt(y)]
    }
    diffs[x][1] = text.join('')
  }
}
