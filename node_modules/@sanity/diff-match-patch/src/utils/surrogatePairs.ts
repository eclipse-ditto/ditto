/**
 * Checks if the character is a high surrogate
 *
 * @param char - Character to check
 * @returns True if high surrogate, false otherwise
 */
export function isHighSurrogate(char: string): boolean {
  const charCode = char.charCodeAt(0)
  return charCode >= 0xd800 && charCode <= 0xdbff
}

/**
 * Checks if the character is a low surrogate
 *
 * @param char - Character to check
 * @returns True if low surrogate, false otherwise
 */
export function isLowSurrogate(char: string): boolean {
  const charCode = char.charCodeAt(0)
  return charCode >= 0xdc00 && charCode <= 0xdfff
}
