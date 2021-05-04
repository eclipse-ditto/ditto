/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import java.util.List;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * References multiple mappings from a {@link PayloadMappingDefinition} in a {@link Source} or a {@link Target}.
 */
public interface PayloadMapping extends Jsonifiable<JsonArray>  {

    /**
     * @return the payload mappings that should be applied for inbound/outbound messages
     */
    List<String> getMappings();

    /**
     * @return {@code true} if no mappings are defined
     */
    boolean isEmpty();

}
