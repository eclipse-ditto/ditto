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

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Abstract implementation of {@link FormElement.Builder}.
 */
abstract class AbstractFormElementBuilder<
        B extends FormElement.Builder<B, E>,
        E extends FormElement<E>
        >
        extends AbstractTypedJsonObjectBuilder<B, E>
        implements FormElement.Builder<B, E> {

    protected AbstractFormElementBuilder(final JsonObjectBuilder wrappedObjectBuilder, final Class<?> selfType) {
        super(wrappedObjectBuilder, selfType);
    }

    @Override
    public B setHref(@Nullable final IRI href) {
        if (href != null) {
            putValue(FormElement.JsonFields.HREF, href.toString());
        } else {
            remove(FormElement.JsonFields.HREF);
        }
        return myself;
    }

    @Override
    public B setContentType(@Nullable final String contentType) {
        putValue(FormElement.JsonFields.CONTENT_TYPE, contentType);
        return myself;
    }

    @Override
    public B setContentCoding(@Nullable final String contentCoding) {
        putValue(FormElement.JsonFields.CONTENT_CODING, contentCoding);
        return myself;
    }

    @Override
    public B setSubprotocol(@Nullable final String subprotocol) {
        putValue(FormElement.JsonFields.SUBPROTOCOL, subprotocol);
        return myself;
    }

    @Override
    public B setSecurity(@Nullable final Security security) {
        if (security != null) {
            if (security instanceof MultipleSecurity) {
                putValue(FormElement.JsonFields.SECURITY_MULTIPLE, ((MultipleSecurity) security).toJson());
            } else if (security instanceof SingleSecurity) {
                putValue(FormElement.JsonFields.SECURITY, security.toString());
            } else {
                throw new IllegalArgumentException("Unsupported security: " + security.getClass().getSimpleName());
            }
        } else {
            remove(FormElement.JsonFields.SECURITY);
        }
        return myself;
    }

    @Override
    public B setScopes(@Nullable final OAuth2Scopes scopes) {
        if (scopes != null) {
            if (scopes instanceof MultipleOAuth2Scopes) {
                putValue(FormElement.JsonFields.SCOPES_MULTIPLE, ((MultipleSecurity) scopes).toJson());
            } else if (scopes instanceof SingleOAuth2Scopes) {
                putValue(FormElement.JsonFields.SCOPES, scopes.toString());
            } else {
                throw new IllegalArgumentException("Unsupported scopes: " + scopes.getClass().getSimpleName());
            }
        } else {
            remove(FormElement.JsonFields.SCOPES);
        }
        return myself;
    }

    @Override
    public B setExpectedResponse(@Nullable final FormElementExpectedResponse expectedResponse) {
        if (expectedResponse != null) {
            putValue(FormElement.JsonFields.RESPONSE, expectedResponse.toJson());
        } else {
            remove(FormElement.JsonFields.RESPONSE);
        }
        return myself;
    }

    @Override
    public B setAdditionalResponses(@Nullable final FormElementAdditionalResponses additionalResponses) {
        if (additionalResponses != null) {
            putValue(FormElement.JsonFields.ADDITIONAL_RESPONSES, additionalResponses.toJson());
        } else {
            remove(FormElement.JsonFields.ADDITIONAL_RESPONSES);
        }
        return myself;
    }

}
