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

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A mutable builder with a fluent API for an immutable {@link FeatureDefinition}.
 */
@NotThreadSafe
public interface FeatureDefinitionBuilder extends Iterable<FeatureDefinition.Identifier> {

    /**
     * Sets the specified Identifier to this builder. This method has no effect if the same identifier was already set.
     *
     * @param identifier the identifier to be set.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code identifier} is {@code null}.
     */
    FeatureDefinitionBuilder setIdentifier(FeatureDefinition.Identifier identifier);

    /**
     * Sets the specified Identifiers to this builder. This method avoids duplicates.
     *
     * @param identifiers the identifiers to be set.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code identifiers} is {@code null}.
     */
    FeatureDefinitionBuilder setAllIdentifiers(Iterable<FeatureDefinition.Identifier> identifiers);

    /**
     * Removes the specified Identifier from this builder.
     *
     * @param identifier the identifier to be removed.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code identifier} is {@code null}.
     */
    FeatureDefinitionBuilder removeIdentifier(FeatureDefinition.Identifier identifier);

    /**
     * Removes the specified Identifiers from this builder.
     *
     * @param identifiers the identifiers to be removed.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code identifiers} is {@code null}.
     */
    FeatureDefinitionBuilder removeAllIdentifiers(Iterable<FeatureDefinition.Identifier> identifiers);

    /**
     * Returns the first Identifier of this builder which is guaranteed to exist.
     *
     * @return the Identifier.
     */
    FeatureDefinition.Identifier getFirstIdentifier();

    /**
     * Returns the count of identifiers of this builder. The size is guaranteed to be at least one.
     *
     * @return the size.
     */
    int getSize();

    /**
     * Returns a sequential {@code Stream} with the identifiers of this builder as its source.
     *
     * @return a sequential stream of the identifiers of this builder.
     */
    Stream<FeatureDefinition.Identifier> stream();

    /**
     * Returns an immutable instance of {@link FeatureDefinition} containing the identifiers which were provided to
     * this builder so far.
     *
     * @return the instance.
     * @throws java.lang.IndexOutOfBoundsException if this builder did not contain any identifiers.
     */
    FeatureDefinition build();

}
