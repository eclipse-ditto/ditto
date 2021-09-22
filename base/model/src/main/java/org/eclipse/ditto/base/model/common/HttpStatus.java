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
package org.eclipse.ditto.base.model.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Represents a HTTP status. This class provides constants of itself for
 * <a href="https://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml">all assigned HTTP status codes.</a>
 * Additionally it allows to create instances for unassigned HTTP status codes as long as they are within the valid
 * bounds.
 *
 * @since 2.0.0
 */
@Immutable
public final class HttpStatus {

    private static final Map<Integer, HttpStatus> ASSIGNED = new HashMap<>();

    /**
     * The initial part of a request has been received and has not yet been rejected by the server. The server intends
     * to send a final response after the request has been fully received and acted upon.
     */
    public static final HttpStatus CONTINUE = assign(100);

    /**
     * The server understands and is willing to comply with the client's request, via the Upgrade header field, for a
     * change in the application protocol being used on this connection.
     */
    public static final HttpStatus SWITCHING_PROTOCOLS = assign(101);

    /**
     * An interim response used to inform the client that the server has accepted the complete request, but has not yet
     * completed it.
     */
    public static final HttpStatus PROCESSING = assign(102);

    /**
     * Indicates to the client that the server is likely to send a final response with the header fields included in the
     * informational response.
     */
    public static final HttpStatus EARLY_HINTS = assign(103);

    /**
     * The request has succeeded.
     */
    public static final HttpStatus OK = assign(200);

    /**
     * The request has been fulfilled and has resulted in one or more new resources being created.
     */
    public static final HttpStatus CREATED = assign(201);

    /**
     * The request has been accepted for processing, but the processing has not been completed.
     */
    public static final HttpStatus ACCEPTED = assign(202);

    /**
     * The request was successful but the enclosed payload has been modified from that of the origin server's 200 OK
     * response by a transforming proxy.
     */
    public static final HttpStatus NON_AUTHORITATIVE_INFORMATION = assign(203);

    /**
     * The server has successfully fulfilled the request and that there is no additional content to send in the response
     * payload body.
     */
    public static final HttpStatus NO_CONTENT = assign(204);

    /**
     * The server has fulfilled the request and desires that the user agent reset the "document view", which caused the
     * request to be sent, to its original state as received from the origin server.
     */
    public static final HttpStatus RESET_CONTENT = assign(205);

    /**
     * The server is successfully fulfilling a range request for the target resource by transferring one or more parts
     * of the selected representation that correspond to the satisfiable ranges found in the request's Range header
     * field.
     */
    public static final HttpStatus PARTIAL_CONTENT = assign(206);

    /**
     * A Multi-Status response conveys information about multiple resources in situations where multiple status codes
     * might be appropriate.
     */
    public static final HttpStatus MULTI_STATUS = assign(207);

    /**
     * Used inside a DAV: propstat response element to avoid enumerating the internal members of multiple bindings to
     * the same collection repeatedly.
     */
    public static final HttpStatus ALREADY_REPORTED = assign(208);

    /**
     * The server has fulfilled a GET request for the resource, and the response is a representation of the result of
     * one or more instance-manipulations applied to the current instance.
     */
    public static final HttpStatus IM_USED = assign(226);

    /**
     * The target resource has more than one representation, each with its own more specific identifier, and information
     * about the alternatives is being provided so that the user (or user agent) can select a preferred representation
     * by redirecting its request to one or more of those identifiers.
     */
    public static final HttpStatus MULTIPLE_CHOICES = assign(300);

    /**
     * The target resource has been assigned a new permanent URI and any future references to this resource ought to use
     * one of the enclosed URIs.
     */
    public static final HttpStatus MOVED_PERMANENTLY = assign(301);

    /**
     * The target resource resides temporarily under a different URI. Since the redirection might be altered on
     * occasion, the client ought to continue to use the effective request URI for future requests.
     */
    public static final HttpStatus FOUND = assign(302);

    /**
     * The server is redirecting the user agent to a different resource, as indicated by a URI in the Location header
     * field, which is intended to provide an indirect response to the original request.
     */
    public static final HttpStatus SEE_OTHER = assign(303);

    /**
     * A conditional GET or HEAD request has been received and would have resulted in a 200 OK response if it were not
     * for the fact that the condition evaluated to false.
     */
    public static final HttpStatus NOT_MODIFIED = assign(304);

