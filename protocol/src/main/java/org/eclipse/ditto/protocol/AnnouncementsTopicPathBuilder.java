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
package org.eclipse.ditto.protocol;

/**
 * Builder to create a topic path for announcements.
 *
 * @since 2.0.0
 */
public interface AnnouncementsTopicPathBuilder extends TopicPathBuildable {

    /**
     * Set the announcement name on the topic path.
     *
     * @return this builder.
     */
    AnnouncementsTopicPathBuilder name(String name);
}
