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

import java.util.regex.Pattern;

/**
 * A SingleHreflang is a single String {@link Hreflang}.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-bcp47">BCP47 - Tags for Identifying Languages</a>
 * @since 3.0.0
 */
public interface SingleHreflang extends Hreflang, CharSequence {

    Pattern BCP47_PATTERN = Pattern.compile("^(((([A-Za-z]{2,3}(-([A-Za-z]{3}(-[A-Za-z]{3}){0,2}))?)|[A-Za-z]{4}|[A-Za-z]{5,8})(-([A-Za-z]{4}))?(-([A-Za-z]{2}|[0-9]{3}))?(-([A-Za-z0-9]{5,8}|[0-9][A-Za-z0-9]{3}))*(-([0-9A-WY-Za-wy-z](-[A-Za-z0-9]{2,8})+))*(-(x(-[A-Za-z0-9]{1,8})+))?)|(x(-[A-Za-z0-9]{1,8})+)|((en-GB-oed|i-ami|i-bnn|i-default|i-enochian|i-hak|i-klingon|i-lux|i-mingo|i-navajo|i-pwn|i-tao|i-tay|i-tsu|sgn-BE-FR|sgn-BE-NL|sgn-CH-DE)|(art-lojban|cel-gaulish|no-bok|no-nyn|zh-guoyu|zh-hakka|zh-min|zh-min-nan|zh-xiang)))$");

    static SingleHreflang of(final CharSequence charSequence) {
        if (charSequence instanceof SingleHreflang) {
            return (SingleHreflang) charSequence;
        }
        if (!BCP47_PATTERN.matcher(charSequence).matches()) {
            throw WotValidationException.newBuilder("'hreflang' value was not in specified BCP47 format: " + charSequence)
                    .build();
        }
        return new ImmutableSingleHreflang(charSequence);
    }
}
