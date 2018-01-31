/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.amqpbridge.mapping.javascript;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.script.ScriptException;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.amqpbridge.mapping.ImmutablePayloadMapperMessage;
import org.eclipse.ditto.services.amqpbridge.mapping.MappingTemplate;
import org.eclipse.ditto.services.amqpbridge.mapping.PayloadMapperMessage;

import delight.nashornsandbox.NashornSandbox;
import delight.nashornsandbox.NashornSandboxes;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

/**
 * TODO doc
 */
final class DynamicNashornSandboxPayloadMapper extends AbstractJavaScriptPayloadMapper {

    private final NashornSandbox nashornSandbox;

    DynamicNashornSandboxPayloadMapper(final JavaScriptPayloadMapperOptions options) {
        super(options);

        nashornSandbox = NashornSandboxes.create();

        nashornSandbox.setMaxCPUTime(MAX_SCRIPT_EXEC_TIME_MS);
        nashornSandbox.setMaxMemory(10000 * 1024);
        nashornSandbox.setMaxPerparedStatements(30); // because preparing scripts for execution is expensive
        nashornSandbox.setExecutor(Executors.newSingleThreadExecutor());

        nashornSandbox.allowNoBraces(true); // true required for loading mustache.js
        nashornSandbox.allowExitFunctions(false);
        nashornSandbox.allowLoadFunctions(false);
        nashornSandbox.allowPrintFunctions(false);
        nashornSandbox.allowReadFunctions(false);
        nashornSandbox.allowGlobalsObjects(false);

        try {
            nashornSandbox.eval(TEMPLATE_VARS);
        } catch (final ScriptException e) {
            throw new IllegalStateException(e);
        }

        initLibraries();
    }

    @Override
    void loadJavascriptLibrary(final Reader reader, final String libraryName) {
        try {
            final StringBuilder buffer = new StringBuilder();
            char[] arr = new char[8 * 1024];
            int numCharsRead;
            while ((numCharsRead = reader.read(arr, 0, arr.length)) != -1) {
                buffer.append(arr, 0, numCharsRead);
            }
            reader.close();
            final String targetString = buffer.toString();
            nashornSandbox.eval(targetString);
        } catch (final ScriptException | IOException e) {
            throw new IllegalStateException("Could not load script <" + libraryName + ">", e);
        }
    }

    @Override
    public Adaptable mapIncomingMessageToDittoAdaptable(final MappingTemplate template, final PayloadMapperMessage message) {

        nashornSandbox.inject(MAPPING_HEADERS_VAR, message.getHeaders());
        final String bytesString = convertToJsonArrayString(message.getRawData().orElse(null));
        try {
            nashornSandbox.eval(MAPPING_BYTEARRAY_VAR + " = " + bytesString + ";");
        } catch (ScriptException e) {
            e.printStackTrace();
        }
        nashornSandbox.inject(MAPPING_STRING_VAR, message.getStringData().orElse(null));

        try {
            nashornSandbox.eval(DITTO_PROTOCOL_JSON_VAR + "={};"); // reset variable

            nashornSandbox.eval(template.getMappingTemplate());
            final String dittoProtocolJsonStr = (String) nashornSandbox.eval(
                    "JSON.stringify(" + DITTO_PROTOCOL_JSON_VAR + ");");
            final JsonObject jsonObject = JsonFactory.readFrom(dittoProtocolJsonStr).asObject();
            return ProtocolFactory.jsonifiableAdaptableFromJson(jsonObject);
        } catch (final ScriptException e) {
            e.printStackTrace();
            // TODO throw custom exception
        }
        return null;
    }

    @Override
    public PayloadMapperMessage mapOutgoingMessageFromDittoAdaptable(final MappingTemplate template,
            final Adaptable dittoProtocolAdaptable) {
        final JsonifiableAdaptable jsonifiableAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(dittoProtocolAdaptable);

        try {
            final ScriptObjectMirror adaptableJsonJsObj = (ScriptObjectMirror) nashornSandbox.eval(
                    "JSON.parse('" + jsonifiableAdaptable.toJsonString() + "');");

            nashornSandbox.inject(DITTO_PROTOCOL_JSON_VAR, adaptableJsonJsObj);
            nashornSandbox.eval(template.getMappingTemplate());

            final Object mappingString = nashornSandbox.eval(MAPPING_STRING_VAR);
            final Object mappingByteArray = nashornSandbox.eval(MAPPING_BYTEARRAY_VAR);
            final ScriptObjectMirror mappingHeaders =
                    (ScriptObjectMirror) nashornSandbox.eval(MAPPING_HEADERS_VAR);
            final Map<String, String> headers = Optional.ofNullable(
                    mappingHeaders)
                    .map(h -> h.entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())))
                    .orElse(Collections.emptyMap());

            final String mappingResultString = (mappingString instanceof String) ? (String) mappingString : null;
            return new ImmutablePayloadMapperMessage(convertToByteBuffer(mappingByteArray), mappingResultString, headers);
        } catch (final ScriptException e) {
            e.printStackTrace();
            // TODO throw custom exception
        }
        return null;
    }

}
