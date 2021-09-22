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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URL;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link DefinitionIdentifier}.
 */
@Immutable
final class ImmutableFeatureDefinitionIdentifier implements DefinitionIdentifier {

    private final DefinitionIdentifier delegate;

    private ImmutableFeatureDefinitionIdentifier(final DefinitionIdentifier delegate) {
        this.delegate = delegate;
    }

    private ImmutableFeatureDefinitionIdentifier(final CharSequence theNamespace, final CharSequence theName,
            final CharSequence theVersion) {
        this(ImmutableDefinitionIdentifier.getInstance(theNamespace, theName, theVersion));
    }

    /**
     * Returns an instance of {@code ImmutableFeatureDefinitionIdentifier}.
     *
     * @param namespace the namespace of the returned Identifier.
     * @param name the name of the returned Identifier.
     * @param version the version of the returned Identifier.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if any argument is empty.
     */
    public static ImmutableFeatureDefinitionIdentifier getInstance(final CharSequence namespace,
            final CharSequence name, final CharSequence version) {

        return new ImmutableFeatureDefinitionIdentifier(namespace, name, version);
    }

    /**
     * Parses the specified CharSequence and returns an instance of {@code ImmutableFeatureDefinitionIdentifier}.
     *
     * @param featureDefinitionIdentifier CharSequence-representation of an FeatureDefinition Identifier.
     * @return the instance.
     * @throws NullPointerException if {@code featureDefinitionIdentifier} is {@code null}.
     * @throws DefinitionIdentifierInvalidException if {@code featureDefinitionIdentifier} is invalid.
     */
    public static ImmutableFeatureDefinitionIdentifier ofParsed(final CharSequence featureDefinitionIdentifier) {
        checkNotNull(featureDefinitionIdentifier, "CharSequence-representation of the identifier");

        if (featureDefinitionIdentifier instanceof ImmutableFeatureDefinitionIdentifier) {
            return (ImmutableFeatureDefinitionIdentifier) featureDefinitionIdentifier;
        }
        return new ImmutableFeatureDefinitionIdentifier(
                ImmutableDefinitionIdentifier.ofParsed(featureDefinitionIdentifier)
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableFeatureDefinitionIdentifier that = (ImmutableFeatureDefinitionIdentifier) o;
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
