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
 * TmOptionalElement holds a single "tm:optional" element of a ThingModel.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#thing-model-td-required">WoT TD Thing Model Optional</a>
 * @since 3.0.0
 */
public interface TmOptionalElement extends CharSequence {

    static TmOptionalElement of(final CharSequence charSequence) {
        if (charSequence instanceof TmOptionalElement) {
            return (TmOptionalElement) charSequence;
        }
        return new ImmutableTmOptionalElement(charSequence);
    }
}
