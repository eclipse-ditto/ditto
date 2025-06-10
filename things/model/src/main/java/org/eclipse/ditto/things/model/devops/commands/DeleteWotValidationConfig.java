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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Command to delete a WoT validation configuration.
 * <p>
 * This command encapsulates all information required to delete a WoT validation config, including the config ID
 * and the Ditto headers. It is immutable and thread-safe.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableCommand(typePrefix = WotValidationConfigCommand.TYPE_PREFIX, name = DeleteWotValidationConfig.NAME)
public final class DeleteWotValidationConfig extends AbstractWotValidationConfigCommand<DeleteWotValidationConfig> implements WotValidationConfigCommand<DeleteWotValidationConfig> {

    /**
     * Name of this command.
     * This is used to identify the command type in the command journal and for deserialization.
     */
    public static final String NAME = "deleteWotValidationConfig";

    /**
     * Type of this command.
     * This is the full type identifier including the prefix.
     */
    private static final String TYPE = WotValidationConfigCommand.TYPE_PREFIX + NAME;

    /**
     * Constructs a new {@code DeleteWotValidationConfig} command.
     *
     * @param configId the ID of the config to delete.
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code configId} is empty.
     */
    private DeleteWotValidationConfig(
            final WotValidationConfigId configId,
            final DittoHeaders dittoHeaders) {
        super(TYPE, configId, dittoHeaders);
        if (configId.toString().isEmpty()) {
            throw new IllegalArgumentException("Config ID must not be empty");
        }
    }

    /**
     * Creates a new instance of {@code DeleteWotValidationConfig}.
     *
     * @param configId the ID of the validation config.
     * @param dittoHeaders the headers of the command.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DeleteWotValidationConfig of(
            final WotValidationConfigId configId,
            final DittoHeaders dittoHeaders) {
        return new DeleteWotValidationConfig(configId, dittoHeaders);
    }

    /**
     * Creates a new instance of {@code DeleteWotValidationConfig} from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers of the command.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static DeleteWotValidationConfig fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String configIdString = jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID);
        return of(WotValidationConfigId.of(configIdString), dittoHeaders);
    }

    @Override
    public String getTypePrefix() {
        return WotValidationConfigCommand.TYPE_PREFIX;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public Category getCategory() {
        return Category.DELETE;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getManifest() {
        return getType();
    }

    @Override
    public DeleteWotValidationConfig setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getEntityId(), dittoHeaders);
    }
}
