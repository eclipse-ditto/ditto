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

import java.io.UnsupportedEncodingException;
import java.util.*;

import org.eclipse.ditto.base.model.common.DittoConstants;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.*;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;

/**
 * A message mapper implementation for the Mapping incoming CloudEvents to Ditto Protocol.
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
    static final String CE_SPECVERSION = "ce-specversion";

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

    /**
     * The context representing this mapper
     */
    public static final MappingContext CONTEXT =
            ConnectivityModelFactory.newMappingContextBuilder(
                            CloudEventsMapper.class.getCanonicalName(), DEFAULT_OPTIONS)
                    .build();

    final String SPECVERSION = "specversion";
    final String ID = "id";
    final String SOURCE = "source";
    final String TYPE = "type";

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

    private JsonifiableAdaptable extractData(final String message)
            throws UnsupportedEncodingException {
        final JsonObject payloadJson = JsonFactory.newObject(message);
        if (payloadJson.getValue("data_base64").isPresent()) {
            String base64Data = payloadJson.getValue("data_base64").orElse(null).asString();
            String decodedData = base64decoding(base64Data);

            final JsonifiableAdaptable decodedJsonifiableAdaptable =
                    DittoJsonException.wrapJsonRuntimeException(
                            () ->
                                    ProtocolFactory.jsonifiableAdaptableFromJson(JsonFactory.newObject(decodedData)));
            return decodedJsonifiableAdaptable;

        } else {
            String data = payloadJson.getValue("data").get().toString();
            return DittoJsonException.wrapJsonRuntimeException(
                    () -> ProtocolFactory.jsonifiableAdaptableFromJson(JsonFactory.newObject(data)));
        }
    }

    public boolean checkHeaders(final ExternalMessage message) {
        Map<String, String> headers = message.getHeaders();
        if (headers.get(CE_ID) == null || headers.get(CE_SOURCE) == null || headers.get(CE_TYPE) == null || headers.get(CE_SPECVERSION) == null) {
            System.out.println("This is not a Binary CloudEvent");
            return false;
        } else {
            System.out.println("This is a Binary CloudEvent");
            return true;
        }

    }

    @Override
    public List<Adaptable> map(final ExternalMessage message) {
        // extract message as String
        checkHeaders(message);
        System.out.println("The headers are " + message.getHeaders());
        System.out.println("The headers belong to class " + message.getHeaders().getClass());

        final String payload = extractPayloadAsString(message);
        if (validatePayload(payload)) {
            try {
                JsonifiableAdaptable adaptable = extractData(payload);
                return singletonList(ProtocolFactory.newAdaptableBuilder(adaptable).build());
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public DittoHeaders getAdditionalInboundHeaders(ExternalMessage message) {
        return DittoHeaders.empty();
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
