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

import java.io.Reader;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.amqpbridge.mapping.ImmutablePayloadMapperMessage;
import org.eclipse.ditto.services.amqpbridge.mapping.MappingTemplate;
import org.eclipse.ditto.services.amqpbridge.mapping.PayloadMapperMessage;

import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

/**
 * TODO doc
 */
final class DynamicNashornPayloadMapper extends AbstractJavaScriptPayloadMapper {

    private final ScriptEngine engine;
    private final Invocable invocable;
    private final Object jsJSON;

    DynamicNashornPayloadMapper(final JavaScriptPayloadMapperOptions options) {
        super(options);

        // this one simply forbids use of any java classes, including reflection
        engine = new NashornScriptEngineFactory().getScriptEngine(
                new String[]{"--no-java", "-strict", "--no-syntax-extensions"}, // disable direct access to Java API
                null, // ClassLoader
                string -> false); // forbids use of any java classes, including reflection
        final Bindings engineBindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        // define which JS methods are not usable by removing them from engine global scope:
        engineBindings.remove("echo"); // prevent echoing to SOUT - for what would that be useful?
        engineBindings.remove("print"); // prevent printing to SOUT - for what would that be useful?
        engineBindings.remove("load"); // prevent loading other .js files (e.g. via URL)
        engineBindings.remove("loadWithNewGlobal"); // prevent loading other .js files (e.g. via URL)
        engineBindings.remove("exit"); // prevent exiting the JVM
        engineBindings.remove("quit"); // prevent quitting the JVM
        invocable = (Invocable) engine;

        initLibraries();

        try {
            jsJSON = engine.eval("JSON");
            engine.eval(TEMPLATE_VARS);
        } catch (ScriptException e) {
            e.printStackTrace();
            throw new IllegalStateException("Could not exec script", e);
        }
    }

    @Override
    void loadJavascriptLibrary(final Reader reader, final String libraryName) {
        try {
            engine.eval(reader);
        } catch (final ScriptException e) {
            throw new IllegalStateException("Could not load script <" + libraryName + ">", e);
        }
    }

    @Override
    public Adaptable mapIncomingMessageToDittoAdaptable(final MappingTemplate template,
            final PayloadMapperMessage message) {

        engine.put(MAPPING_HEADERS_VAR, message.getHeaders());

        final String bytesString = convertToJsonArrayString(message.getRawData().orElse(null));
        try {
            engine.eval(MAPPING_BYTEARRAY_VAR + " = " + bytesString + ";");
        } catch (ScriptException e) {
            e.printStackTrace();
        }
        engine.put(MAPPING_STRING_VAR, message.getStringData().orElse(null));

        try {
            engine.eval(DITTO_PROTOCOL_JSON_VAR + "={};"); // reset variable

            engine.eval(template.getMappingTemplate());
            final String dittoProtocolJsonStr = (String) engine.eval(
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
            final ScriptObjectMirror adaptableJsonJsObj =
                    (ScriptObjectMirror) invocable.invokeMethod(jsJSON, "parse", jsonifiableAdaptable.toJsonString());

            engine.put(DITTO_PROTOCOL_JSON_VAR, adaptableJsonJsObj);
            engine.eval(template.getMappingTemplate());
            final Object mappingString = engine.eval(MAPPING_STRING_VAR);
            final Object mappingByteArray = engine.eval(MAPPING_BYTEARRAY_VAR);
            final ScriptObjectMirror mappingHeaders = (ScriptObjectMirror) engine.eval(MAPPING_HEADERS_VAR);
            final Map<String, String> headers = Optional.ofNullable(mappingHeaders)
                    .map(h -> h.entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()))
                    )
                    .orElse(Collections.emptyMap());

            final String mappingResultString = (mappingString instanceof String) ? (String) mappingString : null;
            return new ImmutablePayloadMapperMessage(convertToByteBuffer(mappingByteArray), mappingResultString,
                    headers);
        } catch (final ScriptException | NoSuchMethodException e) {
            e.printStackTrace();
            // TODO throw custom exception
        }
        return null;
    }

}
