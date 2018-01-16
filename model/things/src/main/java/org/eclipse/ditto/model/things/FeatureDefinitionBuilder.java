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
     * @param identifier the identifier to be added.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code identifier} is {@code null}.
     */
    FeatureDefinitionBuilder add(FeatureDefinition.Identifier identifier);

    /**
     * Adds all of the identifiers in the specified Iterable to this builder if they're not already present.
     *
     * @param identifiers the identifiers to be added.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code identifiers} is {@code null}.
     */
    FeatureDefinitionBuilder addAll(Iterable<FeatureDefinition.Identifier> identifiers);

    /**
     * Removes the specified Identifier from this builder if it is present.
     *
     * @param identifier the identifier to be removed.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code identifier} is {@code null}.
     */
    FeatureDefinitionBuilder remove(FeatureDefinition.Identifier identifier);

    /**
     * Removes from this builder all of its identifiers that are contained in the specified Iterable.
     *
     * @param identifiers the identifiers to be removed.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code identifiers} is {@code null}.
     */
    FeatureDefinitionBuilder removeAll(Iterable<FeatureDefinition.Identifier> identifiers);

    /**
     * Returns the first Identifier of this builder or {@code null} if an intermediate state of this builder does not
     * contain any identifiers - however this should be an utterly exception.
     *
     * @return the Identifier or {@code null}.
     */
    @Nullable
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
