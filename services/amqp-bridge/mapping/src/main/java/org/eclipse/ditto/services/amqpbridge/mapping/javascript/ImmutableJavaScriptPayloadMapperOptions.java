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

import java.util.Objects;

/**
 * TODO doc
 */
final class ImmutableJavaScriptPayloadMapperOptions implements JavaScriptPayloadMapperOptions {

    private final boolean loadBytebufferJS;
    private final boolean loadLongJS;
    private final boolean loadMustacheJS;

    ImmutableJavaScriptPayloadMapperOptions(final boolean loadBytebufferJS, final boolean loadLongJS,
            final boolean loadMustacheJS) {

        this.loadBytebufferJS = loadBytebufferJS;
        this.loadLongJS = loadLongJS;
        this.loadMustacheJS = loadMustacheJS;
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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ImmutableJavaScriptPayloadMapperOptions)) return false;
        final ImmutableJavaScriptPayloadMapperOptions that = (ImmutableJavaScriptPayloadMapperOptions) o;
        return loadBytebufferJS == that.loadBytebufferJS &&
                loadLongJS == that.loadLongJS &&
                loadMustacheJS == that.loadMustacheJS;
    }

    @Override
    public int hashCode() {
        return Objects.hash(loadBytebufferJS, loadLongJS, loadMustacheJS);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "loadBytebufferJS=" + loadBytebufferJS +
                ", loadLongJS=" + loadLongJS +
                ", loadMustacheJS=" + loadMustacheJS +
                "]";
    }

    /**
     *
     */
    static final class Builder implements JavaScriptPayloadMapperOptions.Builder {

        private boolean loadBytebufferJS = false;
        private boolean loadLongJS = false;
        private boolean loadMustacheJS = false;

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
            return new ImmutableJavaScriptPayloadMapperOptions(loadBytebufferJS, loadLongJS, loadMustacheJS);
        }
    }
}
