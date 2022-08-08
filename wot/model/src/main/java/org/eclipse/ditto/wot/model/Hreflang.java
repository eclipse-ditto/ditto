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
 * "The hreflang attribute specifies the language of a linked document. The value of this must be a valid language tag."
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-bcp47">BCP47 - Tags for Identifying Languages</a>
 * @since 3.0.0
 */
public interface Hreflang {

    static SingleHreflang newSingleHreflang(final CharSequence charSequence) {
        return SingleHreflang.of(charSequence);
    }

    static MultipleHreflang newMultipleHreflang(final Collection<SingleHreflang> hreflangs) {
        return MultipleHreflang.of(hreflangs);
    }
}
