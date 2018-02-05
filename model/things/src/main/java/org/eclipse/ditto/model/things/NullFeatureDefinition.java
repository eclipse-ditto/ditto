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
package org.eclipse.ditto.model.things;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
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
    public Identifier getFirstIdentifier() {
        return new NullIdentifier();
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public Stream<Identifier> stream() {
        return Stream.empty();
    }

    @Override
    public Iterator<Identifier> iterator() {
        final List<Identifier> emptyList = Collections.emptyList();
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

    private static final class NullIdentifier implements FeatureDefinition.Identifier {

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
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            return true;
        }

    }

}
