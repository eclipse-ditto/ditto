/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.base;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.WithManifest;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * A service message that incites to action or conveys notice or warning.
 *
 * @param <T> the type of the implementing class.
 */
public interface Signal<T extends Signal> extends Jsonifiable.WithPredicate<JsonObject, JsonField>,
        WithDittoHeaders<T>, WithManifest, WithType, WithId, WithName, WithResource {

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
