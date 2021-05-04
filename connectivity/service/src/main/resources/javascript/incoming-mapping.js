/**
 * Maps the passed parameters to a Ditto Protocol message.
 * @param {Object.<string, string>} headers - The headers Object containing all received header values
 * @param {string} [textPayload] - The String to be mapped
 * @param {ArrayBuffer} [bytePayload] - The bytes to be mapped as ArrayBuffer
 * @param {string} [contentType] - The received Content-Type, e.g. "application/json"
 * @returns {(DittoProtocolMessage|Array<DittoProtocolMessage>)} dittoProtocolMessage(s) -
 *  The mapped Ditto Protocol message,
 *  an array of Ditto Protocol messages or
 *  <code>null</code> if the message could/should not be mapped
 */
function mapToDittoProtocolMsg(
  headers,
  textPayload,
  bytePayload,
  contentType
) {

  // ### Insert/adapt your mapping logic here.
  // Use helper function Ditto.buildDittoProtocolMsg to build Ditto protocol message
  // based on incoming payload.
  // See https://www.eclipse.org/ditto/connectivity-mapping.html#helper-functions for details.

  // ### example code assuming the Ditto protocol content type for incoming messages.
  if (contentType === 'application/vnd.eclipse.ditto+json') {
    // Message is sent as Ditto protocol text payload and can be used directly
    return JSON.parse(textPayload);
  } else if (contentType === 'application/octet-stream') {
    // Message is sent as binary payload; assume Ditto protocol message (JSON).
    try {
      return JSON.parse(Ditto.arrayBufferToString(bytePayload));
    } catch (e) {
      // parsing failed (no JSON document); return null to drop the message
      return null;
    }
  }

  // no mapping logic matched; return null to drop the message
  return null;
}

/**
 * Maps the passed external message to a Ditto Protocol message.
 * @param {ExternalMessage} externalMsg - The external message to map to a Ditto Protocol message
 * @returns {(DittoProtocolMessage|Array<DittoProtocolMessage>)} dittoProtocolMessage(s) -
 *  The mapped Ditto Protocol message,
 *  an array of Ditto Protocol messages or
 *  <code>null</code> if the message could/should not be mapped
 */
function mapToDittoProtocolMsgWrapper(externalMsg) {

  let headers = externalMsg.headers;
  let textPayload = externalMsg.textPayload;
  let bytePayload = externalMsg.bytePayload;
  let contentType = externalMsg.contentType;

  return mapToDittoProtocolMsg(headers, textPayload, bytePayload, contentType);
}
