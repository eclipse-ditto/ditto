import {type Diff, DIFF_DELETE, DIFF_EQUAL, DIFF_INSERT} from './diff.js'

/**
 * Crush the diff into an encoded string which describes the operations
 * required to transform text1 into text2.
 * E.g. =3\t-2\t+ing  -> Keep 3 chars, delete 2 chars, insert 'ing'.
 * Operations are tab-separated.  Inserted text is escaped using %xx notation.
 *
 * @param diffs - Array of diff tuples.
 * @returns Delta text.
 * @internal
 */
export function toDelta(diffs: Diff[]): string {
  const text: string[] = []
  for (let x = 0; x < diffs.length; x++) {
    const [diffType, diff] = diffs[x]
    switch (diffType) {
      case DIFF_INSERT:
        text.push(`+${encodeURI(diff)}`)
        break
      case DIFF_DELETE:
        text.push(`-${diff.length}`)
        break
      case DIFF_EQUAL:
        text.push(`=${diff.length}`)
        break
      default:
        throw new Error(`Unknown diff type: ${diffType}`)
    }
  }

  return text.join('\t').replace(/%20/g, ' ')
}
