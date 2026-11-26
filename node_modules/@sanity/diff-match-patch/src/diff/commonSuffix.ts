/**
 * Determine the common suffix of two strings.
 *
 * @param text1 - First string.
 * @param text2 - Second string.
 * @returns The number of characters common to the end of each string.
 * @internal
 */
export function getCommonSuffix(text1: string, text2: string): number {
  // Quick check for common null cases.
  if (!text1 || !text2 || text1[text1.length - 1] !== text2[text2.length - 1]) {
    return 0
  }

  // Binary search.
  // Performance analysis: http://neil.fraser.name/news/2007/10/09/
  let pointerMin = 0
  let pointerMax = Math.min(text1.length, text2.length)
  let pointerMid = pointerMax
  let pointerEnd = 0
  while (pointerMin < pointerMid) {
    if (
      text1.substring(text1.length - pointerMid, text1.length - pointerEnd) ===
      text2.substring(text2.length - pointerMid, text2.length - pointerEnd)
    ) {
      pointerMin = pointerMid
      pointerEnd = pointerMin
    } else {
      pointerMax = pointerMid
    }
    pointerMid = Math.floor((pointerMax - pointerMin) / 2 + pointerMin)
  }

  return pointerMid
}
