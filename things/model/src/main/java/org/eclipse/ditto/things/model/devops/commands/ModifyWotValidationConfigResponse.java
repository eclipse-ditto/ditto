/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.devops.commands;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Response to a {@link ModifyWotValidationConfig} command.
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyWotValidationConfigResponse.TYPE)
public final class ModifyWotValidationConfigResponse
        extends AbstractWotValidationConfigCommandResponse<ModifyWotValidationConfigResponse>
        implements WithOptionalEntity<ModifyWotValidationConfigResponse> {

    static final String TYPE = TYPE_PREFIX + ModifyWotValidationConfig.NAME;


    private ModifyWotValidationConfigResponse(final WotValidationConfigId configId,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatus.NO_CONTENT, configId, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyWotValidationConfigResponse}.
     *
     * @param configId the ID of the WoT validation config.
     * @param dittoHeaders the headers of the response.
     * @return a new ModifyWotValidationConfigResponse.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyWotValidationConfigResponse of(final WotValidationConfigId configId,
            final DittoHeaders dittoHeaders) {
        return new ModifyWotValidationConfigResponse(configId, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyWotValidationConfigResponse} for a modified config.
     *
     * @param configId the ID of the WoT validation config.
     * @param dittoHeaders the headers of the response.
     * @return a new ModifyWotValidationConfigResponse.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyWotValidationConfigResponse modified(final WotValidationConfigId configId,
            final DittoHeaders dittoHeaders) {
        return new ModifyWotValidationConfigResponse(configId, dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyWotValidationConfigResponse} from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     */
    public static ModifyWotValidationConfigResponse fromJson(final String jsonString,
            final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ModifyWotValidationConfigResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     */
    public static ModifyWotValidationConfigResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        final WotValidationConfigId configId =
                WotValidationConfigId.of(jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID));
        return modified(configId, dittoHeaders);
    }


    @Override
    public ModifyWotValidationConfigResponse setEntity(final JsonValue entity) {
        return of(configId, getDittoHeaders());
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public ModifyWotValidationConfigResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifyWotValidationConfigResponse(configId, dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), configId);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ModifyWotValidationConfigResponse that = (ModifyWotValidationConfigResponse) o;
        return Objects.equals(configId, that.configId);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifyWotValidationConfigResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", configId=" + configId +
                "]";
    }
} 