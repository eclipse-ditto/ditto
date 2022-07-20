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
package org.eclipse.ditto.policies.enforcement.pre;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Pre-Enforcer which checks that commands that modify entities cause no harm downstream.
 */
public final class CommandWithOptionalEntityPreEnforcer implements PreEnforcer {

    /**
     * Constructs a new instance of CommandWithOptionalEntityPreEnforcer extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    @SuppressWarnings("unused")
    public CommandWithOptionalEntityPreEnforcer(final ActorSystem actorSystem, final Config config) {
        // no-op
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal) {
        return checkForHarmfulEntity(signal);
    }

    private static <T extends Signal<?>> CompletionStage<T> checkForHarmfulEntity(final T signal) {

        if (signal instanceof Command && signal instanceof WithOptionalEntity) {
            final Optional<JsonValue> optionalEntity = ((WithOptionalEntity) signal).getEntity();
            if (optionalEntity.isPresent() && isJsonValueIllegal(optionalEntity.get())) {
                throw buildError(signal);
            }
        }
        return CompletableFuture.completedFuture(signal);
    }

    private static boolean isJsonValueIllegal(final JsonValue entity) {
        final boolean result;
        if (entity.isArray()) {
            result = entity.asArray().stream().anyMatch(CommandWithOptionalEntityPreEnforcer::isJsonValueIllegal);
        } else if (entity.isObject()) {
            result = entity.asObject().stream().anyMatch(CommandWithOptionalEntityPreEnforcer::isJsonFieldIllegal);
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
