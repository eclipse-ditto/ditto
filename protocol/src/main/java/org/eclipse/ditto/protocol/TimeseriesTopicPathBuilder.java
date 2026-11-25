/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol;

/**
 * Builder for the timeseries criterion of a {@link TopicPath} (Phase 1 carries only the {@code
 * retrieve} action, future phases will add aggregation actions). Reached through
 * {@link TopicPathBuilder#timeseries()}.
 *
 * @since 4.0.0
 */
public interface TimeseriesTopicPathBuilder extends TopicPathBuildable {

    /**
     * Sets the {@code Action} of this builder to {@link TopicPath.Action#RETRIEVE}. A previously
     * set action is replaced.
     *
     * @return this builder to allow method chaining.
     */
    TopicPathBuildable retrieve();
}
