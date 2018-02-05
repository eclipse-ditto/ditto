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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * An immutable implementation of {@link org.eclipse.ditto.model.things.FeatureDefinition.Identifier}.
 */
@Immutable
final class ImmutableFeatureDefinitionIdentifier implements FeatureDefinition.Identifier {

    private static final Pattern IDENTIFIER_PATTERN = FeatureIdentifierPatternBuilder.getInstance()
            .addCapturingGroup(CapturingGroup.NAMESPACE)
            .addCapturingGroup(CapturingGroup.NAME)
            .addCapturingGroup(CapturingGroup.VERSION)
            .build();

    private static final String COLON = ":";

    private final String namespace;
    private final String name;
    private final String version;
    private final String stringRepresentation;

    private ImmutableFeatureDefinitionIdentifier(final CharSequence theNamespace, final CharSequence theName,
            final CharSequence theVersion) {

        namespace = argumentNotEmpty(theNamespace, "namespace").toString();
        name = argumentNotEmpty(theName, "name").toString();
        version = argumentNotEmpty(theVersion, "version").toString();
        stringRepresentation = namespace + COLON + name + COLON + version;
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
     * @param featureIdentifierAsCharSequence CharSequence-representation of an FeatureDefinition Identifier.
     * @return the instance.
     * @throws NullPointerException if {@code featureIdentifierAsCharSequence} is {@code null}.
     * @throws FeatureDefinitionIdentifierInvalidException if {@code featureIdentifierAsCharSequence} is invalid.
     */
    public static ImmutableFeatureDefinitionIdentifier ofParsed(final CharSequence featureIdentifierAsCharSequence) {
        checkNotNull(featureIdentifierAsCharSequence, "CharSequence-representation of the identifier");
        final Matcher matcher = IDENTIFIER_PATTERN.matcher(featureIdentifierAsCharSequence);
        if (!matcher.matches()) {
            throw new FeatureDefinitionIdentifierInvalidException(featureIdentifierAsCharSequence);
        }

        final String parsedNamespace = matcher.group(CapturingGroup.NAMESPACE);
        final String parsedName = matcher.group(CapturingGroup.NAME);
        final String parsedVersion = matcher.group(CapturingGroup.VERSION);

        return getInstance(parsedNamespace, parsedName, parsedVersion);
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return version;
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
        return Objects.equals(namespace, that.namespace) &&
                Objects.equals(name, that.name) &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, name, version);
    }

    @Override
    public int length() {
        return stringRepresentation.length();
    }

    @Override
    public char charAt(final int index) {
        return stringRepresentation.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return stringRepresentation.subSequence(start, end);
    }

    @Override
    public String toString() {
        return stringRepresentation;
    }

    /**
     * This class provides constants for names of regex pattern capturing group.
     */
    @Immutable
    private static final class CapturingGroup {

        /**
         * Name of the capturing group for the namespace.
         */
        public static final String NAMESPACE = "NAMESPACE";

        /**
         * Name of the capturing group for the name.
         */
        public static final String NAME = "NAME";

        /**
         * Name of the capturing group for the version.
         */
        public static final String VERSION = "VERSION";

        private CapturingGroup() {
            throw new AssertionError();
        }

    }

    /**
     * A mutable builder with a fluent API for a {@link Pattern} for parsing string representations of a
     * FeatureDefinitionIdentifier.
     */
    @NotThreadSafe
    private static final class FeatureIdentifierPatternBuilder {

        private static final Pattern ELEMENT_PATTERN = Pattern.compile("[_a-zA-Z0-9\\-.]+");

        private final StringBuilder stringBuilder;

        private FeatureIdentifierPatternBuilder() {
            stringBuilder = new StringBuilder();
        }

        /**
         * Returns an instance of {@code FeatureIdentifierPatternBuilder}.
         *
         * @return the instance.
         */
        public static FeatureIdentifierPatternBuilder getInstance() {
            return new FeatureIdentifierPatternBuilder();
        }

        /**
         * Adds a capturing group with the specified name.
         *
         * @param elementCapturingGroupName the name of the capturing group to be added.
         * @return this builder instance to allow method chaining.
         */
        public FeatureIdentifierPatternBuilder addCapturingGroup(final CharSequence elementCapturingGroupName) {
            if (0 < stringBuilder.length()) {
                stringBuilder.append(COLON);
            }
            stringBuilder.append("(?<").append(elementCapturingGroupName).append(">");
            stringBuilder.append(ELEMENT_PATTERN).append(")");
            return this;
        }

        /**
         * Returns a Pattern consisting of the named capturing groups which were added to this builder. The capturing
         * groups are delimited by {@link #COLON}.
         *
         * @return the new Pattern.
         */
        public Pattern build() {
            return Pattern.compile(stringBuilder.toString());
        }

    }

}
