import {type Diff, DIFF_DELETE, DIFF_INSERT, doDiff} from './diff.js'

/**
 * Find the 'middle snake' of a diff, split the problem in two
 * and return the recursively constructed diff.
 * See Myers 1986 paper: An O(ND) Difference Algorithm and Its Variations.
 *
 * @param text1 - Old string to be diffed.
 * @param text2 - New string to be diffed.
 * @returns Array of diff tuples.
 * @internal
 */
export function bisect(text1: string, text2: string, deadline: number): Diff[] {
  // Cache the text lengths to prevent multiple calls.
  const text1Length = text1.length
  const text2Length = text2.length
  const maxD = Math.ceil((text1Length + text2Length) / 2)
  const vOffset = maxD
  const vLength = 2 * maxD
  const v1 = new Array(vLength)
  const v2 = new Array(vLength)
  // Setting all elements to -1 is faster in Chrome & Firefox than mixing
  // integers and undefined.
  for (let x = 0; x < vLength; x++) {
    v1[x] = -1
    v2[x] = -1
  }
  v1[vOffset + 1] = 0
  v2[vOffset + 1] = 0
  const delta = text1Length - text2Length
  // If the total number of characters is odd, then the front path will collide
  // with the reverse path.
  const front = delta % 2 !== 0
  // Offsets for start and end of k loop.
  // Prevents mapping of space beyond the grid.
  let k1start = 0
  let k1end = 0
  let k2start = 0
  let k2end = 0
  for (let d = 0; d < maxD; d++) {
    // Bail out if deadline is reached.
    if (Date.now() > deadline) {
      break
    }
    // Walk the front path one step.
    for (let k1 = -d + k1start; k1 <= d - k1end; k1 += 2) {
      const k1Offset = vOffset + k1
      let x1
      if (k1 === -d || (k1 !== d && v1[k1Offset - 1] < v1[k1Offset + 1])) {
        x1 = v1[k1Offset + 1]
      } else {
        x1 = v1[k1Offset - 1] + 1
      }
      let y1 = x1 - k1
      while (x1 < text1Length && y1 < text2Length && text1.charAt(x1) === text2.charAt(y1)) {
        x1++
        y1++
      }
      v1[k1Offset] = x1
      if (x1 > text1Length) {
        // Ran off the right of the graph.
        k1end += 2
      } else if (y1 > text2Length) {
        // Ran off the bottom of the graph.
        k1start += 2
      } else if (front) {
        const k2Offset = vOffset + delta - k1
        if (k2Offset >= 0 && k2Offset < vLength && v2[k2Offset] !== -1) {
          // Mirror x2 onto top-left coordinate system.
          const x2 = text1Length - v2[k2Offset]
          if (x1 >= x2) {
            // Overlap detected.
            return bisectSplit(text1, text2, x1, y1, deadline)
          }
        }
      }
    }

    // Walk the reverse path one step.
    for (let k2 = -d + k2start; k2 <= d - k2end; k2 += 2) {
      const k2Offset = vOffset + k2
      let x2
      if (k2 === -d || (k2 !== d && v2[k2Offset - 1] < v2[k2Offset + 1])) {
        x2 = v2[k2Offset + 1]
      } else {
        x2 = v2[k2Offset - 1] + 1
      }
      let y2 = x2 - k2
      while (
        x2 < text1Length &&
        y2 < text2Length &&
        text1.charAt(text1Length - x2 - 1) === text2.charAt(text2Length - y2 - 1)
      ) {
        x2++
        y2++
      }
      v2[k2Offset] = x2
      if (x2 > text1Length) {
        // Ran off the left of the graph.
        k2end += 2
      } else if (y2 > text2Length) {
        // Ran off the top of the graph.
        k2start += 2
      } else if (!front) {
        const k1Offset = vOffset + delta - k2
        if (k1Offset >= 0 && k1Offset < vLength && v1[k1Offset] !== -1) {
          const x1 = v1[k1Offset]
          const y1 = vOffset + x1 - k1Offset
          // Mirror x2 onto top-left coordinate system.
          x2 = text1Length - x2
          if (x1 >= x2) {
            // Overlap detected.
            return bisectSplit(text1, text2, x1, y1, deadline)
          }
        }
      }
    }
  }
  // Number of diffs equals number of characters, no commonality at all.
  return [
    [DIFF_DELETE, text1],
    [DIFF_INSERT, text2],
  ]
}

/**
 * Given the location of the 'middle snake', split the diff in two parts
 * and recurse.
 *
 * @param text1 - Old string to be diffed.
 * @param text2 - New string to be diffed.
 * @param x - Index of split point in text1.
 * @param y - Index of split point in text2.
 * @param deadline - Time at which to bail if not yet complete.
 * @returns Array of diff tuples.
 * @internal
 */
function bisectSplit(text1: string, text2: string, x: number, y: number, deadline: number): Diff[] {
  const text1a = text1.substring(0, x)
  const text2a = text2.substring(0, y)
  const text1b = text1.substring(x)
  const text2b = text2.substring(y)

  // Compute both diffs serially.
  const diffs = doDiff(text1a, text2a, {checkLines: false, deadline})
  const diffsb = doDiff(text1b, text2b, {checkLines: false, deadline})

  return diffs.concat(diffsb)
}
