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
package org.eclipse.ditto.wot.model;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;

/**
 * Abstract implementation of {@link FormElement}.
 */
abstract class AbstractFormElement<F extends FormElement<F>>
        extends AbstractTypedJsonObject<F>
        implements FormElement<F> {

    private static final String CONTENT_TYPE_DEFAULT = "application/json";

    protected AbstractFormElement(final JsonObject wrappedObject) {
        super(wrappedObject);
    }

    @Override
    public IRI getHref() {
        return IRI.of(wrappedObject.getValueOrThrow(JsonFields.HREF));
    }

    @Override
    public String getContentType() {
        return wrappedObject.getValue(JsonFields.CONTENT_TYPE).orElse(CONTENT_TYPE_DEFAULT);
    }

    @Override
    public Optional<String> getContentCoding() {
        return wrappedObject.getValue(JsonFields.CONTENT_CODING);
    }

    @Override
    public Optional<FormElementSubprotocol> getSubprotocol() {
        return wrappedObject.getValue(JsonFields.SUBPROTOCOL)
                .map(FormElementSubprotocol::of);
    }

    @Override
    public Optional<Security> getSecurity() {
        return Optional.ofNullable(
                TdHelpers.getValueIgnoringWrongType(wrappedObject, JsonFields.SECURITY_MULTIPLE)
                        .map(MultipleSecurity::fromJson)
                        .map(Security.class::cast)
                        .orElseGet(() -> wrappedObject.getValue(JsonFields.SECURITY)
                                .map(SingleSecurity::of)
                                .orElse(null)
                        )
        );
    }

    @Override
    public Optional<OAuth2Scopes> getScopes() {
        return Optional.ofNullable(
                TdHelpers.getValueIgnoringWrongType(wrappedObject, JsonFields.SCOPES_MULTIPLE)
                        .map(MultipleOAuth2Scopes::fromJson)
                        .map(OAuth2Scopes.class::cast)
                        .orElseGet(() -> wrappedObject.getValue(JsonFields.SCOPES)
                                .map(SingleOAuth2Scopes::of)
                                .orElse(null)
                        )
        );
    }

    @Override
    public Optional<FormElementExpectedResponse> getExpectedResponse() {
        return wrappedObject.getValue(JsonFields.RESPONSE)
                .map(FormElementExpectedResponse::fromJson);
    }

    @Override
    public Optional<FormElementAdditionalResponses> getAdditionalResponses() {
        return wrappedObject.getValue(JsonFields.ADDITIONAL_RESPONSES)
                .map(FormElementAdditionalResponses::fromJson);
    }

    @Override
    public JsonObject toJson() {
        return wrappedObject;
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractFormElement;
    }

}
