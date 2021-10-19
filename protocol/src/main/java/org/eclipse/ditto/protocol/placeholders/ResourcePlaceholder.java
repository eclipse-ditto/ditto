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

import org.eclipse.ditto.base.model.signals.WithResource;
import org.eclipse.ditto.placeholders.Placeholder;

/**
 * A {@link Placeholder} that requires the {@link WithResource} to resolve its placeholders.
 *
 * @since 2.2.0
 */
public interface ResourcePlaceholder extends Placeholder<WithResource> {

    /**
     * Returns the singleton instance of the {@link ResourcePlaceholder}.
     *
     * @return the singleton instance.
     */
    static ResourcePlaceholder getInstance() {
        return ImmutableResourcePlaceholder.INSTANCE;
    }
}
