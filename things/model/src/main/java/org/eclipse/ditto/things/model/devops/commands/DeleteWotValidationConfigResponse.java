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
 * Response to a {@link DeleteWotValidationConfig} command.
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableCommandResponse(type = DeleteWotValidationConfigResponse.TYPE)
public final class DeleteWotValidationConfigResponse extends AbstractWotValidationConfigCommandResponse<DeleteWotValidationConfigResponse>
        implements WithOptionalEntity<DeleteWotValidationConfigResponse> {

    static final String TYPE = WotValidationConfigCommandResponse.TYPE_PREFIX + DeleteWotValidationConfig.NAME;

    private DeleteWotValidationConfigResponse(final WotValidationConfigId configId,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatus.NO_CONTENT, configId, dittoHeaders);
    }

    /**
     * Creates a new instance of {@code DeleteWotValidationConfigResponse}.
     *
     * @param configId the ID of the deleted WoT validation config.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DeleteWotValidationConfigResponse of(final WotValidationConfigId configId,
            final DittoHeaders dittoHeaders) {
        return new DeleteWotValidationConfigResponse(configId, dittoHeaders);
    }

    /**
     * Creates a new {@code DeleteWotValidationConfigResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static DeleteWotValidationConfigResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        final String configIdString = jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID);
        return of(WotValidationConfigId.of(configIdString), dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public DeleteWotValidationConfigResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(configId, dittoHeaders);
    }

    @Override
    public DeleteWotValidationConfigResponse setEntity(final JsonValue entity) {
        return this;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return super.equals(o);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof DeleteWotValidationConfigResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                "]";
    }
} 