    /**
     * Defined in a previous version of this specification and is now deprecated, due to security concerns regarding
     * in-band configuration of a proxy.
     */
    public static final HttpStatus USE_PROXY = assign(305);

    /**
     * The target resource resides temporarily under a different URI and the user agent MUST NOT change the request
     * method if it performs an automatic redirection to that URI.
     */
    public static final HttpStatus TEMPORARY_REDIRECT = assign(307);

    /**
     * The target resource has been assigned a new permanent URI and any future references to this resource ought to use
     * one of the enclosed URIs.
     */
    public static final HttpStatus PERMANENT_REDIRECT = assign(308);

    /**
     * The server cannot or will not process the request due to something that is perceived to be a client error (e.g.,
     * malformed request syntax, invalid request message framing, or deceptive request routing).
     */
    public static final HttpStatus BAD_REQUEST = assign(400);

    /**
     * The request has not been applied because it lacks valid authentication credentials for the target resource.
     */
    public static final HttpStatus UNAUTHORIZED = assign(401);

    /**
     * Reserved for future use.
     */
    public static final HttpStatus PAYMENT_REQUIRED = assign(402);

    /**
     * The server understood the request but refuses to authorize it.
     */
    public static final HttpStatus FORBIDDEN = assign(403);

    /**
     * The origin server did not find a current representation for the target resource or is not willing to disclose
     * that one exists.
     */
    public static final HttpStatus NOT_FOUND = assign(404);

    /**
     * The method received in the request-line is known by the origin server but not supported by the target resource.
     */
    public static final HttpStatus METHOD_NOT_ALLOWED = assign(405);

    /**
     * The target resource does not have a current representation that would be acceptable to the user agent, according
     * to the proactive negotiation header fields received in the request1, and the server is unwilling to supply a
     * default representation.
     */
    public static final HttpStatus NOT_ACCEPTABLE = assign(406);

    /**
     * Similar to 401 Unauthorized, but it indicates that the client needs to authenticate itself in order to use a
     * proxy.
     */
    public static final HttpStatus PROXY_AUTHENTICATION_REQUIRED = assign(407);

    /**
     * The server did not receive a complete request message within the time that it was prepared to wait.
     */
    public static final HttpStatus REQUEST_TIMEOUT = assign(408);

    /**
     * The request could not be completed due to a conflict with the current state of the target resource. This code is
     * used in situations where the user might be able to resolve the conflict and resubmit the request.
     */
    public static final HttpStatus CONFLICT = assign(409);

    /**
     * The target resource is no longer available at the origin server and that this condition is likely to be
     * permanent.
     */
    public static final HttpStatus GONE = assign(410);

    /**
     * The server refuses to accept the request without a defined Content-Length.
     */
    public static final HttpStatus LENGTH_REQUIRED = assign(411);

    /**
     * One or more conditions given in the request header fields evaluated to false when tested on the server.
     */
    public static final HttpStatus PRECONDITION_FAILED = assign(412);

    /**
     * The server is refusing to process a request because the request payload is larger than the server is willing or
     * able to process.
     */
    public static final HttpStatus REQUEST_ENTITY_TOO_LARGE = assign(413);

    /**
     * The server is refusing to service the request because the request-target is longer than the server is willing to
     * interpret.
     */
    public static final HttpStatus REQUEST_URI_TOO_LONG = assign(414);

    /**
     * The origin server is refusing to service the request because the payload is in a format not supported by this
     * method on the target resource.
     */
    public static final HttpStatus UNSUPPORTED_MEDIA_TYPE = assign(415);

    /**
     * None of the ranges in the request's Range header field overlap the current extent of the selected resource or
     * that the set of ranges requested has been rejected due to invalid ranges or an excessive request of small or
     * overlapping ranges.
     */
    public static final HttpStatus REQUESTED_RANGE_NOT_SATISFIABLE = assign(416);

    /**
     * The expectation given in the request's Expect header field could not be met by at least one of the inbound
     * servers.
     */
    public static final HttpStatus EXPECTATION_FAILED = assign(417);

    /**
     * Any attempt to brew coffee with a teapot should result in the error code "418 I'm a teapot". The resulting entity
     * body MAY be short and stout.
     */
    public static final HttpStatus IM_A_TEAPOT = assign(418);

