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
import org.eclipse.ditto.model.amqpbridge.MessageMapperConfigurationFailedException;
import org.eclipse.ditto.model.amqpbridge.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.model.amqpbridge.MessageMappingFailedException;
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
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
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

    private static final String INCOMING_SCRIPT = "/javascript/incoming-mapping.js";
    private static final String OUTGOING_SCRIPT = "/javascript/outgoing-mapping.js";

    private static final String EXTERNAL_MESSAGE_HEADERS = "headers";
    private static final String EXTERNAL_MESSAGE_CONTENT_TYPE = "contentType";
    private static final String EXTERNAL_MESSAGE_TEXT_PAYLOAD = "textPayload";
    private static final String EXTERNAL_MESSAGE_BYTE_PAYLOAD = "bytePayload";

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
        contextFactory = new SandboxingContextFactory(configuration.getMaxScriptExecutionTime(),
                configuration.getMaxScriptStackDepth());

        try {
            // create scope once and load the required libraries in order to get best performance:
            scope = (Scriptable) contextFactory.call(cx -> {
                final Scriptable scope = cx.initSafeStandardObjects();
                initLibraries(cx, scope);
                return scope;
            });
        } catch (final RhinoException e) {
            final boolean sourceExists = e.lineSource() != null && !e.lineSource().isEmpty();
            final String lineSource = sourceExists ? (", source:\n" + e.lineSource()) : "";
            final boolean stackExists = e.getScriptStackTrace() != null && !e.getScriptStackTrace().isEmpty();
            final String scriptStackTrace = stackExists ? (", stack:\n" + e.getScriptStackTrace()) : "";
            throw MessageMapperConfigurationFailedException.newBuilder(e.getMessage() +
                    " - in line/column #" + e.lineNumber() + "/" + e.columnNumber() + lineSource + scriptStackTrace)
                    .cause(e)
                    .build();
        }
    }

    @Override
    public Adaptable map(final ExternalMessage message) {

        try {
            return (Adaptable) contextFactory.call(cx -> {
                final NativeObject headersObj = new NativeObject();
                message.getHeaders().forEach((key, value) -> headersObj.put(key, headersObj, value));

                final NativeArray bytePayload;
                if (message.getBytePayload().isPresent()) {
                    final ByteBuffer byteBuffer = message.getBytePayload().get();
                    final byte[] array = byteBuffer.array();
                    bytePayload = new NativeArray(array.length);
                    for (int a = 0; a < array.length; a++) {
                        ScriptableObject.putProperty(bytePayload, a, array[a]);
                    }
                } else {
                    bytePayload = null;
                }

                final String contentType = message.getHeaders().get(ExternalMessage.CONTENT_TYPE_HEADER);
                final String textPayload = message.getTextPayload().orElse(null);

                final NativeObject externalMessage = new NativeObject();
                externalMessage.put(EXTERNAL_MESSAGE_HEADERS, externalMessage, headersObj);
                externalMessage.put(EXTERNAL_MESSAGE_TEXT_PAYLOAD, externalMessage, textPayload);
                externalMessage.put(EXTERNAL_MESSAGE_BYTE_PAYLOAD, externalMessage, bytePayload);
                externalMessage.put(EXTERNAL_MESSAGE_CONTENT_TYPE, externalMessage, contentType);

                final Function mapToDittoProtocolMsgWrapper = (Function) scope.get("mapToDittoProtocolMsgWrapper", scope);
                final Object result = mapToDittoProtocolMsgWrapper.call(cx, scope, scope, new Object[] {externalMessage});

                final String dittoProtocolJsonStr = (String) NativeJSON.stringify(cx, scope, result, null, null);

                return DittoJsonException.wrapJsonRuntimeException(() -> {
                    final JsonObject jsonObject = JsonFactory.readFrom(dittoProtocolJsonStr).asObject();
                    return ProtocolFactory.jsonifiableAdaptableFromJson(jsonObject);
                });
            });
        } catch (final RhinoException e) {
            throw buildMessageMappingFailedException(e, message.findContentType().orElse(""));
        } catch (final Throwable e) {
            throw MessageMappingFailedException.newBuilder(message.findContentType().orElse(null))
                    .description(e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    private MessageMappingFailedException buildMessageMappingFailedException(final RhinoException e, final String contentType) {
        final boolean sourceExists = e.lineSource() != null && !e.lineSource().isEmpty();
        final String lineSource = sourceExists ? (", source:\n" + e.lineSource()) : "";
        final boolean stackExists = e.getScriptStackTrace() != null && !e.getScriptStackTrace().isEmpty();
        final String scriptStackTrace = stackExists ? (", stack:\n" + e.getScriptStackTrace()) : "";
        return MessageMappingFailedException.newBuilder(contentType)
                .description(e.getMessage() + " - in line/column #" + e.lineNumber() + "/" + e.columnNumber() +
                        lineSource + scriptStackTrace)
                .cause(e)
                .build();
    }

    @Override
    public ExternalMessage map(final Adaptable adaptable) {
        final JsonifiableAdaptable jsonifiableAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);

        try {
            return (ExternalMessage) contextFactory.call(cx -> {
                final Object dittoProtocolMessage =
                        NativeJSON.parse(cx, scope, jsonifiableAdaptable.toJsonString(), new NullCallable());

                final Function mapFromDittoProtocolMsgWrapper = (Function) scope.get("mapFromDittoProtocolMsgWrapper", scope);
                final NativeObject result =
                        (NativeObject) mapFromDittoProtocolMsgWrapper.call(cx, scope, scope, new Object[] {dittoProtocolMessage});

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

                final ExternalMessageBuilder messageBuilder = AmqpBridgeModelFactory.newExternalMessageBuilder(headers,
                        MessageMappers.determineMessageType(adaptable));

                if (!(contentType instanceof Undefined)) {
                    messageBuilder.withAdditionalHeaders(ExternalMessage.CONTENT_TYPE_HEADER,
                            ((CharSequence) contentType).toString());
                }

                final Optional<ByteBuffer> byteBuffer = convertToByteBuffer(bytePayload);
                if (byteBuffer.isPresent()) {
                    messageBuilder.withBytes(byteBuffer.get());
                } else if (!(textPayload instanceof Undefined)) {
                    messageBuilder.withText(((CharSequence) textPayload).toString());
                }

                return messageBuilder.build();
            });
        } catch (final RhinoException e) {
            throw buildMessageMappingFailedException(e, "");
        } catch (final Throwable e) {
            throw MessageMappingFailedException.newBuilder("")
                    .description(e.getMessage())
                    .cause(e)
                    .build();
        }
    }


    private void initLibraries(final Context cx, final Scriptable scope) {
        if (getConfiguration().map(JavaScriptMessageMapperConfiguration::isLoadBytebufferJS).orElse(false)) {
            loadJavascriptLibrary(cx, scope, new InputStreamReader(getClass().getResourceAsStream(WEBJARS_BYTEBUFFER)),
                    WEBJARS_BYTEBUFFER);
        }
        if (getConfiguration().map(JavaScriptMessageMapperConfiguration::isLoadLongJS).orElse(false)) {
            loadJavascriptLibrary(cx, scope, new InputStreamReader(getClass().getResourceAsStream(WEBJARS_LONG)),
                    WEBJARS_LONG);
        }
        if (getConfiguration().map(JavaScriptMessageMapperConfiguration::isLoadMustacheJS).orElse(false)) {
            loadJavascriptLibrary(cx, scope, new InputStreamReader(getClass().getResourceAsStream(WEBJARS_MUSTACHE)),
                    WEBJARS_MUSTACHE);
        }

        loadJavascriptLibrary(cx, scope, new InputStreamReader(getClass().getResourceAsStream(INCOMING_SCRIPT)),
                INCOMING_SCRIPT);
        loadJavascriptLibrary(cx, scope, new InputStreamReader(getClass().getResourceAsStream(OUTGOING_SCRIPT)),
                OUTGOING_SCRIPT);

        cx.evaluateString(scope,
                    getConfiguration().flatMap(JavaScriptMessageMapperConfiguration::getIncomingMappingScript)
                            .orElse(""),
                JavaScriptMessageMapperConfigurationProperties.INCOMING_MAPPING_SCRIPT, 1, null);
        cx.evaluateString(scope,
                    getConfiguration().flatMap(JavaScriptMessageMapperConfiguration::getOutgoingMappingScript)
                            .orElse(""),
                JavaScriptMessageMapperConfigurationProperties.OUTGOING_MAPPING_SCRIPT, 1, null);
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
