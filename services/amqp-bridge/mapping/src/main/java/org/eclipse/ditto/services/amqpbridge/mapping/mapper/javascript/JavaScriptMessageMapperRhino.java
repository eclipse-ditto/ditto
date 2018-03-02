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
package org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.script.Bindings;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.amqpbridge.AmqpBridgeModelFactory;
import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.model.amqpbridge.ExternalMessageBuilder;
import org.eclipse.ditto.model.amqpbridge.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperConfiguration;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMappers;
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
 * This mapper executes its mapping methods on the <b>current thread</b>. The caller should be aware of that.
 */
final class JavaScriptMessageMapperRhino implements MessageMapper {

    private static final String WEBJARS_PATH = "/META-INF/resources/webjars";

    private static final String WEBJARS_BYTEBUFFER = WEBJARS_PATH + "/bytebuffer/5.0.0/dist/bytebuffer.min.js";
    private static final String WEBJARS_LONG = WEBJARS_PATH + "/long/3.2.0/dist/long.min.js";
    private static final String WEBJARS_MUSTACHE = WEBJARS_PATH + "/mustache/2.3.0/mustache.min.js";

    private static final String DITTO_PROTOCOL_JSON_VAR = "ditto_protocolJson";
    private static final String MAPPING_CONTENT_TYPE_VAR = "ditto_mappingContentType";
    private static final String MAPPING_STRING_VAR = "ditto_mappingString";
    private static final String MAPPING_BYTEARRAY_VAR = "ditto_mappingByteArray";
    private static final String MAPPING_HEADERS_VAR = "ditto_mappingHeaders";

    private static final String INIT_VARS_TEMPLATE = "var " +
            MAPPING_CONTENT_TYPE_VAR + "=undefined," +
            MAPPING_STRING_VAR + "=undefined," +
            MAPPING_BYTEARRAY_VAR + "=undefined," +
            MAPPING_HEADERS_VAR + "=undefined," +
            DITTO_PROTOCOL_JSON_VAR + "={}" +
            ";";

    @Nullable
    private ContextFactory contextFactory;
    @Nullable
    private Scriptable scope;

    @Nullable private JavaScriptMessageMapperConfiguration configuration;

    JavaScriptMessageMapperRhino() {
        // no-op
    }

    @Override
    public String getContentType() {
        return Optional.ofNullable(configuration)
                .flatMap(MessageMapperConfiguration::findContentType)
                .orElseThrow(() -> MessageMapperConfigurationInvalidException
                        .newBuilder(ExternalMessage.CONTENT_TYPE_HEADER)
                        .build());
    }

    @Override
    public void configure(final MessageMapperConfiguration options) {
        this.configuration = new ImmutableJavaScriptMessageMapperConfiguration.Builder(options.getProperties()).build();
        contextFactory = new RhinoContextFactory();

        // create scope once and load the required libraries in order to get best performance:
        scope = (Scriptable) contextFactory.call(cx -> {
            final Scriptable scope = cx.initSafeStandardObjects();
            cx.evaluateString(scope, INIT_VARS_TEMPLATE, "init-vars", 1, null);
            initLibraries(cx, scope);
            return scope;
        });
    }

    @Override
    public Adaptable map(final ExternalMessage message) {

        return (Adaptable) contextFactory.call(cx -> {
            cx.evaluateString(scope, INIT_VARS_TEMPLATE, "init-vars", 1, null);

            final NativeObject headersObj = new NativeObject();
            message.getHeaders().forEach((key, value) -> headersObj.put(key, headersObj, value));
            ScriptableObject.putProperty(scope, MAPPING_HEADERS_VAR, headersObj);

            if (message.getBytePayload().isPresent()) {
                final ByteBuffer byteBuffer = message.getBytePayload().get();
                final byte[] array = byteBuffer.array();
                final NativeArray newArray = new NativeArray(array.length);
                for (int a = 0; a < array.length; a++) {
                    ScriptableObject.putProperty(newArray, a, array[a]);
                }
                ScriptableObject.putProperty(scope, MAPPING_BYTEARRAY_VAR, newArray);
            }

            ScriptableObject.putProperty(scope, MAPPING_CONTENT_TYPE_VAR, message.getHeaders().get(
                    ExternalMessage.CONTENT_TYPE_HEADER));
            ScriptableObject.putProperty(scope, MAPPING_STRING_VAR, message.getTextPayload().orElse(null));
            ScriptableObject.putProperty(scope, DITTO_PROTOCOL_JSON_VAR, new NativeObject());

            cx.evaluateString(scope,
                    getConfiguration().flatMap(JavaScriptMessageMapperConfiguration::getIncomingMappingScript)
                            .orElse(""), "template", 1, null);

            final Object dittoProtocolJson = ScriptableObject.getProperty(scope, DITTO_PROTOCOL_JSON_VAR);
            final String dittoProtocolJsonStr =
                    (String) NativeJSON.stringify(cx, scope, dittoProtocolJson, null, null);

            return DittoJsonException.wrapJsonRuntimeException(() -> {
                final JsonObject jsonObject = JsonFactory.readFrom(dittoProtocolJsonStr).asObject();
                return ProtocolFactory.jsonifiableAdaptableFromJson(jsonObject);
            });
        });
    }

