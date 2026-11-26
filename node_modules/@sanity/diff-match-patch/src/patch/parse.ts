import {DIFF_DELETE, DIFF_EQUAL, DIFF_INSERT} from '../diff/diff.js'
import {countUtf8Bytes} from '../utils/utf8Indices.js'
import {createPatchObject, type Patch} from './createPatchObject.js'

const patchHeader = /^@@ -(\d+),?(\d*) \+(\d+),?(\d*) @@$/

/**
 * Parse a textual representation of patches and return a list of Patch objects.
 *
 * @param textline - Text representation of patches.
 * @returns Array of Patch objects.
 * @public
 */
export function parse(textline: string): Patch[] {
  if (!textline) {
    return []
  }

  const patches: Patch[] = []
  const lines = textline.split('\n')

  let textPointer = 0
  while (textPointer < lines.length) {
    const m = lines[textPointer].match(patchHeader)
    if (!m) {
      throw new Error(`Invalid patch string: ${lines[textPointer]}`)
    }

    const patch = createPatchObject(toInt(m[1]), toInt(m[3]))
    patches.push(patch)
    if (m[2] === '') {
      patch.start1--
      patch.utf8Start1--
      patch.length1 = 1
      patch.utf8Length1 = 1
    } else if (m[2] === '0') {
      patch.length1 = 0
      patch.utf8Length1 = 0
    } else {
      patch.start1--
      patch.utf8Start1--
      // The patch itself will contain the UTF-8 length
      patch.utf8Length1 = toInt(m[2])
      // We start with UCS-2 length set to the same, but we adjust for it later
      patch.length1 = patch.utf8Length1
    }

    if (m[4] === '') {
      patch.start2--
      patch.utf8Start2--
      patch.length2 = 1
      patch.utf8Length2 = 1
    } else if (m[4] === '0') {
      patch.length2 = 0
      patch.utf8Length2 = 0
    } else {
      patch.start2--
      patch.utf8Start2--
      // The patch itself will contain the UTF-8 length
      patch.utf8Length2 = toInt(m[4])
      // We start with UCS-2 length set to the same, but we adjust for it later
      patch.length2 = patch.utf8Length2
    }
    textPointer++

    while (textPointer < lines.length) {
      const currentLine = lines[textPointer]
      const sign = currentLine.charAt(0)

      if (sign === '@') {
        // Start of next patch
        break
      }

      if (sign === '') {
        // Blank line? Ignore.
        textPointer++
        continue
      }

      let line: string
      try {
        line = decodeURI(currentLine.slice(1))
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
      } catch (ex) {
        // Malformed URI sequence.
        throw new Error(`Illegal escape in parse: ${currentLine}`)
      }

      // The number of bytes in a line does not equate to the number of "characters"
      // returned by `string.length` - we have to subtract the diff here in order to
      // make slicing/calculations work correctly
      const utf8Diff = countUtf8Bytes(line) - line.length
      if (sign === '-') {
        // Deletion.
        patch.diffs.push([DIFF_DELETE, line])
        patch.length1 -= utf8Diff
      } else if (sign === '+') {
        // Insertion.
        patch.diffs.push([DIFF_INSERT, line])
        patch.length2 -= utf8Diff
      } else if (sign === ' ') {
        // Minor equality.
        patch.diffs.push([DIFF_EQUAL, line])
        patch.length1 -= utf8Diff
        patch.length2 -= utf8Diff
      } else {
        // WTF?
        throw new Error(`Invalid patch mode "${sign}" in: ${line}`)
      }
      textPointer++
    }
  }
  return patches
}

function toInt(num: string): number {
  return parseInt(num, 10)
}
