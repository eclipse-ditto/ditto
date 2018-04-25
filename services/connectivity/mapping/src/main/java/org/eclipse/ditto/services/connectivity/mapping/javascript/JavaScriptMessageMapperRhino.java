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
package org.eclipse.ditto.services.connectivity.mapping.javascript;

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
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperConfiguration;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.typedarrays.NativeArrayBuffer;

import com.typesafe.config.Config;

/**
 * This mapper executes its mapping methods on the <b>current thread</b>. The caller should be aware of that.
 */
final class JavaScriptMessageMapperRhino implements MessageMapper {

    private static final String WEBJARS_PATH = "/META-INF/resources/webjars";

    private static final String WEBJARS_BYTEBUFFER = WEBJARS_PATH + "/bytebuffer/5.0.1/dist/bytebuffer.js";
    private static final String WEBJARS_LONG = WEBJARS_PATH + "/long/3.2.0/dist/long.min.js";

    private static final String DITTO_SCOPE_SCRIPT = "/javascript/ditto-scope.js";
    private static final String INCOMING_SCRIPT = "/javascript/incoming-mapping.js";
    private static final String OUTGOING_SCRIPT = "/javascript/outgoing-mapping.js";

    private static final String EXTERNAL_MESSAGE_HEADERS = "headers";
    private static final String EXTERNAL_MESSAGE_CONTENT_TYPE = "contentType";
    private static final String EXTERNAL_MESSAGE_TEXT_PAYLOAD = "textPayload";
    private static final String EXTERNAL_MESSAGE_BYTE_PAYLOAD = "bytePayload";

    private static final String INCOMING_FUNCTION_NAME = "mapToDittoProtocolMsgWrapper";
    private static final String OUTGOING_FUNCTION_NAME = "mapFromDittoProtocolMsgWrapper";

    private static final String CONFIG_JAVASCRIPT_MAX_SCRIPT_SIZE_BYTES = "javascript.maxScriptSizeBytes";
    private static final String CONFIG_JAVASCRIPT_MAX_SCRIPT_EXECUTION_TIME = "javascript.maxScriptExecutionTime";
    private static final String CONFIG_JAVASCRIPT_MAX_SCRIPT_STACK_DEPTH = "javascript.maxScriptStackDepth";

    @Nullable
    private ContextFactory contextFactory;
    @Nullable
    private Scriptable scope;

    @Nullable private JavaScriptMessageMapperConfiguration configuration;

    JavaScriptMessageMapperRhino() {
        // no-op
    }

