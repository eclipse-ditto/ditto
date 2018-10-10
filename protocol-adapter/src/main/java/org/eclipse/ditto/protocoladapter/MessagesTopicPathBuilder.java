/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.protocoladapter;

/**
 * Builder to create a topic path for messages.
 */
public interface MessagesTopicPathBuilder extends TopicPathBuildable {

    /**
     * Sets a custom subject on the topic path. This custom subject is only considered if there is no predefined
     * {@link TopicPath.Action}.
     *
     * @param subject the subject to set on the topic path.
     * @return this builder to allow method chaining.
     */
    MessagesTopicPathBuilder subject(final String subject);
}
