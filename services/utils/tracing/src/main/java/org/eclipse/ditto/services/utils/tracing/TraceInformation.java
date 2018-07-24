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
package org.eclipse.ditto.services.utils.tracing;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * This class provides as a wrapper for tracing information as it can hold the uri (name) of a trace and its tags.
 */
public final class TraceInformation {

    private final String traceUri;
    private final Map<String, String> tags;

    TraceInformation(final String traceUri, final Map<String, String> tags) {
        this.traceUri = traceUri;
        this.tags = Collections.unmodifiableMap(tags);
    }

    public String getTraceUri() {
        return traceUri;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final TraceInformation that = (TraceInformation) o;
        return Objects.equals(traceUri, that.traceUri) &&
                Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceUri, tags);
    }

    @Override
    public String toString() {
        return "TraceInformation{" +
                "traceUri='" + traceUri + '\'' +
                ", tags=" + tags +
                '}';
    }

    static final class Builder {

        private final String traceUri;
        private final Map<String, String> tags;

        private Builder(final String traceUri) {
            this.traceUri = traceUri;
            this.tags = new HashMap<>();
        }

        static Builder forTraceUri(final String traceUri) {
            return new Builder(traceUri);
        }

        Builder tag(final String key, final String value) {
            this.tags.put(key, value);
            return this;
        }

        TraceInformation build() {
            return new TraceInformation(this.traceUri, this.tags);
        }
    }
}
