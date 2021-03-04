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

  // ###
  // Insert your mapping logic here:
  if (contentType === 'application/vnd.eclipse.ditto+json') {
    let dittoProtocolMsg = JSON.parse(textPayload);
    Object.assign(dittoProtocolMsg.headers, headers);
    return dittoProtocolMsg;
  }
  if (headers) {
    return null; // returning 'null' means that the message will be dropped
    // TODO replace with something useful
  }
  // ###

  return Ditto.buildDittoProtocolMsg(
    namespace,
    id,
    group,
    channel,
    criterion,
    action,
    path,
    dittoHeaders,
    value,
    status,
    extra
  );
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
