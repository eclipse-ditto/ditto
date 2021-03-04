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

import java.time.Instant;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.metadata.Metadata;

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
    PayloadBuilder withValue(@Nullable JsonValue value);

    /**
     * Sets the given extra information which enriches the actual value of the payload.
     * Previously set extra is replaced.
     *
     * @param extra the extra payload information or {@code null}.
     * @return this builder to allow method chaining.
     */
    PayloadBuilder withExtra(@Nullable JsonObject extra);

    /**
     * Sets the given {@code status} to this builder. A previously set status is replaced.
     *
     * @param status the status to set.
     * @return this builder to allow method chaining.
     * @deprecated as of 2.0.0 please use {@link #withStatus(HttpStatus)} instead.
     */
    @Deprecated
    default PayloadBuilder withStatus(@Nullable final HttpStatusCode status) {
        return withStatus(null != status ? status.getAsHttpStatus() : null);
    }

    /**
     * Sets the given {@code status} to this builder. A previously set status is replaced.
     *
     * @param status the status to set.
     * @return this builder to allow method chaining.
     * @since 2.0.0
     */
    PayloadBuilder withStatus(@Nullable HttpStatus status);

    /**
     * Sets the given {@code status} to this builder. A previously set status is replaced.
     *
     * @param status the status to set.
     * @return this builder to allow method chaining.
     * @throws IllegalArgumentException if {@code status} is not supported.
     * @deprecated as of 2.0.0 please use {@link #withStatus(HttpStatus)} instead.
     */
    @Deprecated
    PayloadBuilder withStatus(int status);

    /**
     * Sets the given {@code revision} to this builder. A previously set revision is replaced.
     *
     * @param revision the revision to set.
     * @return this builder to allow method chaining.
     */
    PayloadBuilder withRevision(long revision);

    /**
     * Sets the given {@code timestamp} to this builder. A previously set timestamp is replaced.
     *
     * @param timestamp the timestamp to set.
     * @return this builder to allow method chaining.
     */
    PayloadBuilder withTimestamp(@Nullable Instant timestamp);

    /**
     * Sets the given {@code metadata} to this builder. A previously set metadata is replaced.
     *
     * @param metadata the metadata to set.
     * @return this builder to allow method chaining.
     * @since 1.3.0
     */
    PayloadBuilder withMetadata(@Nullable Metadata metadata);

    /**
     * Sets the given {@code fields} to this builder. Previously set fields are replaced.
     *
     * @param fields the fields to set.
     * @return this builder to allow method chaining.
     */
    PayloadBuilder withFields(@Nullable JsonFieldSelector fields);

    /**
     * Sets the given {@code fields} to this builder. Previously set fields are replaced.
     *
     * @param fields the fields to set.
     * @return this builder to allow method chaining.
     */
    PayloadBuilder withFields(@Nullable String fields);

    /**
     * Creates a new {@code Payload} from the previously set values.
     *
     * @return the payload.
     */
    Payload build();

}