    /**
     * The request was directed at a server that is not able to produce a response. This can be sent by a server that is
     * not configured to produce responses for the combination of scheme and authority that are included in the request
     * URI.
     */
    public static final HttpStatus MISDIRECTED_REQUEST = assign(421);

    /**
     * The server understands the content type of the request entity (hence a 415 Unsupported Media Type status code is
     * inappropriate), and the syntax of the request entity is correct (thus a 400 Bad Request status code is
     * inappropriate) but was unable to process the contained instructions.
     */
    public static final HttpStatus UNPROCESSABLE_ENTITY = assign(422);

    /**
     * The source or destination resource of a method is locked.
     */
    public static final HttpStatus LOCKED = assign(423);

    /**
     * The method could not be performed on the resource because the requested action depended on another action and
     * that action failed.
     */
    public static final HttpStatus FAILED_DEPENDENCY = assign(424);

    /**
     * Indicates that the server is unwilling to risk processing a request that might be replayed.
     * <p>
     * User agents that send a request in early data are expected to retry the request when receiving a 425 (Too Early)
     * response status code. A user agent SHOULD retry automatically, but any retries MUST NOT be sent in early data.
     * </p>
     */
    public static final HttpStatus TOO_EARLY = assign(425);

    /**
     * The server refuses to perform the request using the current protocol but might be willing to do so after the
     * client upgrades to a different protocol.
     */
    public static final HttpStatus UPGRADE_REQUIRED = assign(426);

    /**
     * The origin server requires the request to be conditional.
     */
    public static final HttpStatus PRECONDITION_REQUIRED = assign(428);

    /**
     * The user has sent too many requests in a given amount of time ("rate limiting").
     */
    public static final HttpStatus TOO_MANY_REQUESTS = assign(429);

    /**
     * The server is unwilling to process the request because its header fields are too large. The request MAY be
     * resubmitted after reducing the size of the request header fields.
     */
    public static final HttpStatus REQUEST_HEADER_FIELDS_TOO_LARGE = assign(431);

    /**
     * The server is denying access to the resource as a consequence of a legal demand.
     */
    public static final HttpStatus UNAVAILABLE_FOR_LEGAL_REASONS = assign(451);

    /**
     * The server encountered an unexpected condition that prevented it from fulfilling the request.
     */
    public static final HttpStatus INTERNAL_SERVER_ERROR = assign(500);

    /**
     * The server does not support the functionality required to fulfill the request.
     */
    public static final HttpStatus NOT_IMPLEMENTED = assign(501);

    /**
     * The server, while acting as a gateway or proxy, received an invalid response from an inbound server it accessed
     * while attempting to fulfill the request.
     *
     * @deprecated Do not use in Ditto for exceptions as Ditto reserves 502 as code when gracefully shutting down HTTP.
     */
    @Deprecated(/*forRemoval = false*/) // not to be deleted, just marked as "do not use"
    public static final HttpStatus BAD_GATEWAY = assign(502);

    /**
     * The server is currently unable to handle the request due to a temporary overload or scheduled maintenance, which
     * will likely be alleviated after some delay.
     */
    public static final HttpStatus SERVICE_UNAVAILABLE = assign(503);

    /**
     * The server, while acting as a gateway or proxy, did not receive a timely response from an upstream server it
     * needed to access in order to complete the request.
     */
    public static final HttpStatus GATEWAY_TIMEOUT = assign(504);

    /**
     * The server does not support, or refuses to support, the major version of HTTP that was used in the request
     * message.
     */
    public static final HttpStatus HTTPVERSION_NOT_SUPPORTED = assign(505);

    /**
     * The server has an internal configuration error: the chosen variant resource is configured to engage in
     * transparent content negotiation itself, and is therefore not a proper end point in the negotiation process.
     */
    public static final HttpStatus VARIANT_ALSO_NEGOTIATES = assign(506);

    /**
     * The method could not be performed on the resource because the server is unable to store the representation needed
     * to successfully complete the request.
     */
    public static final HttpStatus INSUFFICIENT_STORAGE = assign(507);

