/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpResponse;
import akka.util.ByteString;

/**
 * This class creates a {@link HttpResponse} from a {@link DittoRuntimeException}.
 */
@Immutable
final class DittoRuntimeExceptionToHttpResponse implements Function<DittoRuntimeException, HttpResponse> {

    private final HeaderTranslator headerTranslator;

    private DittoRuntimeExceptionToHttpResponse(final HeaderTranslator headerTranslator) {
        this.headerTranslator = headerTranslator;
    }

    /**
     * Returns an instance of {@code DittoRuntimeExceptionToHttpResponse}.
     *
     * @param headerTranslator is used to translate the DittoHeaders of the exception to external headers.
     * @return the instance.
     * @throws NullPointerException if {@code headerTranslator} is {@code null}.
     */
    public static DittoRuntimeExceptionToHttpResponse getInstance(final HeaderTranslator headerTranslator) {
        return new DittoRuntimeExceptionToHttpResponse(checkNotNull(headerTranslator, "headerTranslator"));
    }

    @Override
    public HttpResponse apply(final DittoRuntimeException exception) {
        checkNotNull(exception, "exception");
        return HttpResponse.create()
                .withStatus(exception.getHttpStatus().getCode())
                .withHeaders(getExternalHeadersFor(exception.getDittoHeaders()))
                .withEntity(ContentTypes.APPLICATION_JSON, ByteString.fromString(exception.toJsonString()));
    }

    private List<HttpHeader> getExternalHeadersFor(final DittoHeaders dittoHeaders) {
        final Map<String, String> externalHeaders = headerTranslator.toExternalAndRetainKnownHeaders(dittoHeaders);
        final List<HttpHeader> result = new ArrayList<>(externalHeaders.size());
        externalHeaders.forEach((key, value) -> result.add(HttpHeader.parse(key, value)));
        return result;
    }

}
