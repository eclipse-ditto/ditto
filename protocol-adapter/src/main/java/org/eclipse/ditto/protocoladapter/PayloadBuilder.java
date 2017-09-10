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
package org.eclipse.ditto.protocoladapter;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;

/**
 * A builder to create {@link Payload} instances.
 */
public interface PayloadBuilder {

    /**
     * Sets the given {@code value} to this builder. A previously set value is replaced.
     *
     * @param value the value to set.
     * @return this builder to allow method chaining.
     */
    PayloadBuilder withValue(JsonValue value);

    /**
     * Sets the given {@code status} to this builder. A previously set status is replaced.
     *
     * @param status the status to set.
     * @return this builder to allow method chaining.
     */
    PayloadBuilder withStatus(HttpStatusCode status);

    /**
     * Sets the given {@code status} to this builder. A previously set status is replaced.
     *
     * @param status the status to set.
     * @return this builder to allow method chaining.
     */
    PayloadBuilder withStatus(int status);

    /**
     * Sets the given {@code revision} to this builder. A previously set revision is replaced.
     *
     * @param revision the revision to set.
     * @return this builder to allow method chaining.
     */
    PayloadBuilder withRevision(long revision);

    /**
     * Sets the given {@code fields} to this builder. Previously set fields are replaced.
     *
     * @param fields the fields to set.
     * @return this builder to allow method chaining.
     */
    PayloadBuilder withFields(JsonFieldSelector fields);

    /**
     * Sets the given {@code fields} to this builder. Previously set fields are replaced.
     *
     * @param fields the fields to set.
     * @return this builder to allow method chaining.
     */
    PayloadBuilder withFields(String fields);

    /**
     * Creates a new {@code Payload} from the previously set values.
     *
     * @return the payload.
     */
    Payload build();

}
