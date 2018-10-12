/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.base;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFieldSelectorInvalidException;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointerInvalidException;
import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.signals.base.AbstractErrorRegistry;
import org.eclipse.ditto.signals.base.ErrorRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.base.JsonTypeNotParsableException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationProviderUnavailableException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayMethodNotAllowedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayPlaceholderNotResolvableException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayQueryTimeExceededException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceTimeoutException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceTooManyRequestsException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceUnavailableException;

/**
 * A {@link ErrorRegistry} aware of common {@link DittoRuntimeException}s.
 */
@Immutable
public final class CommonErrorRegistry extends AbstractErrorRegistry<DittoRuntimeException> {

    private CommonErrorRegistry(final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code CommonErrorRegistry}.
     *
     * @return the error registry.
     */
    public static CommonErrorRegistry newInstance() {
        final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies = new HashMap<>();

        parseStrategies.put(JsonParseException.ERROR_CODE, (jsonObject, dittoHeaders) -> new DittoJsonException(
                JsonParseException.newBuilder().message(getMessage(jsonObject)).build(), dittoHeaders));

        parseStrategies.put(JsonMissingFieldException.ERROR_CODE, (jsonObject, dittoHeaders) -> new DittoJsonException(
                JsonMissingFieldException.newBuilder().message(getMessage(jsonObject)).build(), dittoHeaders));

        parseStrategies.put(JsonFieldSelectorInvalidException.ERROR_CODE,
                (jsonObject, dittoHeaders) -> new DittoJsonException(
                        JsonFieldSelectorInvalidException.newBuilder().message(getMessage(jsonObject)).build(),
                        dittoHeaders));

        parseStrategies.put(JsonPointerInvalidException.ERROR_CODE,
                (jsonObject, dittoHeaders) -> new DittoJsonException(
                        JsonPointerInvalidException.newBuilder().message(getMessage(jsonObject)).build(),
                        dittoHeaders));

        // other common exceptions

        parseStrategies.put(DittoHeaderInvalidException.ERROR_CODE, DittoHeaderInvalidException::fromJson);
        parseStrategies.put(CommandNotSupportedException.ERROR_CODE, CommandNotSupportedException::fromJson);
        parseStrategies.put(JsonTypeNotParsableException.ERROR_CODE, JsonTypeNotParsableException::fromJson);
        parseStrategies.put(InvalidRqlExpressionException.ERROR_CODE, InvalidRqlExpressionException::fromJson);

        // Gateway exceptions
        parseStrategies.put(GatewayAuthenticationFailedException.ERROR_CODE,
                GatewayAuthenticationFailedException::fromJson);

        parseStrategies.put(GatewayAuthenticationProviderUnavailableException.ERROR_CODE,
                (jsonObject, dittoHeaders) -> GatewayAuthenticationProviderUnavailableException
                        .fromMessage(getMessage(jsonObject), dittoHeaders));

        parseStrategies.put(GatewayInternalErrorException.ERROR_CODE,
                (jsonObject, dittoHeaders) -> GatewayInternalErrorException
                        .fromMessage(getMessage(jsonObject), dittoHeaders));

        parseStrategies.put(GatewayMethodNotAllowedException.ERROR_CODE,
                (jsonObject, dittoHeaders) -> GatewayMethodNotAllowedException
                        .fromMessage(getMessage(jsonObject), dittoHeaders));

        parseStrategies.put(GatewayQueryTimeExceededException.ERROR_CODE,
                (jsonObject, dittoHeaders) -> GatewayQueryTimeExceededException
                        .fromMessage(getMessage(jsonObject), dittoHeaders));

        parseStrategies.put(GatewayServiceTimeoutException.ERROR_CODE,
                (jsonObject, dittoHeaders) -> GatewayServiceTimeoutException
                        .fromMessage(getMessage(jsonObject), dittoHeaders));

        parseStrategies.put(GatewayServiceUnavailableException.ERROR_CODE,
                (jsonObject, dittoHeaders) -> GatewayServiceUnavailableException
                        .fromMessage(getMessage(jsonObject), dittoHeaders));

        parseStrategies.put(GatewayServiceTooManyRequestsException.ERROR_CODE,
                (jsonObject, dittoHeaders) -> GatewayServiceTooManyRequestsException
                        .fromMessage(getMessage(jsonObject), dittoHeaders));

        parseStrategies.put(GatewayPlaceholderNotResolvableException.ERROR_CODE,
                GatewayPlaceholderNotResolvableException::fromJson);

        return new CommonErrorRegistry(parseStrategies);
    }


    private static String getMessage(final JsonObject jsonObject) {
        return jsonObject.getValue(DittoJsonException.JsonFields.MESSAGE)
                .orElseThrow(() -> JsonMissingFieldException.newBuilder()
                        .fieldName(DittoRuntimeException.JsonFields.MESSAGE.getPointer().toString()).build());
    }

}
