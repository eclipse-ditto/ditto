/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
    MessagesTopicPathBuilder subject(String subject);
}
