/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.actors;

import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;

import akka.NotUsed;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.Attributes;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

/**
 * Transforms a {@link Source} of {@link JsonValue}s into a {@link HttpResponse}.
 * The response's entity is a chunked stream of newline delimited JSON values (NDJSON).
 *
 * @see <a href="https://github.com/ndjson/ndjson-spec">NDJSON</a>
 */
@Immutable
final class JsonValueSourceToHttpResponse implements Function<Source<JsonValue, NotUsed>, HttpResponse> {

    /**
     * The content type of the HttpResponse this function returns.
     */
    static final ContentType CONTENT_TYPE_NDJSON = ContentTypes.parse("application/x-ndjson");

    private JsonValueSourceToHttpResponse() {
        super();
    }

    /**
     * Returns an instance of {@code JsonValueSourceToHttpResponse}.
     *
     * @return the instance.
     */
    static JsonValueSourceToHttpResponse getInstance() {
        return new JsonValueSourceToHttpResponse();
    }

    @Override
    public HttpResponse apply(final Source<JsonValue, NotUsed> source) {
        ConditionChecker.checkNotNull(source, "source");
        return HttpResponse.create()
                .withEntity(getChunkedHttpEntity(getRenderedCompactJsonArraySource(source)))
                .withStatus(HttpStatus.OK.getCode());
    }

    private static Source<ByteString, NotUsed> getRenderedCompactJsonArraySource(final Source<JsonValue, NotUsed> source) {
        return source.map(JsonValue::toString)
                .via(intersperseWithNewlineDelimiter())
                .map(ByteString::fromString)
                .withAttributes(Attributes.logLevels(Attributes.logLevelDebug(), Attributes.logLevelDebug(),
                        Attributes.logLevelError()))
                .log(JsonValueSourceToHttpResponse.class.getSimpleName());
    }

    private static Flow<String, String, NotUsed> intersperseWithNewlineDelimiter() {
        return Flow.of(String.class).intersperse("\n");
    }

    private static HttpEntity.Chunked getChunkedHttpEntity(final Source<ByteString, NotUsed> source) {
        return HttpEntities.createChunked(CONTENT_TYPE_NDJSON, source);
    }

}