    /**
     * The server terminated an operation because it encountered an infinite loop while processing a request with
     * "Depth: infinity". This status indicates that the entire operation failed.
     */
    public static final HttpStatus LOOP_DETECTED = assign(508);

    /**
     * The policy for accessing the resource has not been met in the request. The server should send back all the
     * information necessary for the client to issue an extended request.
     */
    public static final HttpStatus NOT_EXTENDED = assign(510);

    /**
     * The client needs to authenticate to gain network access.
     */
    public static final HttpStatus NETWORK_AUTHENTICATION_REQUIRED = assign(511);

    /**
     * This status code is not specified in any RFCs, but is used by some HTTP proxies to signal a network connect
     * timeout behind the proxy to a client in front of the proxy.
     */
    public static final HttpStatus NETWORK_CONNECT_TIMEOUT = assign(599);

    private final int code;
    private final Category category;

    private HttpStatus(final int code, final Category category) {
        this.code = code;
        this.category = category;
    }

    /**
     * Tries to get an instance of HttpStatus with the specified status code. If the provided status code is invalid,
     * this method returns an empty Optional instead of throwing an exception.
     *
     * @param code the code of the HttpStatus to get an instance for.
     * @return an Optional that either contains the HttpStatus with the specified code or is empty if the code is not
     * within the range of valid HTTP status codes.
     * @see #getInstance(int)
     */
    public static Optional<HttpStatus> tryGetInstance(final int code) {
        HttpStatus httpStatus;
        try {
            httpStatus = getInstance(code);
        } catch (final HttpStatusCodeOutOfRangeException e) {
            httpStatus = null;
        }
        return Optional.ofNullable(httpStatus);
    }

    /**
     * Gets an instance of HttpStatus with the specified status code or throws an exception if the specified code is not
     * within the range of valid HTTP status codes.
     *
     * @param code the code of the returned HttpStatus.
     * @return the HttpStatus.
     * @throws org.eclipse.ditto.base.model.common.HttpStatusCodeOutOfRangeException if {@code code} is not within the range of valid HTTP status codes.
     * @see #tryGetInstance(int)
     */
    public static HttpStatus getInstance(final int code) throws HttpStatusCodeOutOfRangeException {
        HttpStatus result = ASSIGNED.get(code);
        if (null == result) {
            result = newInstance(code);
        }
        return result;
    }

    private static HttpStatus newInstance(final int code) throws HttpStatusCodeOutOfRangeException {
        return new HttpStatus(code, Category.of(code).orElseThrow(() -> new HttpStatusCodeOutOfRangeException(code)));
    }

    private static HttpStatus assign(final int code) {
        final HttpStatus result = tryToCreateInstance(code);
        ASSIGNED.put(code, result);
        return result;
    }

    private static HttpStatus tryToCreateInstance(final int code) {
        try {
            return newInstance(code);
        } catch (final HttpStatusCodeOutOfRangeException e) {

            // This should never be the case for assigned status codes!
            throw new IllegalStateException(e);
        }
    }

    public int getCode() {
        return code;
    }

    public boolean isInformational() {
        return Category.INFORMATIONAL == category;
    }

    public boolean isSuccess() {
        return Category.SUCCESS == category;
    }

    public boolean isRedirection() {
        return Category.REDIRECTION == category;
    }

    public boolean isClientError() {
        return Category.CLIENT_ERROR == category;
    }

    public boolean isServerError() {
        return Category.SERVER_ERROR == category;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HttpStatus that = (HttpStatus) o;
        return code == that.code && category == that.category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, category);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "code=" + code +
                ", category=" + category +
                "]";
    }

    enum Category {
        INFORMATIONAL,
        SUCCESS,
        REDIRECTION,
        CLIENT_ERROR,
        SERVER_ERROR;

        private static final byte ORDINAL_TO_STATUS_CODE_OFFSET = 100;
        private static final byte UPPER_BOUND_OFFSET = 99;

        static Optional<Category> of(final int code) {
            for (final Category category : values()) {
                final int ordinal = category.ordinal();
                final int lowerBound = (ordinal + 1) * ORDINAL_TO_STATUS_CODE_OFFSET;
                final int upperBound = lowerBound + UPPER_BOUND_OFFSET;
                if (lowerBound <= code && code <= upperBound) {
                    return Optional.of(category);
                }
            }
            return Optional.empty();
        }
    }

}
