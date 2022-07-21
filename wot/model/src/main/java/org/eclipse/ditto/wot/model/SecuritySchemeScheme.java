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
 * SecuritySchemeScheme enlists all available {@link SecurityScheme} {@code "scheme"} values and can also be a custom
 * scheme named via context extension.
 *
 * @since 2.4.0
 */
public interface SecuritySchemeScheme extends CharSequence {

    SecuritySchemeScheme NOSEC = of("nosec");

    SecuritySchemeScheme AUTO = of("auto");

    SecuritySchemeScheme COMBO = of("combo");

    SecuritySchemeScheme BASIC = of("basic");

    SecuritySchemeScheme DIGEST = of("digest");

    SecuritySchemeScheme APIKEY = of("apikey");

    SecuritySchemeScheme BEARER = of("bearer");

    SecuritySchemeScheme PSK = of("psk");

    SecuritySchemeScheme OAUTH2 = of("oauth2");

    static SecuritySchemeScheme of(final CharSequence charSequence) {
        if (charSequence instanceof SecuritySchemeScheme) {
            return (SecuritySchemeScheme) charSequence;
        }
        return new ImmutableSecuritySchemeScheme(charSequence);
    }

}
