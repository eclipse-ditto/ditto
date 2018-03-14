/**
 * Builds an external message from the passed parameters.
 * @param {Object.<string, string>} headers - The external headers Object containing header values
 * @param {string} [textPayload] - The external mapped String
 * @param {string} [bytePayload] - The external mapped byte[]
 * @param {string} contentType - The returned Content-Type
 * @returns {ExternalMessage} externalMessage - the mapped external message or <code>null</code> if the
 * message could/should not be mapped
 */
function buildExternalMsg(headers, textPayload, bytePayload, contentType) {

    let externalMsg = {};
    externalMsg.headers = headers;
    externalMsg.textPayload = textPayload;
    externalMsg.bytePayload = bytePayload;
    externalMsg.contentType = contentType;
    return externalMsg;
}

/**
 * Maps the passed parameters which originated from a Ditto Protocol message to an external message.
 * @param {string} namespace - The namespace of the entity in java package notation, e.g.: "org.eclipse.ditto"
 * @param {string} id - The ID of the entity
 * @param {string} channel - The channel for the signal, one of: "twin"|"live"
 * @param {string} group - The affected group/entity, one of: "things"
 * @param {string} criterion - The criterion to apply, one of: "commands"|"events"|"search"|"messages"|"errors"
 * @param {string} action - The action to perform, one of: "create"|"retrieve"|"modify"|"delete"
 * @param {string} path - The path which is affected by the message, e.g.: "/attributes"
 * @param {Object.<string, string>} dittoHeaders - The headers Object containing all Ditto Protocol header values
 * @param {*} [value] - The value to apply / which was applied (e.g. in a "modify" action)
 * @returns {ExternalMessage} externalMessage - The mapped external message or <code>null</code> if the
 * message could/should not be mapped
 */
function mapFromDittoProtocolMsg(
    namespace,
    id,
    group,
    channel,
    criterion,
    action,
    path,
    dittoHeaders,
    value
) {

    // ###
    // Insert your mapping logic here:
    // ###

    return buildExternalMsg(
        headers,        // the external headers Object containing header values
        textPayload,    // (optional) the external mapped String
        bytePayload,    // (optional) the external mapped byte[]
        contentType     // the external Content-Type
    );
}

/**
 * Maps the passed Ditto Protocol message to an external message.
 * @param {DittoProtocolMessage} dittoProtocolMsg - The Ditto Protocol message to map
 * @returns {ExternalMessage} externalMessage - The mapped external message or <code>null</code> if the
 * message could/should not be mapped
 */
function mapFromDittoProtocolMsgWrapper(dittoProtocolMsg) {

    let topic = dittoProtocolMsg.topic;
    let splitTopic = topic.split("/");

    let namespace = splitTopic[0];
    let id = splitTopic[1];
    let group = splitTopic[2];
    let channel = splitTopic[3];
    let criterion = splitTopic[4];
    let action = splitTopic[5];

    let path = dittoProtocolMsg.path;
    let dittoHeaders = dittoProtocolMsg.headers;
    let value = dittoProtocolMsg.value;

    return mapFromDittoProtocolMsg(namespace, id, group, channel, criterion, action, path, dittoHeaders, value);
}
