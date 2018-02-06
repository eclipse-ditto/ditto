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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * TODO doc
 */
final class ImmutableJavaScriptPayloadMapperOptions implements JavaScriptPayloadMapperOptions {

    @Nullable private final String incomingMappingScript;
    @Nullable private final String outgoingMappingScript;
    private final boolean loadBytebufferJS;
    private final boolean loadLongJS;
    private final boolean loadMustacheJS;

    ImmutableJavaScriptPayloadMapperOptions(@Nullable final String incomingMappingScript,
            @Nullable final String outgoingMappingScript, final boolean loadBytebufferJS, final boolean loadLongJS,
            final boolean loadMustacheJS) {
        this.incomingMappingScript = incomingMappingScript;
        this.outgoingMappingScript = outgoingMappingScript;
        this.loadBytebufferJS = loadBytebufferJS;
        this.loadLongJS = loadLongJS;
        this.loadMustacheJS = loadMustacheJS;
    }

    @Override
    public Optional<String> getIncomingMappingScript() {
        return Optional.ofNullable(incomingMappingScript);
    }

    @Override
    public Optional<String> getOutgoingMappingScript() {
        return Optional.ofNullable(outgoingMappingScript);
    }

    @Override
    public boolean isLoadBytebufferJS() {
        return loadBytebufferJS;
    }

    @Override
    public boolean isLoadLongJS() {
        return loadLongJS;
    }

    @Override
    public boolean isLoadMustacheJS() {
        return loadMustacheJS;
    }

    @Override
    public Map<String, String> getAsMap() {
        final Map<String, String> optionsMap = new HashMap<>();
        optionsMap.put("incomingMappingScript", incomingMappingScript);
        optionsMap.put("outgoingMappingScript", outgoingMappingScript);
        optionsMap.put("loadBytebufferJS", Boolean.valueOf(loadBytebufferJS).toString());
        optionsMap.put("loadLongJS", Boolean.valueOf(loadLongJS).toString());
        optionsMap.put("loadMustacheJS", Boolean.valueOf(loadMustacheJS).toString());
        return Collections.unmodifiableMap(optionsMap);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableJavaScriptPayloadMapperOptions)) {
            return false;
        }
        final ImmutableJavaScriptPayloadMapperOptions that = (ImmutableJavaScriptPayloadMapperOptions) o;
        return loadBytebufferJS == that.loadBytebufferJS &&
                loadLongJS == that.loadLongJS &&
                loadMustacheJS == that.loadMustacheJS &&
                Objects.equals(incomingMappingScript, that.incomingMappingScript) &&
                Objects.equals(outgoingMappingScript, that.outgoingMappingScript);
    }

    @Override
    public int hashCode() {
        return Objects.hash(incomingMappingScript, outgoingMappingScript, loadBytebufferJS, loadLongJS, loadMustacheJS);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "incomingMappingScript=" + incomingMappingScript +
                ", outgoingMappingScript=" + outgoingMappingScript +
                ", loadBytebufferJS=" + loadBytebufferJS +
                ", loadLongJS=" + loadLongJS +
                ", loadMustacheJS=" + loadMustacheJS +
                "]";
    }

    /**
     *
     */
    static final class Builder implements JavaScriptPayloadMapperOptions.Builder {

        @Nullable private String incomingMappingScript;
        @Nullable private String outgoingMappingScript;
        private boolean loadBytebufferJS;
        private boolean loadLongJS;
        private boolean loadMustacheJS;

        Builder(final Map<String, String> options) {
            incomingMappingScript = Optional.ofNullable(options.get("incomingMappingScript"))
                    .orElse(null);
            outgoingMappingScript = Optional.ofNullable(options.get("outgoingMappingScript"))
                    .orElse(null);
            loadBytebufferJS = Optional.ofNullable(options.get("loadBytebufferJS"))
                    .map(Boolean::parseBoolean)
                    .orElse(false);
            loadLongJS = Optional.ofNullable(options.get("loadLongJS"))
                    .map(Boolean::parseBoolean)
                    .orElse(false);
            loadMustacheJS = Optional.ofNullable(options.get("loadMustacheJS"))
                    .map(Boolean::parseBoolean)
                    .orElse(false);
        }

        @Override
        public JavaScriptPayloadMapperOptions.Builder incomingMappingScript(@Nullable final String mappingScript) {
            incomingMappingScript = mappingScript;
            return this;
        }

        @Override
        public JavaScriptPayloadMapperOptions.Builder outgoingMappingScript(@Nullable final String mappingScript) {
            outgoingMappingScript = mappingScript;
            return this;
        }

        @Override
        public JavaScriptPayloadMapperOptions.Builder loadBytebufferJS(final boolean load) {
            loadBytebufferJS = load;
            return this;
        }

        @Override
        public JavaScriptPayloadMapperOptions.Builder loadLongJS(final boolean load) {
            loadLongJS = load;
            return this;
        }

        @Override
        public JavaScriptPayloadMapperOptions.Builder loadMustacheJS(final boolean load) {
            loadMustacheJS = load;
            return this;
        }

        @Override
        public JavaScriptPayloadMapperOptions build() {
            return new ImmutableJavaScriptPayloadMapperOptions(incomingMappingScript, outgoingMappingScript,
                    loadBytebufferJS, loadLongJS, loadMustacheJS);
        }
    }
}
