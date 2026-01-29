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
 * OAuth2Flow describes the {@code flow} of a {@link OAuth2SecurityScheme}.
 *
 * @since 2.4.0
 */
public interface OAuth2Flow extends CharSequence {

    /**
     * Authorization code flow.
     *
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#oauth2securityscheme">WoT TD OAuth2SecurityScheme (flow)</a>
     */
    OAuth2Flow CODE = of("code");

    /**
     * Client credentials flow.
     *
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#oauth2securityscheme">WoT TD OAuth2SecurityScheme (flow)</a>
     */
    OAuth2Flow CLIENT = of("client");

    /**
     * Device authorization flow.
     *
     * @see <a href="https://www.w3.org/TR/wot-thing-description11/#oauth2securityscheme">WoT TD OAuth2SecurityScheme (flow)</a>
     */
    OAuth2Flow DEVICE = of("device");

    /**
     * Creates an OAuth2Flow from the specified string.
     *
     * @param charSequence the flow name.
     * @return the OAuth2Flow.
     */
    static OAuth2Flow of(final CharSequence charSequence) {
        if (charSequence instanceof OAuth2Flow) {
            return (OAuth2Flow) charSequence;
        }
        return new ImmutableOAuth2Flow(charSequence);
    }
}
