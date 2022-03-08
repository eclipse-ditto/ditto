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
 * AtContext represents the JSON-LD type which is included in the Json document as {@code "@type"}.
 * "JSON-LD keyword to label the object with semantic tags (or types)."
 *
 * @since 2.4.0
 */
public interface AtType {

    static SingleAtType newSingleAtType(final CharSequence type) {
        return SingleAtType.of(type);
    }

    static MultipleAtType newMultipleAtType(final Collection<SingleAtType> types) {
        return MultipleAtType.of(types);
    }
}
