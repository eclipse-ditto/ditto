/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.connectivity.service.mapping;

import static java.util.Collections.singletonList;
import static org.eclipse.ditto.json.JsonFactory.newObject;

import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.MessageMappingFailedException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;

/**
 * A message mapper implementation for the Mapping incoming CloudEvents to Ditto Protocol.
 */
@PayloadMapper(alias = {"CloudEvents",
    // legacy full qualified name
    "org.eclipse.ditto.connectivity.service.mapping.CloudEventsMapper"})
public final class CloudEventsMapper extends AbstractMessageMapper {

  static final String CE_ID = "ce-id";
  static final String CE_TYPE = "ce-type";
  static final String CE_SOURCE = "ce-source";
  static final String CE_SPECVERSION = "ce-specversion";
  static final String STRUCTURED_CONTENT_TYPE = "application/cloudevents+json";
  static final String DITTO_PROTOCOL_CONTENT_TYPE = "application/vnd.eclipse.ditto+json";
  static final String SPECVERSION = "specversion";
  static final String ID = "id";
  static final String SOURCE = "source";
  static final String TYPE = "type";
  static final String OUTBOUNDTYPE = "org.eclipse.ditto.outbound";
  static final String OUTBOUNDSPECVERSION = "1.0";
  static final String OUTBOUNDSOURCE = "https://github.com/eclipse/ditto";
  static final String DATA = "data";

  static final String OUTBOUND_DATA_CONTENT_TYPE = "datacontenttype";

  static final String OUTBOUND_TIME = "time";

  static final String OUTBOUND_SUBJECT = "subject";
  static final String DATA_BASE64 = "data_base64";

  @Override
  public List<Adaptable> map(final ExternalMessage message) {
    final String payload = extractPayloadAsString(message);
    final String contentType = message.findContentType().orElse("");
    if (contentType.equals(DITTO_PROTOCOL_CONTENT_TYPE)) {
      if (isBinaryCloudEvent(message)) {
        final JsonifiableAdaptable binaryAdaptable = DittoJsonException.wrapJsonRuntimeException(
            () -> ProtocolFactory.jsonifiableAdaptableFromJson(newObject(payload)));
        final DittoHeaders headers = binaryAdaptable.getDittoHeaders()
            .toBuilder()
            .correlationId(message.getHeaders().get(CE_ID))
            .build();
        return singletonList(
            ProtocolFactory.newAdaptableBuilder(binaryAdaptable).withHeaders(headers).build());
      } else {
        throw MessageMappingFailedException.newBuilder(
                message.findContentType().orElse("")).description("This is not a CloudEvent")
            .dittoHeaders(DittoHeaders.of(message.getHeaders())).build();
      }
    } else if (contentType.equals(STRUCTURED_CONTENT_TYPE)) {
      if (isStructuredCloudEvent(payload)) {
        final JsonifiableAdaptable adaptable = extractData(payload);
        final DittoHeaders headers = adaptable.getDittoHeaders()
            .toBuilder()
            .correlationId(getInboundId(payload))
            .build();
        return singletonList(
            ProtocolFactory.newAdaptableBuilder(adaptable).withHeaders(headers).build());
      } else {
        throw MessageMappingFailedException.newBuilder(
                message.findContentType().orElse("")).description("This is not a CloudEvent")
            .dittoHeaders(DittoHeaders.of(message.getHeaders())).build();
      }
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public DittoHeaders getAdditionalInboundHeaders(ExternalMessage message) {
    return DittoHeaders.empty();
  }

  @Override
  public List<ExternalMessage> map(final Adaptable adaptable) {
    return List.of(ExternalMessageFactory.newExternalMessageBuilder(
            Map.of(ExternalMessage.CONTENT_TYPE_HEADER, STRUCTURED_CONTENT_TYPE))
        .withTopicPath(adaptable.getTopicPath())
        .withText(getExternalCloudEventSpecifications(adaptable))
        .asResponse(isResponse(adaptable)).asError(isError(adaptable)).build());
  }

  boolean isStructuredCloudEvent(final String payload) {
    final JsonObject jsonObject = newObject(payload);
    return jsonObject.getValue(SPECVERSION).isPresent() &&
        jsonObject.getValue(TYPE).isPresent() &&
        jsonObject.getValue(ID).isPresent() &&
        jsonObject.getValue(SOURCE).isPresent();
  }

  boolean isBinaryCloudEvent(final ExternalMessage message) {
    final Map<String, String> headers = message.getHeaders();
    return message.getHeaders().containsKey(CE_ID) &&
        headers.containsKey(CE_SOURCE) &&
        headers.containsKey(CE_TYPE) &&
        headers.containsKey(CE_SPECVERSION);
  }

  private JsonifiableAdaptable extractData(final String message) {
    final JsonObject payloadJson = newObject(message);
    final Optional<JsonValue> base64Opt = payloadJson.getValue(DATA_BASE64);
    final Optional<JsonValue> dataOpt = payloadJson.getValue(DATA);
    if (base64Opt.isPresent()) {
      final String base64Data = base64Opt.get().asString();
      final String decodedData = base64decoding(base64Data);
      return DittoJsonException.wrapJsonRuntimeException(
          () -> ProtocolFactory.jsonifiableAdaptableFromJson(newObject(decodedData)));
    }
    else if (dataOpt.isPresent()) {
      final String structuredData = dataOpt.get().toString();
      return DittoJsonException.wrapJsonRuntimeException(
          () -> ProtocolFactory.jsonifiableAdaptableFromJson(newObject(structuredData)));
    }
    else {
      throw MessageMappingFailedException.newBuilder(
          STRUCTURED_CONTENT_TYPE).description("This is not a CloudEvent").build();
    }
  }


  private String base64decoding(final String base64Message) {
    byte[] messageByte = Base64.getDecoder().decode(base64Message);
    return new String(messageByte);
  }

  private static String getExternalCloudEventSpecifications(final Adaptable adaptable) {
    final String outboundID = adaptable.getDittoHeaders().getCorrelationId()
        .orElseGet(() -> UUID.randomUUID().toString());
    JsonObject dataObject = JsonFactory.newObject(getJsonString(adaptable));
    final String topicPathWithoutEntityId = adaptable.getTopicPath().getPath().split("/", 3)[2];
    final String type = OUTBOUNDTYPE + ":" + topicPathWithoutEntityId;
    final String time = adaptable.getPayload().getTimestamp().orElse(Instant.now()).toString();
    final String subject = adaptable.getTopicPath().getNamespace() + ":" + adaptable.getTopicPath().getEntityName();
    final JsonObject externalMessageObject = JsonObject.newBuilder()
        .set(DATA, dataObject)
        .set(SPECVERSION, OUTBOUNDSPECVERSION)
        .set(ID, outboundID)
        .set(SOURCE, OUTBOUNDSOURCE)
        .set(TYPE, type)
        .set(OUTBOUND_DATA_CONTENT_TYPE, DITTO_PROTOCOL_CONTENT_TYPE)
        .set(OUTBOUND_TIME, time)
        .set(OUTBOUND_SUBJECT, subject)
        .build();
    return externalMessageObject.toString();
  }

  private static String getInboundId(final String message){
    JsonObject inboundMessageObject = JsonFactory.newObject(message);
    return inboundMessageObject.getValue("id").orElse(JsonValue.of("")).asString();
  }

  private static String getJsonString(final Adaptable adaptable) {
    final var jsonifiableAdaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);
    return jsonifiableAdaptable.toJsonString();
  }
}