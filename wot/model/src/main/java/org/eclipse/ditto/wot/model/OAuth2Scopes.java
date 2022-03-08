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
 * A SingleOAuth2Scopes defines the {@code scopes} to be used in an {@link OAuth2SecurityScheme}.
 * It may present itself as {@link SingleOAuth2Scopes} or as {@link MultipleOAuth2Scopes} containing multiple
 *{@link SingleOAuth2Scopes}s.
 *
 * @since 2.4.0
 */
public interface OAuth2Scopes {

    static SingleOAuth2Scopes newSingleOAuth2Scopes(final CharSequence scope) {
        return SingleOAuth2Scopes.of(scope);

    }
    static MultipleOAuth2Scopes newMultipleOAuth2Scopes(final Collection<SingleOAuth2Scopes> scopes) {
        return MultipleOAuth2Scopes.of(scopes);
    }
}
