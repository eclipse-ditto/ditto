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
 * A SingleOAuth2Scopes is a single String {@link OAuth2Scopes}.
 *
 * @since 2.4.0
 */
public interface SingleOAuth2Scopes extends OAuth2Scopes, CharSequence {

    static SingleOAuth2Scopes of(final CharSequence charSequence) {
        if (charSequence instanceof SingleOAuth2Scopes) {
            return (SingleOAuth2Scopes) charSequence;
        }
        return new ImmutableSingleOAuth2Scopes(charSequence);
    }
}
