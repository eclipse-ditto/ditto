/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement.validators;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.enforcement.pre_enforcement.PreEnforcer;

import akka.actor.ActorSystem;

/**
 * Checks that commands that modify entities cause no harm downstream.
 */
public final class CommandWithOptionalEntityValidator implements PreEnforcer {

    public CommandWithOptionalEntityValidator(final ActorSystem actorSystem) {
    }

    public static CommandWithOptionalEntityValidator createInstance() {
        return new CommandWithOptionalEntityValidator(null);
    }

    @Override
    public CompletionStage<DittoHeadersSettable<?>> apply(final DittoHeadersSettable<?> withDittoHeaders) {
        return checkForHarmfulEntity(withDittoHeaders);
    }

    private static <T extends DittoHeadersSettable<?>> CompletionStage<T> checkForHarmfulEntity(
            final T withDittoHeaders) {
        if (withDittoHeaders instanceof Command && withDittoHeaders instanceof WithOptionalEntity) {
            final Optional<JsonValue> optionalEntity = ((WithOptionalEntity) withDittoHeaders).getEntity();
            if (optionalEntity.isPresent() && isJsonValueIllegal(optionalEntity.get())) {
                throw buildError(withDittoHeaders);
            }
        }
        return CompletableFuture.completedFuture(withDittoHeaders);
    }

    private static boolean isJsonValueIllegal(final JsonValue entity) {
        final boolean result;
        if (entity.isArray()) {
            result = entity.asArray().stream().anyMatch(CommandWithOptionalEntityValidator::isJsonValueIllegal);
        } else if (entity.isObject()) {
            result = entity.asObject().stream().anyMatch(CommandWithOptionalEntityValidator::isJsonFieldIllegal);
        } else if (entity.isString()) {
            result = isStringIllegal(entity.asString());
        } else {
            result = false;
        }
        return result;
    }

    private static boolean isJsonFieldIllegal(final JsonField jsonField) {
        return isStringIllegal(jsonField.getKeyName()) || isJsonValueIllegal(jsonField.getValue());
    }

    private static boolean isStringIllegal(final String string) {
        for (int i = 0; i < string.length(); ++i) {
            if (string.charAt(i) == 0) {
                return true;
            }
        }
        return false;
    }

    private static DittoRuntimeException buildError(final DittoHeadersSettable<?> withDittoHeaders) {
        final JsonParseException jsonException = JsonParseException.newBuilder()
                .message("JSON contains a string with the forbidden character '\\u0000'")
                .description("We do not accept any JSON strings containing the null character.")
                .build();
        return new DittoJsonException(jsonException).setDittoHeaders(withDittoHeaders.getDittoHeaders());
    }
}
