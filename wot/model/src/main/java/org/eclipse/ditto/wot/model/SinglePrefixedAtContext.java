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

/**
 * A SinglePrefixedAtContext is a {@link SingleAtContext} containing a {@code prefix} and a {@link SingleUriAtContext}.
 * <p>
 * Prefixed contexts allow using CURIEs (Compact URIs) in Thing Descriptions, where a short prefix
 * can be used instead of the full namespace IRI.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#sec-context">WoT TD @context</a>
 * @since 2.4.0
 */
public interface SinglePrefixedAtContext extends SingleAtContext {

    /**
     * Creates a SinglePrefixedAtContext from the specified prefix and context IRI.
     *
     * @param prefix the prefix to use for this context.
     * @param singleUriAtContext the IRI context that this prefix refers to.
     * @return the SinglePrefixedAtContext.
     */
    static SinglePrefixedAtContext of(final CharSequence prefix, final SingleUriAtContext singleUriAtContext) {
        return new ImmutableSinglePrefixedAtContext(prefix, singleUriAtContext);
    }

    /**
     * Returns the prefix for this context.
     *
     * @return the prefix.
     */
    String getPrefix();

    /**
     * Returns the IRI context that this prefix refers to.
     *
     * @return the delegate context IRI.
     */
    SingleUriAtContext getDelegateContext();
}
