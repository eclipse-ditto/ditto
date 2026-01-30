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

    /**
     * No security required (nosec).
     *
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#nosecurityscheme">WoT TD NoSecurityScheme</a>
     */
    SecuritySchemeScheme NOSEC = of("nosec");

    /**
     * Auto-negotiated security (auto).
     *
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#autosecurityscheme">WoT TD AutoSecurityScheme</a>
     */
    SecuritySchemeScheme AUTO = of("auto");

    /**
     * Combination of security schemes (combo).
     *
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#combosecurityscheme">WoT TD ComboSecurityScheme</a>
     */
    SecuritySchemeScheme COMBO = of("combo");

    /**
     * Basic authentication (basic).
     *
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#basicsecurityscheme">WoT TD BasicSecurityScheme</a>
     */
    SecuritySchemeScheme BASIC = of("basic");

    /**
     * Digest authentication (digest).
     *
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#digestsecurityscheme">WoT TD DigestSecurityScheme</a>
     */
    SecuritySchemeScheme DIGEST = of("digest");

    /**
     * API key authentication (apikey).
     *
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#apikeysecurityscheme">WoT TD APIKeySecurityScheme</a>
     */
    SecuritySchemeScheme APIKEY = of("apikey");

    /**
     * Bearer token authentication (bearer).
     *
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bearersecurityscheme">WoT TD BearerSecurityScheme</a>
     */
    SecuritySchemeScheme BEARER = of("bearer");

    /**
     * Pre-shared key authentication (psk).
     *
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#psksecurityscheme">WoT TD PSKSecurityScheme</a>
     */
    SecuritySchemeScheme PSK = of("psk");

    /**
     * OAuth 2.0 authentication (oauth2).
     *
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#oauth2securityscheme">WoT TD OAuth2SecurityScheme</a>
     */
    SecuritySchemeScheme OAUTH2 = of("oauth2");

    /**
     * Creates a SecuritySchemeScheme from the specified string.
     *
     * @param charSequence the scheme name.
     * @return the SecuritySchemeScheme.
     */
    static SecuritySchemeScheme of(final CharSequence charSequence) {
        if (charSequence instanceof SecuritySchemeScheme) {
            return (SecuritySchemeScheme) charSequence;
        }
        return new ImmutableSecuritySchemeScheme(charSequence);
    }

}
