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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;

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

    @Nullable private ContextFactory contextFactory;
    @Nullable private JavaScriptMessageMapperConfiguration configuration;

    private MappingFunction<ExternalMessage, List<Adaptable>> incomingMapping = DefaultIncomingMapping.get();
    private MappingFunction<Adaptable, List<ExternalMessage>> outgoingMapping = DefaultOutgoingMapping.get();

    /**
     * Constructs a new {@code JavaScriptMessageMapper} object.
     * This constructor is required as the the instance is created via reflection.
     */
    JavaScriptMessageMapperRhino() {
        super();
    }

    @Override
    public String getId() {
        return "javascript";
    }

    @Override
    public void configure(final MappingConfig mappingConfig, final MessageMapperConfiguration options) {
        configuration = new ImmutableJavaScriptMessageMapperConfiguration.Builder(options.getProperties()).build();

        final JavaScriptConfig javaScriptConfig = mappingConfig.getJavaScriptConfig();
        final int maxScriptSizeBytes = javaScriptConfig.getMaxScriptSizeBytes();
        final Integer incomingScriptSize = configuration.getIncomingScript().map(String::length).orElse(0);
        final Integer outgoingScriptSize = configuration.getOutgoingScript().map(String::length).orElse(0);

        if (incomingScriptSize > maxScriptSizeBytes || outgoingScriptSize > maxScriptSizeBytes) {
            throw MessageMapperConfigurationFailedException
                    .newBuilder("The script size was bigger than the allowed <" + maxScriptSizeBytes + "> bytes: " +
                            "incoming script size was <" + incomingScriptSize + "> bytes, " +
                            "outgoing script size was <" + outgoingScriptSize + "> bytes")
                    .build();
        }

        contextFactory = new SandboxingContextFactory(javaScriptConfig.getMaxScriptExecutionTime(),
                javaScriptConfig.getMaxScriptStackDepth());

        try {
            // create scope once and load the required libraries in order to get best performance:
            contextFactory.call(cx -> {
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
    public List<Adaptable> map(final ExternalMessage message) {
        return incomingMapping.apply(message);
    }

    @Override
    public List<ExternalMessage> map(final Adaptable adaptable) {
        return outgoingMapping.apply(adaptable);
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

        final String userIncomingScript = getConfiguration()
                .flatMap(JavaScriptMessageMapperConfiguration::getIncomingScript)
                .orElse("");
        if (userIncomingScript.isEmpty()) {
            // shortcut: the user defined an empty incoming mapping script -> assume that the ExternalMessage is in DittoProtocol
            incomingMapping = DefaultIncomingMapping.get();
        } else {
            incomingMapping = new ScriptedIncomingMapping(contextFactory, scope);
            cx.evaluateString(scope, userIncomingScript,
                    JavaScriptMessageMapperConfigurationProperties.INCOMING_SCRIPT, 1, null);
        }

        final String userOutgoingScript = getConfiguration()
                .flatMap(JavaScriptMessageMapperConfiguration::getOutgoingScript)
                .orElse("");
        if (userOutgoingScript.isEmpty()) {
            // shortcut: the user defined an empty outgoing mapping script -> send the Adaptable as DittoProtocol JSON
            outgoingMapping = DefaultOutgoingMapping.get();
        } else {
            outgoingMapping = new ScriptedOutgoingMapping(contextFactory, scope);
            cx.evaluateString(scope, userOutgoingScript,
                    JavaScriptMessageMapperConfigurationProperties.OUTGOING_SCRIPT, 1, null);
        }
    }

    private Optional<JavaScriptMessageMapperConfiguration> getConfiguration() {
        return Optional.ofNullable(configuration);
    }

    private static void loadJavascriptLibrary(final Context cx,
            final Scriptable scope,
            final Reader reader,
            final String libraryName) {

        try {
            cx.evaluateReader(scope, reader, libraryName, 1, null);
        } catch (final IOException e) {
            throw new IllegalStateException("Could not load script <" + libraryName + ">", e);
        }
    }

}
