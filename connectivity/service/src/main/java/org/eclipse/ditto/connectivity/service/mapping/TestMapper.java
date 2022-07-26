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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import akka.http.javadsl.model.ContentTypes;
import org.eclipse.ditto.base.model.common.DittoConstants;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.json.*;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaderDefinition;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableAdaptable;
import org.eclipse.ditto.protocol.ProtocolFactory;
import scala.runtime.Static;

/**
 * A message mapper implementation for the Ditto Protocol.
 * Expects messages to contain a JSON serialized Ditto Protocol message.
 */
@PayloadMapper(
        alias = {"TestMapper",
                // legacy full qualified name
                "org.eclipse.ditto.connectivity.service.mapping.TestMapper"})
public final class TestMapper extends AbstractMessageMapper {

//    private static final JsonKey MESSAGES_JSON_KEY = JsonKey.of("messages");
//    private static final String OUTGOING_CONTENT_TYPE_KEY = "outgoingContentType";
//    private static final String INCOMING_MESSAGE_HEADERS = "incomingMessageHeaders";
//    private static String getFromHeaderOrDefault(final String headerKey, final String defaultValue) {
//        return "{{header:" + headerKey + "|fn:default('" + defaultValue + "')}}";
//    }
//
//    private static String asPlaceholder(final MessageHeaderDefinition messageHeaderDefinition) {
//        return String.format("{{header:%s}}", messageHeaderDefinition.getKey());
//    }

    final static String CE_ID = "ce-id";
    final static String CE_TYPE = "ce-type";
    final static String CE_SOURCE = "ce-source";
    final static String CE_TIME = "ce-time";

//    private static String ValidateHeaders()

//    private static final Map<String, String> DEFAULT_INCOMING_HEADERS = Map.of(
//            DittoHeaderDefinition.CONTENT_TYPE.getKey(),
//            getFromHeaderOrDefault(DittoHeaderDefinition.CONTENT_TYPE.getKey(),
//                    ContentTypes.APPLICATION_OCTET_STREAM.toString()),
//            MessageHeaderDefinition.DIRECTION.getKey(),
//            getFromHeaderOrDefault(MessageHeaderDefinition.DIRECTION.getKey(), MessageDirection.TO.toString()),
//            MessageHeaderDefinition.THING_ID.getKey(), asPlaceholder(MessageHeaderDefinition.THING_ID),
//            MessageHeaderDefinition.SUBJECT.getKey(), asPlaceholder(MessageHeaderDefinition.SUBJECT),
//            MessageHeaderDefinition.STATUS_CODE.getKey(), asPlaceholder(MessageHeaderDefinition.STATUS_CODE),
//            MessageHeaderDefinition.FEATURE_ID.getKey(), asPlaceholder(MessageHeaderDefinition.FEATURE_ID)
//    );

    static final JsonObject DEFAULT_OPTIONS = JsonObject.newBuilder()
            .set(MessageMapperConfiguration.CONTENT_TYPE_BLOCKLIST,
                    String.join(",", "application/vnd.eclipse-hono-empty-notification",
                            "application/vnd.eclipse-hono-device-provisioning-notification",
                            "application/vnd.eclipse-hono-dc-notification+json",
                            "application/vnd.eclipse-hono-delivery-failure-notification+json"
                    ))
            .build();

    /**
     * The context representing this mapper
     */
    public static final MappingContext CONTEXT = ConnectivityModelFactory.newMappingContextBuilder(
            TestMapper.class.getCanonicalName(),
            DEFAULT_OPTIONS
    ).build();

