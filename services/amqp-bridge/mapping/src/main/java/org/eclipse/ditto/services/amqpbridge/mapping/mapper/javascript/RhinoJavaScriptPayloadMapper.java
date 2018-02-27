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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.script.Bindings;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMapperMessage;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperConfiguration;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMappers;
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
final class RhinoJavaScriptPayloadMapper implements PayloadMapper {

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

    private final ContextFactory contextFactory;
    private final Scriptable scope;

    @Nullable private JavaScriptMessageMapperConfiguration configuration;


    RhinoJavaScriptPayloadMapper() {
        this(PayloadMappers.createMapperOptionsBuilder(Collections.emptyMap()).build());
    }

    RhinoJavaScriptPayloadMapper(final MessageMapperConfiguration configuration) {
        configure(configuration);
        contextFactory = new RhinoContextFactory();

        // create scope once and load the required libraries in order to get best performance:
        scope = (Scriptable) contextFactory.call(cx -> {
            final Scriptable scope = cx.initStandardObjects();
            cx.evaluateString(scope, INIT_VARS_TEMPLATE, "init-vars", 1, null);
            initLibraries(cx, scope);
            return scope;
        });
    }

    @Override
    public List<String> getSupportedContentTypes() {
        return Collections.singletonList(".*"); // matches all contentTypes (via regex)
    }

    @Override
    public void configure(final MessageMapperConfiguration options) {
        this.configuration = new ImmutableJavaScriptMessageMapperMapperOptions.Builder(options.getProperties()).build();
    }

    @Override
    public Adaptable mapIncoming(final PayloadMapperMessage message) {

        return (Adaptable) contextFactory.call(cx -> {
            final NativeObject headersObj = new NativeObject();
            message.getHeaders().forEach((key, value) -> headersObj.put(key, headersObj, value));
            ScriptableObject.putProperty(scope, MAPPING_HEADERS_VAR, headersObj);

            if (message.getRawData().isPresent()) {
                final ByteBuffer byteBuffer = message.getRawData().get();
                final byte[] array = byteBuffer.array();
                final NativeArray newArray = new NativeArray(array.length);
                for (int a = 0; a < array.length; a++) {
                    ScriptableObject.putProperty(newArray, a, array[a]);
                }
                ScriptableObject.putProperty(scope, MAPPING_BYTEARRAY_VAR, newArray);
            }

            ScriptableObject.putProperty(scope, MAPPING_CONTENT_TYPE_VAR, message.getContentType());
            ScriptableObject.putProperty(scope, MAPPING_STRING_VAR, message.getStringData().orElse(null));
            ScriptableObject.putProperty(scope, DITTO_PROTOCOL_JSON_VAR, new NativeObject());

            cx.evaluateString(scope, getConfiguration().flatMap(JavaScriptMessageMapperConfiguration::getIncomingMappingScript)
                    .orElse(""), "template", 1, null);

            final Object dittoProtocolJson = ScriptableObject.getProperty(scope, DITTO_PROTOCOL_JSON_VAR);
            final String dittoProtocolJsonStr =
                    (String) NativeJSON.stringify(cx, scope, dittoProtocolJson, null, null);

            final JsonObject jsonObject = JsonFactory.readFrom(dittoProtocolJsonStr).asObject();
            return ProtocolFactory.jsonifiableAdaptableFromJson(jsonObject);
        });
    }

    @Override
    public PayloadMapperMessage mapOutgoing(final Adaptable dittoProtocolAdaptable) {

        final JsonifiableAdaptable jsonifiableAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(dittoProtocolAdaptable);

        return (PayloadMapperMessage) contextFactory.call(cx ->{
            final Object nativeJsonObject =
                        NativeJSON.parse(cx, scope, jsonifiableAdaptable.toJsonString(), new NullCallable());
            ScriptableObject.putProperty(scope, DITTO_PROTOCOL_JSON_VAR, nativeJsonObject);

            cx.evaluateString(scope, getConfiguration().flatMap(JavaScriptMessageMapperConfiguration::getOutgoingMappingScript)
                    .orElse(""), "template", 1, null);

            final String contentType = ScriptableObject.getTypedProperty(scope, MAPPING_CONTENT_TYPE_VAR, String.class);
            final String mappingString = ScriptableObject.getTypedProperty(scope, MAPPING_STRING_VAR, String.class);
            final Object mappingByteArray = ScriptableObject.getProperty(scope, MAPPING_BYTEARRAY_VAR);
            final Object mappingHeaders = ScriptableObject.getProperty(scope, MAPPING_HEADERS_VAR);

            final Map<String, String> headers = !(mappingHeaders instanceof Undefined) ? null : Collections.emptyMap();
            return PayloadMappers.createPayloadMapperMessage(contentType, convertToByteBuffer(mappingByteArray),
                    mappingString, headers != null ? headers : Collections.emptyMap());
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

    private void loadJavascriptLibrary(final Context cx, final Scriptable scope, final Reader reader, final String libraryName) {

        try {
            cx.evaluateReader(scope, reader, libraryName, 1, null);
        } catch (final IOException e) {
            throw new IllegalStateException("Could not load script <" + libraryName + ">", e);
        }
    }

    private static ByteBuffer convertToByteBuffer(final Object obj) {
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
                            return ByteBuffer.wrap(baos.toByteArray());
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
            return ByteBuffer.wrap(baos.toByteArray());
        }
        return null;
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
