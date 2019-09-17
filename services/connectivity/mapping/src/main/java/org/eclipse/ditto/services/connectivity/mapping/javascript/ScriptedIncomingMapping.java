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
package org.eclipse.ditto.services.connectivity.mapping.javascript;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.typedarrays.NativeArrayBuffer;

/**
 * Mapping function for incoming messages based on JavaScript.
 */
public class ScriptedIncomingMapping implements MappingFunction<ExternalMessage, Optional<Adaptable>> {

    private static final String EXTERNAL_MESSAGE_HEADERS = "headers";
    private static final String EXTERNAL_MESSAGE_CONTENT_TYPE = "contentType";
    private static final String EXTERNAL_MESSAGE_TEXT_PAYLOAD = "textPayload";
    private static final String EXTERNAL_MESSAGE_BYTE_PAYLOAD = "bytePayload";

    private static final String INCOMING_FUNCTION_NAME = "mapToDittoProtocolMsgWrapper";

    @Nullable
    private ContextFactory contextFactory;
    @Nullable
    private Scriptable scope;

    ScriptedIncomingMapping(@Nullable final ContextFactory contextFactory, @Nullable final Scriptable scope) {
        this.contextFactory = contextFactory;
        this.scope = scope;
    }

    @Override
    public Optional<Adaptable> apply(final ExternalMessage message) {
        try {
            return Optional.ofNullable((Adaptable) contextFactory.call(cx -> {
                final NativeObject externalMessage = mapExternalMessageToNativeObject(message);

                final org.mozilla.javascript.Function
                        mapToDittoProtocolMsgWrapper =
                        (org.mozilla.javascript.Function) scope.get(INCOMING_FUNCTION_NAME, scope);
                final Object result =
                        mapToDittoProtocolMsgWrapper.call(cx, scope, scope, new Object[]{externalMessage});

                if (result == null) {
                    // return null if result is null causing the wrapping Optional to be empty
                    return null;
                }

                final String dittoProtocolJsonStr = (String) NativeJSON.stringify(cx, scope, result, null, null);

                return DittoJsonException.wrapJsonRuntimeException(() -> {
                    final JsonObject jsonObject = JsonFactory.readFrom(dittoProtocolJsonStr).asObject();
                    return ProtocolFactory.jsonifiableAdaptableFromJson(jsonObject);
                });
            }));
        } catch (final RhinoException e) {
            throw buildMessageMappingFailedException(e, message.findContentType().orElse(""),
                    DittoHeaders.of(message.getHeaders()));
        } catch (final Throwable e) {
            throw MessageMappingFailedException.newBuilder(message.findContentType().orElse(null))
                    .description(e.getMessage())
                    .dittoHeaders(DittoHeaders.of(message.getHeaders()))
                    .cause(e)
                    .build();
        }
    }

    static NativeObject mapExternalMessageToNativeObject(final ExternalMessage message) {
        final NativeObject headersObj = new NativeObject();
        message.getHeaders().forEach((key, value) -> headersObj.put(key, headersObj, value));

        final NativeArrayBuffer bytePayload =
                message.getBytePayload()
                        .map(bb -> {
                            final NativeArrayBuffer nativeArrayBuffer = new NativeArrayBuffer(bb.remaining());
                            bb.get(nativeArrayBuffer.getBuffer());
                            return nativeArrayBuffer;
                        })
                        .orElse(null);

        final String contentType = message.getHeaders().get(ExternalMessage.CONTENT_TYPE_HEADER);
        final String textPayload = message.getTextPayload().orElse(null);

        final NativeObject externalMessage = new NativeObject();
        externalMessage.put(EXTERNAL_MESSAGE_HEADERS, externalMessage, headersObj);
        externalMessage.put(EXTERNAL_MESSAGE_TEXT_PAYLOAD, externalMessage, textPayload);
        externalMessage.put(EXTERNAL_MESSAGE_BYTE_PAYLOAD, externalMessage, bytePayload);
        externalMessage.put(EXTERNAL_MESSAGE_CONTENT_TYPE, externalMessage, contentType);
        return externalMessage;
    }
}
