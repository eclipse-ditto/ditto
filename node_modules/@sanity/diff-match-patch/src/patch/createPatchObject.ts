import {type Diff} from '../diff/diff.js'

/**
 * Object representing one patch operation.
 *
 * @public
 */
export interface Patch {
  diffs: Diff[]

  start1: number
  start2: number
  utf8Start1: number
  utf8Start2: number

  length1: number
  length2: number
  utf8Length1: number
  utf8Length2: number
}

/**
 * Clones a patch object.
 *
 * @param patch - The patch to clone
 * @returns Cloned object
 * @private
 */
export function clone(patch: Patch): Patch {
  return {...patch, diffs: patch.diffs.map((diff) => ({...diff}))}
}

/**
 * Performs a deep copy of a patch array.
 *
 * @param patches - Patches to clone
 * @returns Cloned array
 * @internal
 */
export function deepCopy(patches: Patch[]): Patch[] {
  return patches.map(clone)
}

/**
 * Create a new, empty, patch object.
 *
 * @param start1 - The index to start the "from" at
 * @param start2 - The index to start the "to" at
 * @returns New patch object
 * @internal
 */
export function createPatchObject(start1: number, start2: number): Patch {
  return {
    diffs: [],

    start1,
    start2,
    utf8Start1: start1,
    utf8Start2: start2,

    length1: 0,
    length2: 0,
    utf8Length1: 0,
    utf8Length2: 0,
  }
}
