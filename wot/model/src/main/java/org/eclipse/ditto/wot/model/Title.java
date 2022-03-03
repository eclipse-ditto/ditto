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
 * A Title "provides a human-readable title (e.g., display a text for UI representation) based on a default language."
 *
 * @since 2.4.0
 */
public interface Title extends CharSequence {

    static Title of(final CharSequence charSequence) {
        if (charSequence instanceof Title) {
            return (Title) charSequence;
        }
        return new ImmutableTitle(charSequence);
    }
}
