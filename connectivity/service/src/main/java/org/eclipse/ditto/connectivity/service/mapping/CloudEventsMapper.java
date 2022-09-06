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

import java.io.UnsupportedEncodingException;
import java.util.*;

import com.eclipsesource.json.Json;
import org.eclipse.ditto.base.model.common.DittoConstants;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.model.MessageMappingFailedException;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.*;
import org.eclipse.ditto.messages.model.MessageHeaders;
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
  static final String CONTENT_TYPE = "application/cloudevents+json";
  static final String SPECVERSION = "specversion";
  static final String ID = "id";
  static final String SOURCE = "source";
  static final String TYPE = "type";
  static final String OUTBOUNDTYPE = "org.eclipse.ditto.outbound";
  static final String OUTBOUNDSPECVERSION = "1.0";
  static final String OUTBOUNDSOURCE = "org.eclipse.ditto";


  @Override
  public List<Adaptable> map(final ExternalMessage message) {
    MessageMappingFailedException mappingFailedException = MessageMappingFailedException.newBuilder(
            message.findContentType().orElse("")).description("This is not a CloudEvent")
        .dittoHeaders(DittoHeaders.of(message.getHeaders())).build();
    if (message.findContentType().orElse("").equals(CONTENT_TYPE)) {
      final String payload = extractPayloadAsString(message);
      try {
        if (isBinaryCloudEvent(message)) {
          final JsonifiableAdaptable binaryAdaptable = DittoJsonException.wrapJsonRuntimeException(
              () -> ProtocolFactory.jsonifiableAdaptableFromJson(newObject(payload)));
          DittoHeaders headers = binaryAdaptable.getDittoHeaders();
          return singletonList(
              ProtocolFactory.newAdaptableBuilder(binaryAdaptable).withHeaders(headers).build());
        }
        if (isStructuredCloudEvent(payload)) {
          final JsonifiableAdaptable adaptable = extractData(payload);
          final DittoHeaders headers = adaptable.getDittoHeaders();
          return singletonList(
              ProtocolFactory.newAdaptableBuilder(adaptable).withHeaders(headers).build());
        } else {
          throw mappingFailedException;
        }
      } catch (MessageMappingFailedException e) {
        throw mappingFailedException;
      }
    }
    return Collections.emptyList();

  }

  @Override
  public DittoHeaders getAdditionalInboundHeaders(ExternalMessage message) {
    return DittoHeaders.empty();
  }

  @Override
  public List<ExternalMessage> map(final Adaptable adaptable) {
    return List.of(ExternalMessageFactory.newExternalMessageBuilder(
            Map.of(ExternalMessage.CONTENT_TYPE_HEADER, CONTENT_TYPE))
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

  public boolean isBinaryCloudEvent(final ExternalMessage message) {
    final Map<String, String> headers = message.getHeaders();
    return message.getHeaders().containsKey(CE_ID) &&
        headers.containsKey(CE_SOURCE) &&
        headers.containsKey(CE_TYPE) &&
        headers.containsKey(CE_SPECVERSION);
  }

  private JsonifiableAdaptable extractData(final String message) {
    final JsonObject payloadJson = newObject(message);
    final Optional<JsonValue> base64Opt = payloadJson.getValue("data_base64");
    if (base64Opt.isPresent()) {
      final String base64Data = base64Opt.get().asString();
      final String decodedData = base64decoding(base64Data);
      return DittoJsonException.wrapJsonRuntimeException(
          () -> ProtocolFactory.jsonifiableAdaptableFromJson(newObject(decodedData)));
    }
    final Optional<JsonValue> dataOpt = payloadJson.getValue("data");
    if (dataOpt.isPresent()) {
      final String data = dataOpt.get().toString();
      return DittoJsonException.wrapJsonRuntimeException(
          () -> ProtocolFactory.jsonifiableAdaptableFromJson(newObject(data)));
    } else {
      throw MessageMappingFailedException.newBuilder(
          CONTENT_TYPE).description("This is not a CloudEvent").build();
    }
  }

  private String base64decoding(final String base64Message) {
    byte[] messageByte = Base64.getDecoder().decode(base64Message);
    return new String(messageByte);
  }

  private static String getExternalCloudEventSpecifications(final Adaptable adaptable) {
    final String outboundID = UUID.randomUUID().toString();
    JsonObject dataObject = JsonFactory.newObject(getJsonString(adaptable));
    final JsonObject externalMessageObject = JsonObject.newBuilder()
        .set("data", dataObject)
        .set(SPECVERSION, OUTBOUNDSPECVERSION)
        .set(ID, outboundID)
        .set(SOURCE, OUTBOUNDSOURCE)
        .set(TYPE, OUTBOUNDTYPE)
        .build();
    return externalMessageObject.toString();
  }

  private static String getJsonString(final Adaptable adaptable) {
    final var jsonifiableAdaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);
    return jsonifiableAdaptable.toJsonString();
  }

}