/**
 * @author Toru Nagashima
 * See LICENSE file in root directory for full license.
 */

/**
 * @param {unknown} path
 * @returns {string}
 */
export function stripImportPathParams(path) {
    const pathString = String(path)
    const index = pathString.indexOf("!")

    if (index === -1) {
        return pathString
    }

    return pathString.slice(0, index)
}
