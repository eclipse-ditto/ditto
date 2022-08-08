/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.io.UnsupportedEncodingException;
import java.util.*;

import org.eclipse.ditto.base.model.common.DittoConstants;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.json.*;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;

/**
 * A message mapper implementation for the Ditto Protocol. Expects messages to contain a JSON
 * serialized Ditto Protocol message.
 */
@PayloadMapper(
    alias = {
      "CloudEvents",
      // legacy full qualified name
      "org.eclipse.ditto.connectivity.service.mapping.CloudEventsMapper"
    })
public final class CloudEventsMapper extends AbstractMessageMapper {

  static final String CE_ID = "ce-id";
  static final String CE_TYPE = "ce-type";
  static final String CE_SOURCE = "ce-source";
  static final String CE_TIME = "ce-time";

  static final JsonObject DEFAULT_OPTIONS =
      JsonObject.newBuilder()
          .set(
              MessageMapperConfiguration.CONTENT_TYPE_BLOCKLIST,
              String.join(
                  ",",
                  "application/vnd.eclipse-hono-empty-notification",
                  "application/vnd.eclipse-hono-device-provisioning-notification",
                  "application/vnd.eclipse-hono-dc-notification+json",
                  "application/vnd.eclipse-hono-delivery-failure-notification+json"))
          .build();

  /** The context representing this mapper */
  public static final MappingContext CONTEXT =
      ConnectivityModelFactory.newMappingContextBuilder(
              CloudEventsMapper.class.getCanonicalName(), DEFAULT_OPTIONS)
          .build();

  final String SPECVERSION = "specversion";
  final String ID = "id";
  final String SOURCE = "source";
  final String TYPE = "type";

  //    public static void main(String[] args) {
  //        String data =
  // "{\"topic\":\"org.eclipse.ditto/sensor/things/twin/commands/modify\",\"path\":\"/\",\"value\":53}";
  //        String data_base64 =
  // Base64.getEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8));
  //////        System.out.println(data_base64);
  ////       String f =
  // "ewogICJ0b3BpYyI6Im15LnNlbnNvcnMvc2Vuc29yMDEvdGhpbmdzL3R3aW4vY29tbWFuZHMvbW9kaWZ5IiwKICAicGF0aCI6Ii8iLAogICJ2YWx1ZSI6ewogICAgICAidGhpbmdJZCI6ICJteS5zZW5zb3JzOnNlbnNvcjAxIiwKICAgICAgInBvbGljeUlkIjogIm15LnRlc3Q6cG9saWN5IiwKICAgICAgImF0dHJpYnV0ZXMiOiB7CiAgICAgICAgICAibWFudWZhY3R1cmVyIjogIldlbGwga25vd24gc2Vuc29ycyBwcm9kdWNlciIsCiAgICAgICAgICAgICJzZXJpYWwgbnVtYmVyIjogIjEwMCIsIAogICAgICAgICAgICAibG9jYXRpb24iOiAiR3JvdW5kIGZsb29yIiB9LAogICAgICAgICAgICAiZmVhdHVyZXMiOiB7CiAgICAgICAgICAgICAgIm1lYXN1cmVtZW50cyI6IAogICAgICAgICAgICAgICB7InByb3BlcnRpZXMiOiAKICAgICAgICAgICAgICAgeyJ0ZW1wZXJhdHVyZSI6IDIwMCwKICAgICAgICAgICAgICAgICJodW1pZGl0eSI6IDEwMH19fX19";
  //////        byte[] a = Base64.getDecoder().decode(f);
  //////        String s = new String(a);
  //////        System.out.println(s);
  //        String test = "{\"specversion\": \"1.0\", \"id\":\"3212e\",
  // \"source\":\"http:somesite.com\",\"type\":\"com.site.com\",\"data\":{\"topic\":\"org.eclipse.ditto/sensor/things/twin/commands/modify\",\"path\":\"/\",\"value\":53}}";
  //        String test2 = "{\"specversion\": \"1.0\", \"id\":\"3212e\",
  // \"source\":\"http:somesite.com\",\"type\":\"com.site.com\",\"data_base64\":\"ewogICJ0b3BpYyI6Im15LnNlbnNvcnMvc2Vuc29yMDEvdGhpbmdzL3R3aW4vY29tbWFuZHMvbW9kaWZ5IiwKICAicGF0aCI6Ii8iLAogICJ2YWx1ZSI6ewogICAgICAidGhpbmdJZCI6ICJteS5zZW5zb3JzOnNlbnNvcjAxIiwKICAgICAgInBvbGljeUlkIjogIm15LnRlc3Q6cG9saWN5IiwKICAgICAgImF0dHJpYnV0ZXMiOiB7CiAgICAgICAgICAibWFudWZhY3R1cmVyIjogIldlbGwga25vd24gc2Vuc29ycyBwcm9kdWNlciIsCiAgICAgICAgICAgICJzZXJpYWwgbnVtYmVyIjogIjEwMCIsIAogICAgICAgICAgICAibG9jYXRpb24iOiAiR3JvdW5kIGZsb29yIiB9LAogICAgICAgICAgICAiZmVhdHVyZXMiOiB7CiAgICAgICAgICAgICAgIm1lYXN1cmVtZW50cyI6IAogICAgICAgICAgICAgICB7InByb3BlcnRpZXMiOiAKICAgICAgICAgICAgICAgeyJ0ZW1wZXJhdHVyZSI6IDIwMCwKICAgICAgICAgICAgICAgICJodW1pZGl0eSI6IDEwMH19fX19\"}";
  //        extractData(test2);
  //
  ////        base64decoding(test2);
  //    }