    @Override
    public void configure(final Config mappingConfig, final MessageMapperConfiguration options) {
        this.configuration = new ImmutableJavaScriptMessageMapperConfiguration.Builder(options.getProperties()).build();

        final int maxScriptSizeBytes = mappingConfig.getInt(CONFIG_JAVASCRIPT_MAX_SCRIPT_SIZE_BYTES);
        final Integer incomingScriptSize = configuration.getIncomingScript().map(String::length).orElse(0);
        final Integer outgoingScriptSize = configuration.getOutgoingScript().map(String::length).orElse(0);

        if (incomingScriptSize > maxScriptSizeBytes || outgoingScriptSize > maxScriptSizeBytes) {
            throw MessageMapperConfigurationFailedException
                    .newBuilder("The script size was bigger than the allowed <" + maxScriptSizeBytes + "> bytes: " +
                            "incoming script size was <" + incomingScriptSize + "> bytes, " +
                            "outgoing script size was <" + outgoingScriptSize + "> bytes")
                    .build();
        }

        contextFactory = new SandboxingContextFactory(
                mappingConfig.getDuration(CONFIG_JAVASCRIPT_MAX_SCRIPT_EXECUTION_TIME),
                mappingConfig.getInt(CONFIG_JAVASCRIPT_MAX_SCRIPT_STACK_DEPTH));

        try {
            // create scope once and load the required libraries in order to get best performance:
            scope = (Scriptable) contextFactory.call(cx -> {
                final Scriptable scope = cx.initSafeStandardObjects(); // that one disables "print, exit, quit", etc.
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
    public Optional<Adaptable> map(final ExternalMessage message) {

        try {
            return Optional.ofNullable((Adaptable) contextFactory.call(cx -> {
                final NativeObject headersObj = new NativeObject();
                message.getHeaders().forEach((key, value) -> headersObj.put(key, headersObj, value));

                final NativeArrayBuffer bytePayload;
                if (message.getBytePayload().isPresent()) {
                    final ByteBuffer byteBuffer = message.getBytePayload().get();
                    final byte[] array = byteBuffer.array();
                    bytePayload = new NativeArrayBuffer(array.length);
                    for (int a = 0; a < array.length; a++) {
                        bytePayload.getBuffer()[a] = array[a];
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

                final Function mapToDittoProtocolMsgWrapper = (Function) scope.get(INCOMING_FUNCTION_NAME, scope);
                final Object result = mapToDittoProtocolMsgWrapper.call(cx, scope, scope, new Object[] {externalMessage});

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

    private MessageMappingFailedException buildMessageMappingFailedException(final RhinoException e,
            final String contentType, final DittoHeaders dittoHeaders) {
        final boolean sourceExists = e.lineSource() != null && !e.lineSource().isEmpty();
        final String lineSource = sourceExists ? (", source:\n" + e.lineSource()) : "";
        final boolean stackExists = e.getScriptStackTrace() != null && !e.getScriptStackTrace().isEmpty();
        final String scriptStackTrace = stackExists ? (", stack:\n" + e.getScriptStackTrace()) : "";
        return MessageMappingFailedException.newBuilder(contentType)
                .description(e.getMessage() + " - in line/column #" + e.lineNumber() + "/" + e.columnNumber() +
                        lineSource + scriptStackTrace)
                .dittoHeaders(dittoHeaders)
                .cause(e)
                .build();
    }

    @Override
    public Optional<ExternalMessage> map(final Adaptable adaptable) {
        final JsonifiableAdaptable jsonifiableAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable);

        try {
            return Optional.ofNullable((ExternalMessage) contextFactory.call(cx -> {
                final Object dittoProtocolMessage =
                        NativeJSON.parse(cx, scope, jsonifiableAdaptable.toJsonString(), new NullCallable());

                final Function mapFromDittoProtocolMsgWrapper = (Function) scope.get(OUTGOING_FUNCTION_NAME, scope);
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

                final ExternalMessageBuilder messageBuilder = ConnectivityModelFactory.newExternalMessageBuilder(headers,
                        adaptable.getTopicPath().getPath());

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


    private void initLibraries(final Context cx, final Scriptable scope) {
        if (getConfiguration().map(JavaScriptMessageMapperConfiguration::isLoadLongJS).orElse(false)) {
            loadJavascriptLibrary(cx, scope, new InputStreamReader(getClass().getResourceAsStream(WEBJARS_LONG)),
                    WEBJARS_LONG);
        }
        if (getConfiguration().map(JavaScriptMessageMapperConfiguration::isLoadBytebufferJS).orElse(false)) {
            loadJavascriptLibrary(cx, scope, new InputStreamReader(getClass().getResourceAsStream(WEBJARS_BYTEBUFFER)),
                    WEBJARS_BYTEBUFFER);
        }

        loadJavascriptLibrary(cx, scope, new InputStreamReader(getClass().getResourceAsStream(DITTO_SCOPE_SCRIPT)),
                DITTO_SCOPE_SCRIPT);
        loadJavascriptLibrary(cx, scope, new InputStreamReader(getClass().getResourceAsStream(INCOMING_SCRIPT)),
                INCOMING_SCRIPT);
        loadJavascriptLibrary(cx, scope, new InputStreamReader(getClass().getResourceAsStream(OUTGOING_SCRIPT)),
                OUTGOING_SCRIPT);

        cx.evaluateString(scope,
                    getConfiguration().flatMap(JavaScriptMessageMapperConfiguration::getIncomingScript)
                            .orElse(""),
                JavaScriptMessageMapperConfigurationProperties.INCOMING_SCRIPT, 1, null);
        cx.evaluateString(scope,
                    getConfiguration().flatMap(JavaScriptMessageMapperConfiguration::getOutgoingScript)
                            .orElse(""),
                JavaScriptMessageMapperConfigurationProperties.OUTGOING_SCRIPT, 1, null);
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
