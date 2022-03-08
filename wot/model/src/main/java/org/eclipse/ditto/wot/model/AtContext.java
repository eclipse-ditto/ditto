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
}
