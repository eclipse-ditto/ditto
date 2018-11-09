/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.mapping.javascript;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.script.Bindings;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.typedarrays.NativeArrayBuffer;

/**
 * Mapping function for outgoing messages based on JavaScript.
 */
public class ScriptedOutgoingMapping implements MappingFunction<Adaptable, Optional<ExternalMessage>> {

    private static final String EXTERNAL_MESSAGE_HEADERS = "headers";
    private static final String EXTERNAL_MESSAGE_CONTENT_TYPE = "contentType";
    private static final String EXTERNAL_MESSAGE_TEXT_PAYLOAD = "textPayload";
    private static final String EXTERNAL_MESSAGE_BYTE_PAYLOAD = "bytePayload";

    private static final String OUTGOING_FUNCTION_NAME = "mapFromDittoProtocolMsgWrapper";

    @Nullable
    private ContextFactory contextFactory;
    @Nullable
    private Scriptable scope;

    ScriptedOutgoingMapping(@Nullable final ContextFactory contextFactory, @Nullable final Scriptable scope) {
        this.contextFactory = contextFactory;
        this.scope = scope;
    }

    @Override
    public Optional<ExternalMessage> apply(final Adaptable adaptable) {
        try {
            final JsonifiableAdaptable jsonifiableAdaptable = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);
            return Optional.ofNullable((ExternalMessage) contextFactory.call(cx -> {
                final Object dittoProtocolMessage =
                        NativeJSON.parse(cx, scope, jsonifiableAdaptable.toJsonString(), new NullCallable());

                final org.mozilla.javascript.Function
                        mapFromDittoProtocolMsgWrapper = (org.mozilla.javascript.Function) scope.get(OUTGOING_FUNCTION_NAME, scope);
                final NativeObject result =
                        (NativeObject) mapFromDittoProtocolMsgWrapper.call(cx, scope, scope,
                                new Object[]{dittoProtocolMessage});

                if (result == null) {
                    // return null if result is null causing the wrapping Optional to be empty
                    return null;
                }

                final Object contentType = result.get(EXTERNAL_MESSAGE_CONTENT_TYPE);
                final Object textPayload = result.get(EXTERNAL_MESSAGE_TEXT_PAYLOAD);
                final Object bytePayload = result.get(EXTERNAL_MESSAGE_BYTE_PAYLOAD);
                final Object mappingHeaders = result.get(EXTERNAL_MESSAGE_HEADERS);

                final Map<String, String> headers;
                if (mappingHeaders != null && !(mappingHeaders instanceof Undefined)) {
                    headers = new HashMap<>();
                    final Map jsHeaders = (Map) mappingHeaders;
                    jsHeaders.forEach((key, value) -> headers.put((String) key, value.toString()));
                } else {
                    headers = Collections.emptyMap();
                }

                final ExternalMessageBuilder messageBuilder =
                        ExternalMessageFactory.newExternalMessageBuilder(headers);

                if (!(contentType instanceof Undefined)) {
                    messageBuilder.withAdditionalHeaders(ExternalMessage.CONTENT_TYPE_HEADER,
                            ((CharSequence) contentType).toString());
                }

                final Optional<ByteBuffer> byteBuffer = convertToByteBuffer(bytePayload);
                if (byteBuffer.isPresent()) {
                    messageBuilder.withBytes(byteBuffer.get());
                } else if (!(textPayload instanceof Undefined)) {
                    messageBuilder.withText(((CharSequence) textPayload).toString());
                } else {
                    throw MessageMappingFailedException.newBuilder("")
                            .description("Neither <bytePayload> nor <textPayload> were defined in the outgoing script")
                            .dittoHeaders(adaptable.getHeaders().orElse(DittoHeaders.empty()))
                            .build();
                }

                return messageBuilder.build();
            }));
        } catch (final RhinoException e) {
            throw buildMessageMappingFailedException(e, MessageMapper.findContentType(adaptable).orElse(""),
                    adaptable.getHeaders().orElseGet(DittoHeaders::empty));
        } catch (final Throwable e) {
            throw MessageMappingFailedException.newBuilder(MessageMapper.findContentType(adaptable).orElse(""))
                    .description(e.getMessage())
                    .dittoHeaders(adaptable.getHeaders().orElseGet(DittoHeaders::empty))
                    .cause(e)
                    .build();
        }
    }

    private static Optional<ByteBuffer> convertToByteBuffer(final Object obj) {
        if (obj instanceof NativeArrayBuffer) {
            return Optional.of(ByteBuffer.wrap(((NativeArrayBuffer) obj).getBuffer()));
        } else if (obj instanceof Bindings) {
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
        } else if (obj instanceof List<?>) {
            final List<?> list = (List<?>) obj;
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            list.forEach(e -> baos.write(((Number) e).intValue()));
            return Optional.of(ByteBuffer.wrap(baos.toByteArray()));
        }
        return Optional.empty();
    }

    private static class NullCallable implements Callable {

        @Override
        public Object call(final Context context, final Scriptable scope, final Scriptable holdable,
                final Object[] objects) {
            return objects[1];
        }
    }
}
