import {type Diff} from './diff.js'

/**
 * Clones a diff tuple.
 *
 * @param diff - Tuple to clone.
 * @returns New, cloned tuple.
 * @internal
 */
export function cloneDiff(diff: Diff): Diff {
  const [type, patch] = diff
  return [type, patch]
}
