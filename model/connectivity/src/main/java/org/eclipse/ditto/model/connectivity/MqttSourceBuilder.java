/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.model.connectivity;

/**
 * A mutable builder for a {@link MqttSource} with a fluent API.
 */
public interface MqttSourceBuilder extends SourceBuilder<MqttSourceBuilder> {

    /**
     * Qos mqtt source builder.
     *
     * @param qos the mqtt qos vaue
     * @return this builder
     */
    MqttSourceBuilder qos(int qos);

    /**
     * @return the new {@link MqttSource} instance
     */
    MqttSource build();

}