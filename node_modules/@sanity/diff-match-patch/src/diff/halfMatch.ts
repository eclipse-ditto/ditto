import {getCommonPrefix} from './commonPrefix.js'
import {getCommonSuffix} from './commonSuffix.js'

type HalfMatch = [string, string, string, string, string]

/**
 * Does a slice of shorttext exist within longtext such that the slice
 * is at least half the length of longtext?
 *
 * @param longtext - Longer string.
 * @param shorttext - Shorter string.
 * @param i - Start index of quarter length slice within longtext.
 * @returns Five element Array, containing the prefix of
 *     longtext, the suffix of longtext, the prefix of shorttext, the suffix
 *     of shorttext and the common middle.  Or null if there was no match.
 * @internal
 */
export function findHalfMatch(text1: string, text2: string, timeout = 1): null | HalfMatch {
  if (timeout <= 0) {
    // Don't risk returning a non-optimal diff if we have unlimited time.
    return null
  }

  const longText = text1.length > text2.length ? text1 : text2
  const shortText = text1.length > text2.length ? text2 : text1
  if (longText.length < 4 || shortText.length * 2 < longText.length) {
    return null // Pointless.
  }

  // First check if the second quarter is the seed for a half-match.
  const halfMatch1 = halfMatchI(longText, shortText, Math.ceil(longText.length / 4))
  // Check again based on the third quarter.
  const halfMatch2 = halfMatchI(longText, shortText, Math.ceil(longText.length / 2))

  let halfMatch
  if (halfMatch1 && halfMatch2) {
    // Both matched.  Select the longest.
    halfMatch = halfMatch1[4].length > halfMatch2[4].length ? halfMatch1 : halfMatch2
  } else if (!halfMatch1 && !halfMatch2) {
    return null
  } else if (!halfMatch2) {
    halfMatch = halfMatch1
  } else if (!halfMatch1) {
    halfMatch = halfMatch2
  }

  if (!halfMatch) {
    throw new Error('Unable to find a half match.')
  }

  // A half-match was found, sort out the return data.
  let text1A: string
  let text1B: string
  let text2A: string
  let text2B: string

  if (text1.length > text2.length) {
    text1A = halfMatch[0]
    text1B = halfMatch[1]
    text2A = halfMatch[2]
    text2B = halfMatch[3]
  } else {
    text2A = halfMatch[0]
    text2B = halfMatch[1]
    text1A = halfMatch[2]
    text1B = halfMatch[3]
  }
  const midCommon = halfMatch[4]
  return [text1A, text1B, text2A, text2B, midCommon]
}

/**
 * Do the two texts share a slice which is at least half the length of the
 * longer text?
 * This speedup can produce non-minimal diffs.
 *
 * @param longText - First string.
 * @param shortText - Second string.
 * @returns Five element array, containing the prefix of longText,
 *     the suffix of longText, the prefix of shortText, the suffix of
 *     shortText and the common middle.  Or null if there was no match.
 * @internal
 */
function halfMatchI(longText: string, shortText: string, i: number): null | HalfMatch {
  // Start with a 1/4 length slice at position i as a seed.
  const seed = longText.slice(i, i + Math.floor(longText.length / 4))
  let j = -1
  let bestCommon = ''
  let bestLongTextA
  let bestLongTextB
  let bestShortTextA
  let bestShortTextB

  while ((j = shortText.indexOf(seed, j + 1)) !== -1) {
    const prefixLength = getCommonPrefix(longText.slice(i), shortText.slice(j))
    const suffixLength = getCommonSuffix(longText.slice(0, i), shortText.slice(0, j))
    if (bestCommon.length < suffixLength + prefixLength) {
      bestCommon = shortText.slice(j - suffixLength, j) + shortText.slice(j, j + prefixLength)
      bestLongTextA = longText.slice(0, i - suffixLength)
      bestLongTextB = longText.slice(i + prefixLength)
      bestShortTextA = shortText.slice(0, j - suffixLength)
      bestShortTextB = shortText.slice(j + prefixLength)
    }
  }
  if (bestCommon.length * 2 >= longText.length) {
    return [
      bestLongTextA || '',
      bestLongTextB || '',
      bestShortTextA || '',
      bestShortTextB || '',
      bestCommon || '',
    ]
  }

  return null
}
