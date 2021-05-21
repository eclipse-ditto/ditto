/**
 * Maps the passed parameters which originated from a Ditto Protocol message to an external message.
 * @param {string} namespace - The namespace of the entity in java package notation, e.g.: "org.eclipse.ditto"
 * @param {string} name - The name of the entity, e.g.: "device"
 * @param {string} channel - The channel for the signal, one of: "twin"|"live"
 * @param {string} group - The affected group/entity, one of: "things"
 * @param {string} criterion - The criterion to apply, one of: "commands"|"events"|"search"|"messages"|"errors"
 * @param {string} action - The action to perform, one of: "create"|"retrieve"|"modify"|"delete"
 * @param {string} path - The path which is affected by the message, e.g.: "/attributes"
 * @param {Object.<string, string>} dittoHeaders - The headers Object containing all Ditto Protocol header values
 * @param {*} [value] - The value to apply / which was applied (e.g. in a "modify" action)
 * @param {number} [status] - The status code that indicates the result of the command. When this field is set,
 * it indicates that the Ditto Protocol Message contains a response.
 * @param {Object} [extra] - The enriched extra fields when selected via "extraFields" option.
 * @returns {(ExternalMessage|Array<ExternalMessage>)} externalMessage -
 *  The mapped external message,
 *  an array of external messages or
 *  <code>null</code> if the message could/should not be mapped
 */
function mapFromDittoProtocolMsg(
  namespace,
  name,
  group,
  channel,
  criterion,
  action,
  path,
  dittoHeaders,
  value,
  status,
  extra
) {

  // ###
  // Insert your mapping logic here

  // ### example code using the Ditto protocol content type.
  let headers = dittoHeaders;
  let textPayload = JSON.stringify(Ditto.buildDittoProtocolMsg(namespace, name, group, channel, criterion, action,
                                                               path, dittoHeaders, value, status, extra));
  let bytePayload = null;
  let contentType = 'application/vnd.eclipse.ditto+json';

  return Ditto.buildExternalMsg(
      headers, // The external headers Object containing header values
      textPayload, // The external mapped String
      bytePayload, // The external mapped byte[]
      contentType // The returned Content-Type
  );
}

/**
 * Maps the passed Ditto Protocol message to an external message.
 * @param {DittoProtocolMessage} dittoProtocolMsg - The Ditto Protocol message to map
 * @returns {(ExternalMessage|Array<ExternalMessage>)} externalMessage -
 *  The mapped external message,
 *  an array of external messages or
 *  <code>null</code> if the message could/should not be mapped
 */
function mapFromDittoProtocolMsgWrapper(dittoProtocolMsg) {

  let topic = dittoProtocolMsg.topic;
  let splitTopic = topic.split("/");

  let namespace = splitTopic[0];
  let name = splitTopic[1];
  let group = splitTopic[2];

  let channel;
  let criterion;
  let action;
  if (group !== "things"){
    channel = "undefined";
    criterion = splitTopic[3];
    action = splitTopic[4];
  }else{
    channel = splitTopic[3];
    criterion = splitTopic[4];
    action = splitTopic[5];
  }

  let path = dittoProtocolMsg.path;
  let dittoHeaders = dittoProtocolMsg.headers;
  let value = dittoProtocolMsg.value;
  let status = dittoProtocolMsg.status;
  let extra = dittoProtocolMsg.extra;

  return mapFromDittoProtocolMsg(namespace, name, group, channel, criterion, action,
                                 path, dittoHeaders, value, status, extra);
}
