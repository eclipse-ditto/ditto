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
package org.eclipse.ditto.protocoladapter;

/**
 * Provides a build method to actually build a topic path.
 */
public interface TopicPathBuildable {

    /**
     * Creates a new {@code TopicPath} from the previously set values.
     *
     * @return the topic path.
     */
    TopicPath build();
}
