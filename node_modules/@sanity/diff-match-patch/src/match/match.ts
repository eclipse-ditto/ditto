import {bitap} from './bitap.js'

/**
 * @public
 */
export interface MatchOptions {
  /**
   * At what point is no match declared (0.0 = perfection, 1.0 = very loose).
   * The larger threshold is, the slower match() may take to compute
   *
   * Defaults to 0.5
   */
  threshold?: number

  /**
   * How far to search for a match (0 = exact location, 1000+ = broad match).
   * A match this many characters away from the expected location will add
   * 1.0 to the score (0.0 is a perfect match).
   *
   * The larger distance is, the slower match() may take to compute.
   *
   * Defaults to 1000.
   */
  distance?: number
}

/**
 * Locate the best instance of 'pattern' in 'text' near 'loc'.
 *
 * @param text - The text to search.
 * @param pattern - The pattern to search for.
 * @param searchLocation - The location to search around.
 * @param options - Options {@link MatchOptions}
 * @returns Best match index or -1.
 * @public
 */
export function match(
  text: string,
  pattern: string,
  searchLocation: number,
  options: MatchOptions = {},
): number {
  // Check for null inputs.
  if (text === null || pattern === null || searchLocation === null) {
    throw new Error('Null input. (match())')
  }

  const loc = Math.max(0, Math.min(searchLocation, text.length))
  if (text === pattern) {
    // Shortcut (potentially not guaranteed by the algorithm)
    return 0
  } else if (!text.length) {
    // Nothing to match.
    return -1
  } else if (text.substring(loc, loc + pattern.length) === pattern) {
    // Perfect match at the perfect spot!  (Includes case of null pattern)
    return loc
  }

  // Do a fuzzy compare.
  return bitap(text, pattern, loc, options)
}
