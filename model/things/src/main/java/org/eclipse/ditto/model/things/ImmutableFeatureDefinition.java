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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;

/**
 * An immutable implementation of {@link FeatureDefinition}.
 */
@Immutable
final class ImmutableFeatureDefinition implements FeatureDefinition {

    private final List<Identifier> identifierList;

    private ImmutableFeatureDefinition(final Collection<Identifier> identifiers) {
        identifierList = Collections.unmodifiableList(new ArrayList<>(identifiers));
    }

    /**
     * Parses the specified JsonArray and returns an instance of {@code ImmutableFeatureDefinition}.
     *
     * @param featureDefinitionEntriesAsJsonArray JSON array containing the Identifiers of the FeatureDefinition to
     * be returned. Non-string values are ignored.
     * @return the instance.
     * @throws NullPointerException if {@code featureDefinitionEntriesAsJsonArray} is {@code null}.
     * @throws FeatureDefinitionEmptyException if {@code featureDefinitionEntriesAsJsonArray} is empty.
     * @throws FeatureDefinitionIdentifierInvalidException if any Identifier string of the array is invalid.
     */
    public static ImmutableFeatureDefinition fromJson(final JsonArray featureDefinitionEntriesAsJsonArray) {
        checkNotNull(featureDefinitionEntriesAsJsonArray, "JSON array containing the FeatureDefinition entries");
        if (featureDefinitionEntriesAsJsonArray.isEmpty()) {
            throw new FeatureDefinitionEmptyException();
        }

        final List<Identifier> identifiersFromJson = featureDefinitionEntriesAsJsonArray.stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(ImmutableFeatureDefinitionIdentifier::ofParsed)
                .collect(Collectors.toList());

        return new ImmutableFeatureDefinition(identifiersFromJson);
    }

    /**
     * Returns a mutable builder with a fluent API for an {@code ImmutableFeatureDefinition}.
     *
     * @param firstIdentifier the first Identifier of the returned builder.
     * @return the builder.
     * @throws NullPointerException if {@code firstIdentifier} is {@code null}.
     */
    public static Builder getBuilder(final Identifier firstIdentifier) {
        return Builder.getInstance().add(checkNotNull(firstIdentifier, "first identifier"));
    }

    @Override
    public Identifier getFirstIdentifier() {
        return identifierList.get(0);
    }

    @Override
    public int getSize() {
        return identifierList.size();
    }

    @Override
    public Stream<Identifier> stream() {
        return identifierList.stream();
    }

    @Override
    public Iterator<Identifier> iterator() {
        return identifierList.iterator();
    }

    @Override
    public JsonArray toJson() {
        return identifierList.stream()
                .map(Identifier::toString)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableFeatureDefinition that = (ImmutableFeatureDefinition) o;
        return Objects.equals(identifierList, that.identifierList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifierList);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "identifierList=" + identifierList +
                "]";
    }

    public static final class Builder implements FeatureDefinitionBuilder {

        private final Set<Identifier> identifiers;

        /**
         * Constructs a new {@code Builder} object.
         */
        private Builder() {
            identifiers = new LinkedHashSet<>();
        }

        /**
         * Returns an empty instance of {@code Builder}.
         *
         * @return the instance.
         */
        static Builder getInstance() {
            return new Builder();
        }

        @Override
        public Builder add(final CharSequence identifier) {
            identifiers.add(castOrParse(checkNotNull(identifier, "Identifier to be added")));
            return this;
        }

        private static Identifier castOrParse(final CharSequence identifierAsCharSequence) {
            return ThingsModelFactory.newFeatureDefinitionIdentifier(identifierAsCharSequence);
        }

        @Override
        public <T extends CharSequence> Builder addAll(final Iterable<T> identifiers) {
            checkNotNull(identifiers, "Identifiers to be added").forEach(this::add);
            return this;
        }

        @Override
        public Builder remove(final CharSequence identifier) {
            identifiers.remove(castOrParse(checkNotNull(identifier, "Identifier to be removed")));
            return this;
        }

        @Override
        public <T extends CharSequence> Builder removeAll(final Iterable<T> identifiers) {
            checkNotNull(identifiers, "Identifiers to be removed").forEach(this::remove);
            return this;
        }

        @Override
        public Identifier getFirstIdentifier() {
            return iterator().next();
        }

        @Override
        public int getSize() {
            return identifiers.size();
        }

        @Override
        public Stream<Identifier> stream() {
            return identifiers.stream();
        }

        @Override
        public Iterator<Identifier> iterator() {
            return identifiers.iterator();
        }

        @Override
        public ImmutableFeatureDefinition build() {
            if (identifiers.isEmpty()) {
                throw new IndexOutOfBoundsException("This builder does not contain at least one Identifier!");
            }
            return new ImmutableFeatureDefinition(identifiers);
        }

    }

}
