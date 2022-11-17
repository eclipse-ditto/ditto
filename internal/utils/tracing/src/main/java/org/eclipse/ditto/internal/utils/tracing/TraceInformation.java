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
package org.eclipse.ditto.internal.utils.tracing;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;

/**
 * This class provides tracing information as it can hold the URI of a trace and its tags.
 */
@Immutable
public final class TraceInformation {

    private final URI traceUri;
    private final TagSet tagSet;

    private TraceInformation(final URI traceUri, final TagSet tagSet) {
        this.traceUri = traceUri;
        this.tagSet = tagSet;
    }

    static TraceInformation newInstance(final URI traceUri, final TagSet tagSet) {
        return new TraceInformation(checkNotNull(traceUri, "traceUri"), checkNotNull(tagSet, "tagSet"));
    }

    public URI getTraceUri() {
        return traceUri;
    }

    /**
     * Returns an unmodifiable unsorted Set containing all tags of this TraceInformation.
     *
     * @return the tags of this TraceInformation.
     */
    public TagSet getTagSet() {
        return tagSet;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (TraceInformation) o;
        return Objects.equals(traceUri, that.traceUri) && Objects.equals(tagSet, that.tagSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceUri, tagSet);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "traceUri=" + traceUri +
                ", tagSet=" + tagSet +
                "]";
    }

}
