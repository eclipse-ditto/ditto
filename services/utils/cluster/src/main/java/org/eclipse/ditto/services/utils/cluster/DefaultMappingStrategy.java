/*
 * Copyright (c) 2019 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.cluster;

import java.util.Collections;
import java.util.Map;
import java.util.function.BiFunction;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;

@Immutable
public final class DefaultMappingStrategy extends AbstractMappingStrategy {

    @Override
    protected Map<String, BiFunction<JsonObject, DittoHeaders, Jsonifiable>> getIndividualStrategies() {
        return Collections.emptyMap();
    }
}
