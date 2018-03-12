/**
 * Builds a Ditto Protocol message from the passed parameters.
 * @param {string} namespace - The namespace of the entity in java package notation, e.g.: "org.eclipse.ditto"
 * @param {string} id - The ID of the entity
 * @param {string} group - The affected group/entity, one of: "things"
 * @param {string} channel - The channel for the signal, one of: "twin"|"live"
 * @param {string} criterion - The criterion to apply, one of: "commands"|"events"|"search"|"messages"|"errors"
 * @param {string} action - The action to perform, one of: "create"|"retrieve"|"modify"|"delete"
 * @param {string} path - The path which is affected by the message, e.g.: "/attributes"
 * @param {Object.<string, string>} dittoHeaders - The headers Object containing all Ditto Protocol header values
 * @param {*} [value] - The value to apply / which was applied (e.g. in a "modify" action)
 * @returns {DittoProtocolMessage} dittoProtocolMessage - the mapped Ditto Protocol message or <code>null</code> if the
 * message could/should not be mapped
 */
function buildDittoProtocolMsg(namespace, id, group, channel, criterion, action, path, dittoHeaders, value) {

    let dittoProtocolMsg = {};
    dittoProtocolMsg.topic = namespace + "/" + id + "/" + group + "/" + channel + "/" + criterion + "/" + action;
    dittoProtocolMsg.path = path;
    dittoProtocolMsg.headers = dittoHeaders;
    dittoProtocolMsg.value = value;
    return dittoProtocolMsg;
}

/**
 * Maps the passed parameters to a Ditto Protocol message.
 * @param {Object.<string, string>} headers - The headers Object containing all received header values
 * @param {string} [textPayload] - The String to be mapped
 * @param {Array<byte>} [bytePayload] - The bytes to be mapped
 * @param {string} contentType - The received Content-Type, e.g. "application/json"
 * @returns {DittoProtocolMessage} dittoProtocolMessage - the mapped Ditto Protocol message or <code>null</code> if the
 * message could/should not be mapped
 */
function mapToDittoProtocolMsg(
    headers,
    textPayload,
    bytePayload,
    contentType
) {

    // ###
    // Insert your mapping logic here:
    // ###

    return buildDittoProtocolMsg(
        namespace,      // the namespace of the entity in java package notation, e.g.: "org.eclipse.ditto"
        id,             // the ID of the entity
        group,          // "things"
        channel,        // "twin" | "live"
        criterion,      // "commands" | "events" | "search" | "messages" | "errors"
        action,         // "create" | "retrieve" | "modify" | "delete"
        path,           // the path which is affected by the message, e.g.: "/attributes"
        dittoHeaders,   // the headers Object containing all Ditto Protocol header values
        value           // (optional) the value to apply / which was applied (e.g. in a "modify" action)
    );
}

/**
 * Maps the passed external message to a Ditto Protocol message.
 * @param {Object} externalMsg - The external message to map to a Ditto Protocol message
 * @param {Object.<string, string>} externalMsg.headers - The headers Object containing all received header values
 * @param {string} [externalMsg.textPayload] - The String to be mapped
 * @param {Array<byte>}  [externalMsg.bytePayload] - The bytes to be mapped
 * @param {string} externalMsg.contentType - The received Content-Type, e.g. "application/json"
 * @returns {DittoProtocolMessage} dittoProtocolMessage - the mapped Ditto Protocol message or <code>null</code> if the
 * message could/should not be mapped
 */
function mapToDittoProtocolMsgWrapper(externalMsg) {

    let headers = externalMsg.headers;
    let textPayload = externalMsg.textPayload;
    let bytePayload = externalMsg.bytePayload;
    let contentType = externalMsg.contentType;

    return mapToDittoProtocolMsg(headers, textPayload, bytePayload, contentType);
}
