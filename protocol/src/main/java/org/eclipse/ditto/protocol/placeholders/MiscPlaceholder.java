/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.placeholders;

import org.eclipse.ditto.placeholders.Placeholder;

/**
 * A {@link Placeholder} that resolves miscellaneous placeholders, e.g. from the runtime environment like the
 * current time.
 *
 * @since 2.2.0
 */
public interface MiscPlaceholder extends Placeholder<Object> {

    /**
     * The used prefix of this placeholder.
     */
    String PREFIX = "misc";

    /**
     * Returns the singleton instance of the {@link MiscPlaceholder}.
     *
     * @return the singleton instance.
     */
    static MiscPlaceholder getInstance() {
        return ImmutableMiscPlaceholder.INSTANCE;
    }
}
