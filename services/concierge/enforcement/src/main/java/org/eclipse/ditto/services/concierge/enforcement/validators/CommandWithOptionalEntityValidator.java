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
package org.eclipse.ditto.services.concierge.enforcement.validators;

import java.util.Optional;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.signals.base.WithOptionalEntity;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Checks that commands that modify entities cause no harm downstream.
 */
public final class CommandWithOptionalEntityValidator implements
        Function<WithDittoHeaders, WithDittoHeaders> {

    private static final CommandWithOptionalEntityValidator INSTANCE = new CommandWithOptionalEntityValidator();

    public static CommandWithOptionalEntityValidator getInstance() {
        return INSTANCE;
    }

    @Override
    public WithDittoHeaders apply(final WithDittoHeaders withDittoHeaders) {
        return checkForHarmfulEntity(withDittoHeaders);
    }

    private static WithDittoHeaders checkForHarmfulEntity(final WithDittoHeaders withDittoHeaders) {
        if (withDittoHeaders instanceof Command && withDittoHeaders instanceof WithOptionalEntity) {
            final Optional<JsonValue> optionalEntity = ((WithOptionalEntity) withDittoHeaders).getEntity();
            if (optionalEntity.isPresent() && isJsonValueIllegal(optionalEntity.get())) {
                throw buildError(withDittoHeaders);
            }
        }
        return withDittoHeaders;
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

    private static DittoRuntimeException buildError(final WithDittoHeaders withDittoHeaders) {
        final JsonParseException jsonException = JsonParseException.newBuilder()
                .message("JSON contains a string with the forbidden character '\\u0000'")
                .description("We do not accept any JSON strings containing the null character.")
                .build();
        return new DittoJsonException(jsonException).setDittoHeaders(withDittoHeaders.getDittoHeaders());
    }
}
