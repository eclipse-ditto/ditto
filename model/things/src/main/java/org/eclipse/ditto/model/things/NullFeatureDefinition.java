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
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;

/**
 * An immutable implementation of {@link FeatureDefinition} which represents {@code null}.
 */
@Immutable
final class NullFeatureDefinition implements FeatureDefinition {

    private NullFeatureDefinition() {
        super();
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
        return JsonFactory.nullArray();
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

    }

}
