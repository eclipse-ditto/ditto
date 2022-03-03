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
 * A Profile defines the WoT {@code profile} mechanisms followed by this Thing Description / Model and the corresponding
 * Thing implementation.
 * It may present itself as {@link SingleProfile} or as {@link MultipleProfile} containing multiple
 * {@link SingleProfile}s.
 *
 * @since 2.4.0
 */
public interface Profile {

    static SingleProfile newSingleProfile(final CharSequence charSequence) {
        return SingleProfile.of(charSequence);
    }

    static MultipleProfile newMultipleProfile(final Collection<SingleProfile> singleProfiles) {
        return MultipleProfile.of(singleProfiles);
    }
}
