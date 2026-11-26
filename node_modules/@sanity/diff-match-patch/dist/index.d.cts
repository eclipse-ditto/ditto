/**
 * Takes a `patches` array as produced by diff-match-patch and adjusts the
 * `start1` and `start2` properties so that they refer to UCS-2 index instead
 * of a UTF-8 index.
 *
 * @param patches - The patches to adjust
 * @param base - The base string to use for counting bytes
 * @param options - Options for the adjustment of indices
 * @returns A new array of patches with adjusted indicies
 * @beta
 */
export declare function adjustIndiciesToUcs2(
  patches: Patch[],
  base: string,
  options?: AdjustmentOptions,
): Patch[]

/**
 * Options for the index adjustment operations.
 *
 * @public
 */
export declare interface AdjustmentOptions {
  /**
   * When converting indices between UTF-8 and UCS-2, certain scenarios can occur
   * where we go beyond the target offset. This can happen in particular with
   * surrogate pairs/high codepoints, when the base string we are applying the
   * patch to does not fully match the one that was used to generate the patch.
   * Defaults to `false`.
   */
  allowExceedingIndices?: boolean
}

/**
 * Merge a set of patches onto the text. Returns patched text, as well as a
 * list of true/false values indicating which patches were applied.
 *
 * @param patches - Array of Patch objects.
 * @param text - Old text.
 * @param opts - Optional settings for the patch application.
 * @returns Two element Array, containing the new text and an array of boolean values.
 * @public
 */
export declare function applyPatches(
  patches: Patch[],
  originalText: string,
  opts?: Partial<ApplyPatchOptions>,
): PatchResult

/**
 * Options for applying a patch.
 *
 * @public
 */
export declare interface ApplyPatchOptions {
  /**
   * Chunk size for context length
   */
  margin: number
  /**
   * When deleting a large block of text (over ~64 characters),
   * how close do the contents have to be to match the expected contents.
   * (0.0 = perfection, 1.0 = very loose).
   */
  deleteThreshold: number
  /**
   * When converting indices between UTF-8 and UCS-2, certain scenarios can occur
   * where we go beyond the target offset. This can happen in particular with
   * surrogate pairs/high codepoints, when the base string we are applying the
   * patch to does not fully match the one that was used to generate the patch.
   * Defaults to `false`.
   */
  allowExceedingIndices: boolean
}

/**
 * Reduce the number of edits by eliminating operationally trivial equalities.
 *
 * @param rawDiffs - Array of diff tuples.
 * @param editCost - Cost of an empty edit operation in terms of edit characters.
 * @returns Array of diff tuples.
 * @public
 */
export declare function cleanupEfficiency(rawDiffs: Diff[], editCost?: number): Diff[]

/**
 * Reduce the number of edits by eliminating semantically trivial equalities.
 *
 * @param rawDiffs - Array of diff tuples.
 * @returns Array of diff tuples.
 * @public
 */
export declare function cleanupSemantic(rawDiffs: Diff[]): Diff[]

/**
 * The data structure representing a diff is an array of tuples:
 * [[DIFF_DELETE, 'Hello'], [DIFF_INSERT, 'Goodbye'], [DIFF_EQUAL, ' world.']]
 * which means: delete 'Hello', add 'Goodbye' and keep ' world.'
 *
 * @public
 */
export declare type Diff = [DiffType, string]

/**
 * Diff type for deleted text.
 *
 * @public
 */
export declare const DIFF_DELETE = -1

/**
 * Diff type for text that is equal.
 *
 * @public
 */
export declare const DIFF_EQUAL = 0

/**
 * Diff type for inserted text.
 *
 * @public
 */
export declare const DIFF_INSERT = 1

/**
 * Options for generating a diff.
 *
 * @public
 */
export declare interface DiffOptions {
  checkLines: boolean
  timeout: number
}

/**
 * The three different types of changes possible in a diff:
 * - `DIFF_DELETE`: a deletion of text
 * - `DIFF_INSERT`: an insertion of text
 * - `DIFF_EQUAL` : an equal text
 *
 * @public
 */
export declare type DiffType = typeof DIFF_DELETE | typeof DIFF_INSERT | typeof DIFF_EQUAL

/**
 * Find the differences between two texts.  Simplifies the problem by stripping
 * any common prefix or suffix off the texts before diffing.
 *
 * @param textA - Old string to be diffed.
 * @param textA - New string to be diffed.
 * @returns Array of diff tuples.
 * @public
 */
export declare function makeDiff(
  textA: null | string,
  textB: null | string,
  opts?: Partial<DiffOptions>,
): Diff[]

/**
 * Compute a list of patches to turn based on passed diffs.
 *
 * @param diffs - Array of diff tuples.
 * @param options - Options for the patch generation.
 * @returns Array of Patch objects.
 * @public
 */
export declare function makePatches(diffs: Diff[], options?: Partial<MakePatchOptions>): Patch[]

/**
 * Compute a list of patches to turn textA into textB.
 *
 * @param textA - Original text.
 * @param textB - New text.
 * @param options - Options for the patch generation.
 * @returns Array of Patch objects.
 * @public
 */
export declare function makePatches(
  textA: string,
  textB: string,
  options?: Partial<MakePatchOptions>,
): Patch[]

/**
 * Compute a list of patches to turn textA into textB using provided diff tuples.
 *
 * @param textA - Original text.
 * @param diffs - Diff tuples to use as base.
 * @param options - Options for the patch generation.
 * @returns Array of Patch objects.
 * @public
 */
export declare function makePatches(
  textA: string,
  diffs: Diff[],
  options?: Partial<MakePatchOptions>,
): Patch[]

/**
 * Options for patch generation.
 *
 * @public
 */
export declare interface MakePatchOptions {
  margin: number
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
export declare function match(
  text: string,
  pattern: string,
  searchLocation: number,
  options?: MatchOptions,
): number

/**
 * @public
 */
export declare interface MatchOptions {
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
 * Parse a textual representation of patches and return a list of Patch objects.
 *
 * @param textline - Text representation of patches.
 * @returns Array of Patch objects.
 * @public
 */
export declare function parsePatch(textline: string): Patch[]

/**
 * Object representing one patch operation.
 *
 * @public
 */
export declare interface Patch {
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
 * Result of a patch application operation.
 * Index 0 contains the new text
 * Index 1 contains an array of booleans indicating which patches were applied
 *
 * @public
 */
export declare type PatchResult = [string, boolean[]]

/**
 * Create a textual representation of a patch.
 *
 * @param patch - Patch to stringify
 * @returns Text representation of patch
 * @public
 */
export declare function stringifyPatch(patch: Patch): string

/**
 * Create a textual representation of a patch list.
 *
 * @param patches - Patches to stringify
 * @returns Text representation of patches
 * @public
 */
export declare function stringifyPatches(patches: Patch[]): string

/**
 * loc is a location in textA, compute and return the equivalent location in
 * textB.
 * e.g. 'The cat' vs 'The big cat', 1->1, 5->8
 *
 * @param diffs - Array of diff tuples.
 * @param location - Location within textA.
 * @returns Location within textB.
 * @public
 */
export declare function xIndex(diffs: Diff[], location: number): number

export {}
