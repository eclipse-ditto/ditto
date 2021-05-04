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
package org.eclipse.ditto.base.model.signals;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.WithManifest;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * A service message that incites to action or conveys notice or warning.
 *
 * @param <T> the type of the implementing class.
 */
public interface Signal<T extends Signal<T>> extends Jsonifiable.WithPredicate<JsonObject, JsonField>,
        DittoHeadersSettable<T>, WithManifest, WithType, WithName, WithResource {

    /**
     * Returns the name of the signal. This is gathered by the type of the signal by default.
     *
     * @return the name.
     */
    @Override
    default String getName() {
        return getType().contains(":") ? getType().split(":")[1] : getType();
    }

}
