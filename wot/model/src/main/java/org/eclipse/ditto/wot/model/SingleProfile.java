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
 * A SingleProfile is a single String {@link Profile}.
 *
 * @since 2.4.0
 */
public interface SingleProfile extends Profile, IRI {

    /**
     * Creates a SingleProfile from the specified profile IRI.
     *
     * @param charSequence the profile IRI.
     * @return the SingleProfile.
     */
    static SingleProfile of(final CharSequence charSequence) {
        if (charSequence instanceof SingleProfile) {
            return (SingleProfile) charSequence;
        }
        return new ImmutableSingleProfile(charSequence);
    }
}
