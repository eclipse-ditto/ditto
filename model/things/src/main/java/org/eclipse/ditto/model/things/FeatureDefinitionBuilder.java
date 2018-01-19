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

import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A mutable builder with a fluent API for an immutable {@link FeatureDefinition}.
 */
@NotThreadSafe
public interface FeatureDefinitionBuilder extends Iterable<FeatureDefinition.Identifier> {

    /**
     * Adds the specified Identifier to this builder if it is not already present.
     *
     * @param identifier the Identifier to be added.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code identifier} is {@code null}.
     * @throws FeatureDefinitionIdentifierInvalidException if {@code identifier} is invalid.
     */
    FeatureDefinitionBuilder add(CharSequence identifier);

    /**
     * Adds all of the Identifiers in the specified Iterable to this builder if they're not already present.
     *
     * @param identifiers the Identifiers to be added.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code identifiers} is {@code null}.
     * @throws FeatureDefinitionIdentifierInvalidException if any Identifier of {@code identifiers} is invalid.
     */
    <T extends CharSequence> FeatureDefinitionBuilder addAll(Iterable<T> identifiers);

    /**
     * Removes the specified Identifier from this builder if it is present.
     *
     * @param identifier the Identifier to be removed.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code identifier} is {@code null}.
     * @throws FeatureDefinitionIdentifierInvalidException if {@code identifier} is invalid.
     */
    FeatureDefinitionBuilder remove(CharSequence identifier);

    /**
     * Removes from this builder all of its Identifiers that are contained in the specified Iterable.
     *
     * @param identifiers the Identifiers to be removed.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code identifiers} is {@code null}.
     * @throws FeatureDefinitionIdentifierInvalidException if any Identifier of {@code identifiers} is invalid.
     */
    <T extends CharSequence> FeatureDefinitionBuilder removeAll(Iterable<T> identifiers);

    /**
     * Returns the first Identifier of this builder or {@code null} if an intermediate state of this builder does not
     * contain any Identifiers - however this should be an utterly exception.
     *
     * @return the Identifier or {@code null}.
     */
    @Nullable
    FeatureDefinition.Identifier getFirstIdentifier();

    /**
     * Returns the count of Identifiers of this builder. The size is guaranteed to be at least one.
     *
     * @return the size.
     */
    int getSize();

    /**
     * Returns a sequential {@code Stream} with the Identifiers of this builder as its source.
     *
     * @return a sequential stream of the Identifiers of this builder.
     */
    Stream<FeatureDefinition.Identifier> stream();

    /**
     * Returns an immutable instance of {@link FeatureDefinition} containing the Identifiers which were provided to
     * this builder so far.
     *
     * @return the instance.
     * @throws java.lang.IndexOutOfBoundsException if this builder did not contain any Identifiers.
     */
    FeatureDefinition build();

}