  public String base64decoding(final String base64Message) throws UnsupportedEncodingException {
    byte[] messageByte = Base64.getDecoder().decode(base64Message);
    String decodedString = new String(messageByte);
    return decodedString;
  }

  boolean validatePayload(String payload) {
    Map<String, Object> incomingMessagePayload = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final JsonObject jsonObject = JsonFactory.newObject(payload);
    List<JsonKey> key = jsonObject.getKeys();
    for (JsonKey keyIterator : key) {
      Object value = jsonObject.getValue(keyIterator).orElse(null);
      if (value != null) {
        incomingMessagePayload.put(keyIterator.toString(), value);
      }
    }
    if (incomingMessagePayload.get("specversion") == null
        || incomingMessagePayload.get("id") == null
        || incomingMessagePayload.get("source") == null
        || incomingMessagePayload.get("type") == null) {
      return false;
    } else {
      return true;
    }
  }

  private JsonifiableAdaptable extractData(final String message) {
    Map<String, String> payloadMap = new HashMap<>();
    final JsonObject payloadJson = JsonFactory.newObject(message);
    if (payloadJson.getValue("data_base64").isPresent()) {
      try {
        String base64Data = payloadJson.getValue("data_base64").orElse(null).asString();
        String decodedData = base64decoding(base64Data);
        final JsonifiableAdaptable decodedJsonifiableAdaptable =
            DittoJsonException.wrapJsonRuntimeException(
                () ->
                    ProtocolFactory.jsonifiableAdaptableFromJson(
                        JsonFactory.newObject(decodedData)));
        return decodedJsonifiableAdaptable;
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    } else {
      String data = payloadJson.getValue("data").get().toString();
      return DittoJsonException.wrapJsonRuntimeException(
          () -> ProtocolFactory.jsonifiableAdaptableFromJson(JsonFactory.newObject(data)));
    }
  }

  @Override
  public List<Adaptable> map(final ExternalMessage message) {
    // extract message as String
    final String payload = extractPayloadAsString(message);
    if (validatePayload(payload)) {
      JsonifiableAdaptable adaptable = extractData(payload);
      System.out.println(singletonList(ProtocolFactory.newAdaptableBuilder(adaptable).build()));
      return singletonList(ProtocolFactory.newAdaptableBuilder(adaptable).build());
    } else {
      try {
        throw new Exception("This isn't a CloudEvent");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public DittoHeaders getAdditionalInboundHeaders(ExternalMessage message) {
    return null;
  }

  @Override
  public List<ExternalMessage> map(final Adaptable adaptable) {
    return List.of(
        ExternalMessageFactory.newExternalMessageBuilder(getExternalDittoHeaders(adaptable))
            .withTopicPath(adaptable.getTopicPath())
            .withText(getJsonString(adaptable))
            .asResponse(isResponse(adaptable))
            .asError(isError(adaptable))
            .build());
  }

  private static DittoHeaders getExternalDittoHeaders(final Adaptable adaptable) {
    return DittoHeaders.newBuilder()
        .contentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)
        .correlationId(adaptable.getDittoHeaders().getCorrelationId().orElse(null))
        .build();
  }

  private static String getJsonString(final Adaptable adaptable) {
    final var jsonifiableAdaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);
    return jsonifiableAdaptable.toJsonString();
  }

  @Override
  public JsonObject getDefaultOptions() {
    return DEFAULT_OPTIONS;
  }
}
