/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URL;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.ditto.json.JsonValue;

/**
 * An immutable implementation of {@link ThingDefinition}.
 */
final class ImmutableThingDefinition implements ThingDefinition {

    private final DefinitionIdentifier delegate;

    private ImmutableThingDefinition(final DefinitionIdentifier delegate) {
        this.delegate = delegate;
    }

    private ImmutableThingDefinition(final CharSequence theNamespace, final CharSequence theName,
            final CharSequence theVersion) {
        this(ImmutableDefinitionIdentifier.getInstance(theNamespace, theName, theVersion));
    }

    /**
     * Returns an instance of {@code ImmutableThingDefinition}.
     *
     * @param namespace the namespace of the returned Identifier.
     * @param name the name of the returned Identifier.
     * @param version the version of the returned Identifier.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if any argument is empty.
     */
    public static ImmutableThingDefinition getInstance(final CharSequence namespace,
            final CharSequence name, final CharSequence version) {

        return new ImmutableThingDefinition(namespace, name, version);
    }

    /**
     * Parses the specified CharSequence and returns an instance of {@code ImmutableThingDefinition}.
     *
     * @param thingDefinitionIdentifier CharSequence-representation of an ThingDefinition Identifier.
     * @return the instance.
     * @throws NullPointerException if {@code thingDefinitionIdentifier} is {@code null}.
     * @throws DefinitionIdentifierInvalidException if {@code thingDefinitionIdentifier} is invalid.
     */
    public static ImmutableThingDefinition ofParsed(final CharSequence thingDefinitionIdentifier) {
        checkNotNull(thingDefinitionIdentifier, "CharSequence-representation of the identifier");

        if (thingDefinitionIdentifier instanceof ImmutableThingDefinition) {
            return (ImmutableThingDefinition) thingDefinitionIdentifier;
        }
        return new ImmutableThingDefinition(
                ImmutableDefinitionIdentifier.ofParsed(thingDefinitionIdentifier)
        );
    }

    @Override
    public String getNamespace() {
        return delegate.getNamespace();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getVersion() {
        return delegate.getVersion();
    }

    @Override
    public Optional<URL> getUrl() {
        return delegate.getUrl();
    }

    @Override
    public JsonValue toJson() {
        return JsonValue.of(toString());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableThingDefinition that = (ImmutableThingDefinition) o;
        return delegate.equals(that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }

    @Override
    public int length() {
        return delegate.length();
    }

    @Override
    public char charAt(final int index) {
        return delegate.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return delegate.subSequence(start, end);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

}
