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
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.amqpbridge.mapping.ImmutablePayloadMapperMessage;
import org.eclipse.ditto.services.amqpbridge.mapping.MappingTemplate;
import org.eclipse.ditto.services.amqpbridge.mapping.PayloadMapperMessage;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/**
 * TODO doc
 */
final class DynamicRhinoPayloadMapper extends AbstractJavaScriptPayloadMapper {

    private final Context context;
    private final Scriptable scope;

    DynamicRhinoPayloadMapper(final JavaScriptPayloadMapperOptions options) {
        super(options);

        final RhinoContextFactory contextFactory = new RhinoContextFactory();
        context = contextFactory.makeContext();
        contextFactory.enterContext();
        scope = context.initStandardObjects();

        context.evaluateString(scope, TEMPLATE_VARS, "init-vars", 1, null);

        initLibraries();
    }

    @Override
    void loadJavascriptLibrary(final Reader reader, final String libraryName) {
        try {
            context.evaluateReader(scope, reader, libraryName, 1, null);
        } catch (final IOException e) {
            throw new IllegalStateException("Could not load script <" + libraryName + ">", e);
        }
    }

    @Override
    public Adaptable mapIncomingMessageToDittoAdaptable(final MappingTemplate template, final PayloadMapperMessage message) {

        final NativeObject headersObj = new NativeObject();
        message.getHeaders().forEach((key, value) -> headersObj.put(key, headersObj, value));
        ScriptableObject.putProperty(scope, MAPPING_HEADERS_VAR, headersObj);

        if (message.getRawData().isPresent()) {
            final ByteBuffer byteBuffer = message.getRawData().get();
            final byte[] array = byteBuffer.array();
            final NativeArray newArray = new NativeArray(array.length);
            for (int a=0; a < array.length; a++) {
                ScriptableObject.putProperty(newArray, a, array[a]);
            }
            ScriptableObject.putProperty(scope, MAPPING_BYTEARRAY_VAR, newArray);
        }

        ScriptableObject.putProperty(scope, MAPPING_STRING_VAR, message.getStringData().orElse(null));

        ScriptableObject.putProperty(scope, DITTO_PROTOCOL_JSON_VAR, new NativeObject());

        context.evaluateString(scope, template.getMappingTemplate(), "template", 1, null);

        final Object dittoProtocolJson = ScriptableObject.getProperty(scope, DITTO_PROTOCOL_JSON_VAR);
        final String dittoProtocolJsonStr = (String) NativeJSON.stringify(context, scope, dittoProtocolJson, null, null);

        final JsonObject jsonObject = JsonFactory.readFrom(dittoProtocolJsonStr).asObject();
        return ProtocolFactory.jsonifiableAdaptableFromJson(jsonObject);
    }

    @Override
    public PayloadMapperMessage mapOutgoingMessageFromDittoAdaptable(final MappingTemplate template,
            final Adaptable dittoProtocolAdaptable) {
        final JsonifiableAdaptable jsonifiableAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(dittoProtocolAdaptable);

        final Object nativeJsonObject = NativeJSON.parse(context, scope, jsonifiableAdaptable.toJsonString(), new NullCallable());
        ScriptableObject.putProperty(scope, DITTO_PROTOCOL_JSON_VAR, nativeJsonObject);

        context.evaluateString(scope, template.getMappingTemplate(), "template", 1, null);

        final String mappingString = ScriptableObject.getTypedProperty(scope, MAPPING_STRING_VAR, String.class);
        final Object mappingByteArray = ScriptableObject.getProperty(scope, MAPPING_BYTEARRAY_VAR);
        final Object mappingHeaders = ScriptableObject.getProperty(scope, MAPPING_HEADERS_VAR);

        final Map<String, String> headers = !(mappingHeaders instanceof Undefined) ? null : Collections.emptyMap();
        return new ImmutablePayloadMapperMessage(convertToByteBuffer(mappingByteArray), mappingString, headers);
    }


    /**
     *
     */
    private static class RhinoContextFactory extends ContextFactory {

        /**
         * Custom Context to store execution time.
         */
        private static class MyContext extends Context {

            private MyContext(final ContextFactory factory) {
                super(factory);
            }

            long startTime;
        }

        static {
            // Initialize GlobalFactory with custom factory
            ContextFactory.initGlobal(new RhinoContextFactory());
        }

        @Override
        protected Context makeContext() {
            final MyContext cx = new MyContext(this);
            // Use pure interpreter mode to allow for observeInstructionCount(Context, int) to work
            cx.setOptimizationLevel(-1);
            // Make Rhino runtime to call observeInstructionCount each 10000 bytecode instructions
            cx.setInstructionObserverThreshold(10000);
            return cx;
        }

        @Override
        public boolean hasFeature(Context cx, int featureIndex) {
            // Turn on maximum compatibility with MSIE scripts
            switch (featureIndex) {
                case Context.FEATURE_NON_ECMA_GET_YEAR:
                    return true;

                case Context.FEATURE_MEMBER_EXPR_AS_FUNCTION_NAME:
                    return true;

                case Context.FEATURE_RESERVED_KEYWORD_AS_IDENTIFIER:
                    return true;

                case Context.FEATURE_PARENT_PROTO_PROPERTIES:
                    return false;
            }
            return super.hasFeature(cx, featureIndex);
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            final MyContext mcx = (MyContext) cx;
            final long currentTime = System.currentTimeMillis();
            if (currentTime - mcx.startTime > MAX_SCRIPT_EXEC_TIME_MS) {
                // More then x seconds from Context creation time: it is time to stop the script.
                // Throw Error instance to ensure that script will never get control back through catch or finally.
                throw new Error();
            }
        }

        @Override
        protected Object doTopCall(final Callable callable, final Context cx, final Scriptable scope,
                final Scriptable thisObj, final Object[] args) {
            final MyContext mcx = (MyContext) cx;
            mcx.startTime = System.currentTimeMillis();

            return super.doTopCall(callable, cx, scope, thisObj, args);
        }

    }

    /**
     *
     */
    private static class NullCallable implements Callable
    {
        @Override
        public Object call(Context context, Scriptable scope, Scriptable holdable, Object[] objects)
        {
            return objects[1];
        }
    }
}