    final String SPECVERSION = "specversion";
    final String ID = "id";
    final String SOURCE = "source";
    final String TYPE = "type";
//    public static void main(String[] args) {
//        String test ="{\"specversion\": \"1.0\", \"id\":\"3212e\", \"source\":\"http:somesite.com\",\"type\":\"com.site.com\",\"data\":{\"topic\":\"org.eclipse.ditto/sensor/things/twin/commands/modify\",\"path\":\"/\",\"value\":53}}";
//        extractData(test);
//    }
    public boolean validatePayload(String payload){
        Map<String, Object> incomingMessagePayload = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final JsonObject jsonObject = JsonFactory.newObject(payload);
        List<JsonKey> key = jsonObject.getKeys();
        for(JsonKey keyIterator: key){
            Object value = jsonObject.getValue(keyIterator).orElse(null);
                if(value!=null) {
                    incomingMessagePayload.put(keyIterator.toString(), value);
                }
        }
        if(incomingMessagePayload.get("specversion")==null || incomingMessagePayload.get("id")==null || incomingMessagePayload.get("source")==null || incomingMessagePayload.get("type")==null){
            System.out.println("This is not a CloudEvent");
            return false;
        }
        else{
            System.out.println("This is a CloudEvent");
            return true;
        }

    }

    public static JsonifiableAdaptable extractData(final String message){
        Map<String,String> payloadMap = new HashMap<>();
        final JsonObject payloadJson = JsonFactory.newObject(message);
        List<JsonKey> payloadKey = payloadJson.getKeys();
        for(JsonKey key: payloadKey){
            Object value = payloadJson.getValue(key).orElse(null);
                if(value!=null){
                    payloadMap.put(key.toString(),value.toString());
                }
        }
        String data = payloadMap.get("data");
        System.out.println(data);
        final JsonifiableAdaptable jsonifiableAdaptable =DittoJsonException.wrapJsonRuntimeException(() ->
                ProtocolFactory.jsonifiableAdaptableFromJson(JsonFactory.newObject(data))
        );
        return jsonifiableAdaptable;
//        System.out.println(singletonList(
//                ProtocolFactory.newAdaptableBuilder(jsonifiableAdaptable).build()));
    }
    @Override
    public List<Adaptable> map(final ExternalMessage message) {

        final String payload = extractPayloadAsString(message);
        if(validatePayload(payload)){
            System.out.println("This is a CloudEvent");
            JsonifiableAdaptable adaptable = extractData(payload);
            System.out.println(singletonList(
                    ProtocolFactory.newAdaptableBuilder(adaptable).build()));
            return singletonList(
                    ProtocolFactory.newAdaptableBuilder(adaptable).build());
        }

        else{
            try {
                throw new Exception("This isn't a CloudEvent");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        System.out.println("TestMapper Map for internal message is " + List.of(ExternalMessageFactory.newExternalMessageBuilder(getExternalDittoHeaders(adaptable))
                .withTopicPath(adaptable.getTopicPath())
                .withText(getJsonString(adaptable))
                .asResponse(isResponse(adaptable))
                .asError(isError(adaptable))
                .build()));
        return List.of(ExternalMessageFactory.newExternalMessageBuilder(getExternalDittoHeaders(adaptable))
                .withTopicPath(adaptable.getTopicPath())
                .withText(getJsonString(adaptable))
                .asResponse(isResponse(adaptable))
                .asError(isError(adaptable))
                .build());
    }

    private static DittoHeaders getExternalDittoHeaders(final Adaptable adaptable) {
        System.out.println("TestMapper DittoHeaders.builder is " +DittoHeaders.newBuilder()
                .contentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)
                .correlationId(adaptable.getDittoHeaders().getCorrelationId().orElse(null))
                .build());
        return DittoHeaders.newBuilder()
                .contentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)
                .correlationId(adaptable.getDittoHeaders().getCorrelationId().orElse(null))
                .build();
    }

    private static String getJsonString(final Adaptable adaptable) {
        final var jsonifiableAdaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);
        System.out.println("TestMapper jsonifiableAdaptable.toJsonString() is " +jsonifiableAdaptable.toJsonString());
        return jsonifiableAdaptable.toJsonString();
    }

    @Override
    public JsonObject getDefaultOptions() {
        return DEFAULT_OPTIONS;
    }

}
