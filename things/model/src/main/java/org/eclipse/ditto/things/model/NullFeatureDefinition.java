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
package org.eclipse.ditto.things.model;

import java.net.URL;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;

/**
 * An immutable implementation of {@link FeatureDefinition} which represents {@code null}.
 */
@Immutable
final class NullFeatureDefinition implements FeatureDefinition {

    private final JsonArray wrapped;

    private NullFeatureDefinition() {
        wrapped = JsonFactory.nullArray();
    }

    /**
     * Returns an instance of {@code NullFeatureDefinition}.
     *
     * @return the instance.
     */
    public static FeatureDefinition getInstance() {
        return new NullFeatureDefinition();
    }

    @Override
    public DefinitionIdentifier getFirstIdentifier() {
        return new NullIdentifier();
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public Stream<DefinitionIdentifier> stream() {
        return Stream.empty();
    }

    @Override
    public Iterator<DefinitionIdentifier> iterator() {
        final List<DefinitionIdentifier> emptyList = Collections.emptyList();
        return emptyList.iterator();
    }

    @Override
    public JsonArray toJson() {
        return wrapped;
    }

    @Override
    public int hashCode() {
        return Objects.hash(wrapped);
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NullFeatureDefinition other = (NullFeatureDefinition) o;
        return Objects.equals(wrapped, other.wrapped);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [wrapped=" + wrapped + "]";
    }

    private static final class NullIdentifier implements DefinitionIdentifier {

        private NullIdentifier() {
            super();
        }

        @Override
        public String getNamespace() {
            return "";
        }

        @Override
        public String getName() {
            return "";
        }

        @Override
        public String getVersion() {
            return "";
        }

        @Override
        public Optional<URL> getUrl() {
            return Optional.empty();
        }

        @Override
        public int length() {
            return 0;
        }

        @Override
        public char charAt(final int index) {
            return 0;
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            return "";
        }

        @Override
        public String toString() {
            return "";
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getClass().getName());
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            return obj != null && getClass() == obj.getClass();
        }

    }

}
