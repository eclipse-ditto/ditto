/**
 * Determine the common prefix of two strings.
 *
 * @param text1 - First string.
 * @param text2 - Second string.
 * @returns The number of characters common to the start of each string.
 * @internal
 */
export function getCommonPrefix(text1: string, text2: string): number {
  // Quick check for common null cases.
  if (!text1 || !text2 || text1[0] !== text2[0]) {
    return 0
  }

  // Binary search.
  // Performance analysis: http://neil.fraser.name/news/2007/10/09/
  let pointerMin = 0
  let pointerMax = Math.min(text1.length, text2.length)
  let pointerMid = pointerMax
  let pointerStart = 0
  while (pointerMin < pointerMid) {
    if (text1.substring(pointerStart, pointerMid) === text2.substring(pointerStart, pointerMid)) {
      pointerMin = pointerMid
      pointerStart = pointerMin
    } else {
      pointerMax = pointerMid
    }
    pointerMid = Math.floor((pointerMax - pointerMin) / 2 + pointerMin)
  }
  return pointerMid
}
