/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.service.signaltransformer.placeholdersubstitution;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PipelineElement;
import org.eclipse.ditto.placeholders.PlaceholderNotResolvableException;

/**
 * An algorithm for placeholder substitution based on {@link DittoHeaders}.
 */
@Immutable
public final class HeaderBasedPlaceholderSubstitutionAlgorithm {

    private final Map<String, Function<DittoHeaders, String>> replacementDefinitions;
    private final List<CharSequence> knownPlaceHolders;

    private HeaderBasedPlaceholderSubstitutionAlgorithm(
            final Map<String, Function<DittoHeaders, String>> replacementDefinitions) {
        this.replacementDefinitions = Collections.unmodifiableMap(new LinkedHashMap<>(replacementDefinitions));
        this.knownPlaceHolders = Collections.unmodifiableList(new ArrayList<>(replacementDefinitions.keySet()));
    }

    /**
     * Creates a new instance based on the given replacement definitions.
     *
     * @param replacementDefinitions the replacement definitions.
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

        return ExpressionResolver.substitute(input, createReplacerFunction(dittoHeaders))
                .findFirst()
                .orElse(input);
    }

    private Function<String, PipelineElement> createReplacerFunction(final DittoHeaders dittoHeaders) {
        return placeholderWithSpaces -> {
            final String placeholder = placeholderWithSpaces.trim();
            final Function<DittoHeaders, String> placeholderResolver = replacementDefinitions.get(placeholder);
            if (placeholderResolver == null) {
                throw PlaceholderNotResolvableException.newUnknownPlaceholderBuilder(placeholder, knownPlaceHolders)
                        .dittoHeaders(dittoHeaders)
                        .build();
            }
            return Optional.ofNullable(placeholderResolver.apply(dittoHeaders))
                    .map(PipelineElement::resolved)
                    .orElse(PipelineElement.unresolved());
        };
    }

}
