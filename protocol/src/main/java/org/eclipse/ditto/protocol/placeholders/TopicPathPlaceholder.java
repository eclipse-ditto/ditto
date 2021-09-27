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
import org.eclipse.ditto.protocol.TopicPath;

/**
 * A {@link Placeholder} that requires the {@link TopicPath} to resolve its placeholders.
 *
 * @since 2.2.0
 */
public interface TopicPathPlaceholder extends Placeholder<TopicPath> {

    /**
     * Returns the singleton instance of the {@link TopicPathPlaceholder}.
     *
     * @return the singleton instance.
     */
    static TopicPathPlaceholder getInstance() {
        return ImmutableTopicPathPlaceholder.INSTANCE;
    }
}
