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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * An immutable implementation of {@link DefinitionIdentifier}.
 */
@Immutable
final class ImmutableDefinitionIdentifier implements DefinitionIdentifier {

    private static final Pattern IDENTIFIER_PATTERN = DefinitionIdentifierPatternBuilder.getInstance()
            .addCapturingGroup(CapturingGroup.NAMESPACE)
            .addCapturingGroup(CapturingGroup.NAME)
            .addCapturingGroup(CapturingGroup.VERSION)
            .build();

    private static final String COLON = ":";

    private final String namespace;
    private final String name;
    private final String version;
    @Nullable private final URL url;
    private final String stringRepresentation;

    private ImmutableDefinitionIdentifier(final CharSequence theNamespace, final CharSequence theName,
            final CharSequence theVersion, @Nullable final URL theUrl) {

        if (null != theUrl) {
            namespace = "";
            name = "";
            version = "";
            url = theUrl;
            stringRepresentation = url.toString();
        } else {
            namespace = argumentNotEmpty(theNamespace, "namespace").toString();
            name = argumentNotEmpty(theName, "name").toString();
            version = argumentNotEmpty(theVersion, "version").toString();
            url = null;
            stringRepresentation = namespace + COLON + name + COLON + version;
        }
    }

    /**
     * Returns an instance of {@code ImmutableDefinitionIdentifier}.
     *
     * @param namespace the namespace of the returned Identifier.
     * @param name the name of the returned Identifier.
     * @param version the version of the returned Identifier.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if any argument is empty.
     */
    static DefinitionIdentifier getInstance(final CharSequence namespace,
            final CharSequence name, final CharSequence version) {

        return new ImmutableDefinitionIdentifier(namespace, name, version, null);
    }

    /**
     * Returns an instance of {@code ImmutableDefinitionIdentifier} with a provided {@link URL}.
     * The {@code namespace}, {@code name} and {@code version} of the returned Identifier are empty strings as a result.
     *
     * @param url the {@code URL} to use as complete content of the returned Identifier.
     * @return the instance.
     * @throws NullPointerException if {@code url} was {@code null}.
     */
    static DefinitionIdentifier getInstance(final URL url) {
        return new ImmutableDefinitionIdentifier("", "", "", checkNotNull(url, "url"));
    }

    /**
     * Parses the specified CharSequence and returns an instance of {@code ImmutableDefinitionIdentifier}.
     *
     * @param definitionIdentifier CharSequence-representation of a Definition Identifier.
     * @return the instance.
     * @throws NullPointerException if {@code definitionIdentifier} is {@code null}.
     * @throws DefinitionIdentifierInvalidException if {@code definitionIdentifier} is invalid.
     */
    static DefinitionIdentifier ofParsed(final CharSequence definitionIdentifier) {
        checkNotNull(definitionIdentifier, "CharSequence-representation of the identifier");

        if (definitionIdentifier instanceof DefinitionIdentifier) {
            return (DefinitionIdentifier) definitionIdentifier;
        }

        final Matcher matcher = IDENTIFIER_PATTERN.matcher(definitionIdentifier);
        @Nullable final URL url;
        if (!matcher.matches()) {
            try {
                url = new URL(definitionIdentifier.toString());
                if (!isValidHttpUrl(url)) {
                    throw new DefinitionIdentifierInvalidException(definitionIdentifier);
                }
            } catch (final MalformedURLException e) {
                throw new DefinitionIdentifierInvalidException(definitionIdentifier);
            }
        } else {
            url = null;
        }

        if (null != url) {
            return getInstance(url);
        } else {
            final String parsedNamespace = matcher.group(CapturingGroup.NAMESPACE);
            final String parsedName = matcher.group(CapturingGroup.NAME);
            final String parsedVersion = matcher.group(CapturingGroup.VERSION);

            return getInstance(parsedNamespace, parsedName, parsedVersion);
        }
    }

    /**
     * Simple and cheap check whether the given URL is a valid HTTP URL with non-empty hostname.
     *
     * @param url the URL to check for being a valid HTTP URL.
     * @return {@code true} when the URL is a valid HTTP URL.
     */
    private static boolean isValidHttpUrl(final URL url) {
        return url.getProtocol().startsWith("http") && !url.getHost().isEmpty();
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
    public Optional<URL> getUrl() {
        return Optional.ofNullable(url);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableDefinitionIdentifier that = (ImmutableDefinitionIdentifier) o;
        return Objects.equals(namespace, that.namespace) &&
                Objects.equals(name, that.name) &&
                Objects.equals(version, that.version) &&
                Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, name, version, url);
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
     * A mutable builder with a fluent API for a {@link java.util.regex.Pattern} for parsing string representations of a
     * FeatureDefinitionIdentifier.
     */
    @NotThreadSafe
    private static final class DefinitionIdentifierPatternBuilder {

        private static final Pattern ELEMENT_PATTERN = Pattern.compile("[_a-zA-Z0-9\\-.]+");

        private final StringBuilder stringBuilder;

        private DefinitionIdentifierPatternBuilder() {
            stringBuilder = new StringBuilder();
        }

        /**
         * Returns an instance of {@code DefinitionIdentifierPatternBuilder}.
         *
         * @return the instance.
         */
        public static DefinitionIdentifierPatternBuilder getInstance() {
            return new DefinitionIdentifierPatternBuilder();
        }

        /**
         * Adds a capturing group with the specified name.
         *
         * @param elementCapturingGroupName the name of the capturing group to be added.
         * @return this builder instance to allow method chaining.
         */
        public DefinitionIdentifierPatternBuilder addCapturingGroup(final CharSequence elementCapturingGroupName) {
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
