/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.enforcement.placeholders;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.Placeholders;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayPlaceholderNotResolvableException;

/**
 * An algorithm for placeholder substitution based on {@link DittoHeaders}.
 */
@Immutable
public final class HeaderBasedPlaceholderSubstitutionAlgorithm {

    private final Map<String, Function<DittoHeaders, String>> replacementDefinitions;
    private final List<CharSequence> knownPlaceHolders;

    private HeaderBasedPlaceholderSubstitutionAlgorithm(final Map<String, Function<DittoHeaders, String>> replacementDefinitions) {
        this.replacementDefinitions = Collections.unmodifiableMap(new LinkedHashMap<>(replacementDefinitions));
        this.knownPlaceHolders = Collections.unmodifiableList(new ArrayList<>(replacementDefinitions.keySet()));
    }

    /**
     * Creates a new instance based on the given replacement definitions.
     * @param replacementDefinitions the replacement definitions.
     *
     * @return the created instance.
     */
    public static HeaderBasedPlaceholderSubstitutionAlgorithm newInstance(
            final Map<String, Function<DittoHeaders, String>> replacementDefinitions) {

        return new HeaderBasedPlaceholderSubstitutionAlgorithm(requireNonNull(replacementDefinitions));
    }

    /**
     * Substitutes all placeholders contained in the {@code input} based on the {@link DittoHeaders} contained in
     * {@code withDittoHeaders}.
     *
     * @param input the input.
     * @param withDittoHeaders the instance of {@link WithDittoHeaders}, from which the headers are extracted
     * (normally a command).
     * @return the replaced input, if the input contains placeholders; the (same) input object, if no placeholders
     * were contained in the input.
     */
    public String substitute(final String input, final WithDittoHeaders withDittoHeaders) {
        requireNonNull(input);
        requireNonNull(withDittoHeaders);

        return substitute(input, withDittoHeaders.getDittoHeaders());
    }

    /**
     * Substitutes all placeholders contained in the {@code input} based on the {@code dittoHeaders}.
     *
     * @param input the input.
     * @param dittoHeaders the extracted {@link DittoHeaders}.
     * @return the replaced input, if the input contains placeholders; the (same) input object, if no placeholders
     * were contained in the input.
     */
    public String substitute(final String input, final DittoHeaders dittoHeaders) {
        requireNonNull(input);
        requireNonNull(dittoHeaders);

        final Function<String, String> placeholderReplacerFunction = createReplacerFunction(dittoHeaders);
        final Function<String, DittoRuntimeException> unresolvedInputHandler =
                createUnresolvedInputHandler(dittoHeaders);

        return Placeholders.substitute(input, placeholderReplacerFunction, unresolvedInputHandler);
    }

    private Function<String, String> createReplacerFunction(final DittoHeaders dittoHeaders) {
        return placeholder -> {
            final Function<DittoHeaders, String> placeholderResolver = replacementDefinitions.get(placeholder);
            if (placeholderResolver == null) {
                throw GatewayPlaceholderNotResolvableException.newUnknownPlaceholderBuilder(placeholder, knownPlaceHolders)
                        .dittoHeaders(dittoHeaders)
                        .build();
            }
            final String replacement = placeholderResolver.apply(dittoHeaders);
            if (replacement == null) {
                throw new IllegalStateException("Currently not supported: All resolvers have to return a non-null " +
                        "result!");
            }
            return replacement;
        };
    }

    private Function<String, DittoRuntimeException> createUnresolvedInputHandler(
            final DittoHeaders dittoHeaders) {
        return input -> GatewayPlaceholderNotResolvableException.newNotResolvableInputBuilder(input)
                .dittoHeaders(dittoHeaders)
                .build();
    }

}
