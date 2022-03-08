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
 *
 * @since 2.4.0
 */
public interface SinglePrefixedAtContext extends SingleAtContext {

    static SinglePrefixedAtContext of(final CharSequence prefix, final SingleUriAtContext singleUriAtContext) {
        return new ImmutableSinglePrefixedAtContext(prefix, singleUriAtContext);
    }

    String getPrefix();

    SingleUriAtContext getDelegateContext();
}