    @Override
    public ExternalMessage map(final Adaptable adaptable) {
        final JsonifiableAdaptable jsonifiableAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);

        return (ExternalMessage) contextFactory.call(cx -> {
            cx.evaluateString(scope, INIT_VARS_TEMPLATE, "init-vars", 1, null);

            final Object nativeJsonObject =
                    NativeJSON.parse(cx, scope, jsonifiableAdaptable.toJsonString(), new NullCallable());
            ScriptableObject.putProperty(scope, DITTO_PROTOCOL_JSON_VAR, nativeJsonObject);

            cx.evaluateString(scope,
                    getConfiguration().flatMap(JavaScriptMessageMapperConfiguration::getOutgoingMappingScript)
                            .orElse(""), "template", 1, null);

            final Object contentType = ScriptableObject.getProperty(scope, MAPPING_CONTENT_TYPE_VAR);
            final Object mappingString = ScriptableObject.getProperty(scope, MAPPING_STRING_VAR);
            final Object mappingByteArray = ScriptableObject.getProperty(scope, MAPPING_BYTEARRAY_VAR);
            final Object mappingHeaders = ScriptableObject.getProperty(scope, MAPPING_HEADERS_VAR);

            final Map<String, String> headers;
            if (mappingHeaders != null && !(mappingHeaders instanceof Undefined)) {
                headers = new HashMap<>();
                final Map jsHeaders = (Map) mappingHeaders;
                jsHeaders.forEach((key, value) -> headers.put((String) key, value.toString()));
            } else {
                headers = Collections.emptyMap();
            }

            final ExternalMessageBuilder messageBuilder = AmqpBridgeModelFactory.newExternalMessageBuilder(headers,
                    MessageMappers.determineMessageType(adaptable));

            if (!(contentType instanceof Undefined)) {
                messageBuilder.withAdditionalHeaders(ExternalMessage.CONTENT_TYPE_HEADER,
                        ((CharSequence) contentType).toString());
            }

            final Optional<ByteBuffer> byteBuffer = convertToByteBuffer(mappingByteArray);
            if (byteBuffer.isPresent()) {
                messageBuilder.withBytes(byteBuffer.get());
            } else if (!(mappingString instanceof Undefined)) {
                messageBuilder.withText(((CharSequence) mappingString).toString());
            }

            return messageBuilder.build();
        });
    }


    private void initLibraries(final Context cx, final Scriptable scope) {
        if (getConfiguration().map(JavaScriptMessageMapperConfiguration::isLoadBytebufferJS).orElse(false)) {
            loadJavascriptLibrary(cx, scope, new InputStreamReader(getClass().getResourceAsStream(WEBJARS_BYTEBUFFER)),
                    "bytebuffer.js");
        }
        if (getConfiguration().map(JavaScriptMessageMapperConfiguration::isLoadLongJS).orElse(false)) {
            loadJavascriptLibrary(cx, scope, new InputStreamReader(getClass().getResourceAsStream(WEBJARS_LONG)),
                    "long.js");
        }
        if (getConfiguration().map(JavaScriptMessageMapperConfiguration::isLoadMustacheJS).orElse(false)) {
            loadJavascriptLibrary(cx, scope, new InputStreamReader(getClass().getResourceAsStream(WEBJARS_MUSTACHE)),
                    "mustache.js");
        }
    }

    private Optional<JavaScriptMessageMapperConfiguration> getConfiguration() {
        return Optional.ofNullable(configuration);
    }

    private void loadJavascriptLibrary(final Context cx, final Scriptable scope, final Reader reader,
            final String libraryName) {

        try {
            cx.evaluateReader(scope, reader, libraryName, 1, null);
        } catch (final IOException e) {
            throw new IllegalStateException("Could not load script <" + libraryName + ">", e);
        }
    }

    private static Optional<ByteBuffer> convertToByteBuffer(final Object obj) {
        if (obj instanceof Bindings) {
            try {
                final Class<?> cls = Class.forName("jdk.nashorn.api.scripting.ScriptObjectMirror");
                if (cls.isAssignableFrom(obj.getClass())) {
                    final Method isArray = cls.getMethod("isArray");
                    final Object result = isArray.invoke(obj);
                    if (result != null && result.equals(true)) {
                        final Method values = cls.getMethod("values");
                        final Object vals = values.invoke(obj);
                        if (vals instanceof Collection) {
                            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            final Collection coll = (Collection) vals;
                            coll.forEach(e -> baos.write(((Number) e).intValue()));
                            return Optional.of(ByteBuffer.wrap(baos.toByteArray()));
                        }
                    }
                }
            } catch (final ClassNotFoundException | NoSuchMethodException | SecurityException
                    | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new IllegalStateException("Could not retrieve array values", e);
            }
        }
        if (obj instanceof List<?>) {
            final List<?> list = (List<?>) obj;
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            list.forEach(e -> baos.write(((Number) e).intValue()));
            return Optional.of(ByteBuffer.wrap(baos.toByteArray()));
        }
        return Optional.empty();
    }

    /**
     *
     */
    private static class NullCallable implements Callable {

        @Override
        public Object call(Context context, Scriptable scope, Scriptable holdable, Object[] objects) {
            return objects[1];
        }
    }
}
