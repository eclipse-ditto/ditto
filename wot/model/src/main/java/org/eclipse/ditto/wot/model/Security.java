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
 * A Security defines the {@code security} to be used in either a {@link ThingDescription}, {@link ThingModel} or in a
 * {@link FormElement}.
 * It may present itself as {@link SingleSecurity} or as {@link MultipleSecurity} containing multiple
 * {@link SingleSecurity}s.
 *
 * @since 2.4.0
 */
public interface Security {

    static SingleSecurity newSingleSecurity(final CharSequence charSequence) {
        return SingleSecurity.of(charSequence);
    }

    static MultipleSecurity newMultipleSecurity(final Collection<SingleSecurity> securities) {
        return MultipleSecurity.of(securities);
    }
}
