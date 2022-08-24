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
package org.eclipse.ditto.wot.model;

import java.util.Collection;
import java.util.Optional;

/**
 * AtContext represents the JSON-LD context which is included in the Json document as {@code "@context"}.
 * "JSON-LD keyword to define shorthand names called terms that are used throughout a TD document."
 *
 * @since 2.4.0
 */
public interface AtContext {

    static SingleUriAtContext newSingleUriAtContext(final CharSequence context) {
        return SingleUriAtContext.of(context);
    }

    static SinglePrefixedAtContext newSinglePrefixedAtContext(final CharSequence prefix,
            final SingleUriAtContext context) {
        return SinglePrefixedAtContext.of(prefix, context);
    }

    static MultipleAtContext newMultipleAtContext(final Collection<SingleAtContext> contexts) {
        return MultipleAtContext.of(contexts);
    }

    /**
     * Determines the {@code @context} prefix of the passed IRI in {@code singleUriAtContext}.
     * If this {@code AtContext} instance is of type {@code MultipleAtContext} AND contains such an IRI, the prefix
     * for that context entry is returned.
     *
     * @param singleUriAtContext the IRI to determine the context prefix for.
     * @return the determined context prefix if the IRI was part of this {@code AtContext} instance or an empty optional
     * instead.
     * @since 3.0.0
     */
    default Optional<String> determinePrefixFor(final SingleUriAtContext singleUriAtContext) {
        if (this instanceof MultipleAtContext) {
            return ((MultipleAtContext) this).determinePrefixForSingleUriAtContext(singleUriAtContext);
        } else {
            return Optional.empty();
        }
    }
}
