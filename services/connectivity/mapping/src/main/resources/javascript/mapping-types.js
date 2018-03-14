/**
 * Defines the Ditto Protocol message which is understood by Eclipse Ditto.
 * @typedef {Object} DittoProtocolMessage
 * @property {string} topic - The topic of the Ditto Protocol message
 * @property {string} path - The path containing the info what to change / what changed
 * @property {Object.<string, string>} headers - The Ditto headers
 * @property {*} value - The value to change to / changed value
 */

/**
 * Defines an external (not yet mapped) message.
 * @typedef {Object} ExternalMessage
 * @property {Object.<string, string>} headers - The external headers
 * @property {string} [textPayload] - The String to be mapped
 * @property {Array<byte>} [bytePayload] - The bytes to be mapped
 * @property {string} contentType - The external Content-Type, e.g. "application/json"
 */
