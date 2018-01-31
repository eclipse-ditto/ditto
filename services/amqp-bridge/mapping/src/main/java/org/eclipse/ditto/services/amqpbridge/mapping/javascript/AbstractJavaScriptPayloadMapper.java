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

import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.script.Bindings;

import org.eclipse.ditto.services.amqpbridge.mapping.PayloadMapper;

/**
 * TODO doc
 */
abstract class AbstractJavaScriptPayloadMapper implements PayloadMapper {

    private static final String WEBJARS_BYTEBUFFER = "/META-INF/resources/webjars/bytebuffer/5.0.1/dist/bytebuffer.js";
    private static final String WEBJARS_LONG = "/META-INF/resources/webjars/long/3.2.0/dist/long.js";
    private static final String WEBJARS_MUSTACHE = "/META-INF/resources/webjars/mustache/2.3.0/mustache.js";

    static final String DITTO_PROTOCOL_JSON_VAR = "dittoProtocolJson";
    static final String MAPPING_STRING_VAR = "mappingString";
    static final String MAPPING_BYTEARRAY_VAR = "mappingByteArray";
    static final String MAPPING_HEADERS_VAR = "mappingHeaders";

    static final String TEMPLATE_VARS = "var " +
            MAPPING_STRING_VAR + "," +
            MAPPING_BYTEARRAY_VAR + "," +
            MAPPING_HEADERS_VAR + "," +
            DITTO_PROTOCOL_JSON_VAR + "={}" +
            ";";

    static final int MAX_SCRIPT_EXEC_TIME_MS = 10 * 1000;

    private final JavaScriptPayloadMapperOptions options;

    AbstractJavaScriptPayloadMapper(final JavaScriptPayloadMapperOptions options) {
        this.options = options;
    }

    void initLibraries() {
        if (options.isLoadBytebufferJS()) {
            loadJavascriptLibrary(new InputStreamReader(getClass().getResourceAsStream(WEBJARS_BYTEBUFFER)),
                    "bytebuffer.js");
        }
        if (options.isLoadLongJS()) {
            loadJavascriptLibrary(new InputStreamReader(getClass().getResourceAsStream(WEBJARS_LONG)), "long.js");
        }
        if (options.isLoadMustacheJS()) {
            loadJavascriptLibrary(new InputStreamReader(getClass().getResourceAsStream(WEBJARS_MUSTACHE)),
                    "mustache.js");
        }
    }

    abstract void loadJavascriptLibrary(Reader reader, String libraryName);

    static ByteBuffer convertToByteBuffer(final Object obj) {
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

    static String convertToJsonArrayString(final ByteBuffer byteBuffer) {
        final byte[] bytes = Optional.ofNullable(byteBuffer).map(ByteBuffer::array).orElse(null);
        if (bytes != null) {
            final StringBuilder sb = new StringBuilder("[");
            for (final byte aByte : bytes) {
                sb.append(aByte);
                sb.append(",");
            }
            sb.deleteCharAt(sb.length()-1);
            sb.append("]");
            return sb.toString();
        }
        return null;
    }
}
