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
 * TmRequiredElement holds a single "required" element of a ThingModel.
 *
 * @see <a href="https://w3c.github.io/wot-thing-description/#thing-model-td-required">WoT TD Thing Model Required</a>
 * @since 2.4.0
 */
public interface TmRequiredElement extends CharSequence {

    static TmRequiredElement of(final CharSequence charSequence) {
        if (charSequence instanceof TmRequiredElement) {
            return (TmRequiredElement) charSequence;
        }
        return new ImmutableTmRequiredElement(charSequence);
    }
}
