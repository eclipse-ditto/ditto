/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.base.common;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * An enumeration of HTTP status codes.
 *
 * @see <a href="http://tools.ietf.org/html/rfc7231#section-6.1"></a>
 */
@SuppressWarnings("squid:S109")
public enum HttpStatusCode {

    /**
     * The initial part of a request has been received and has not yet been rejected by the server. The server intends
     * to send a final response after the request has been fully received and acted upon.
     */
    CONTINUE(100),

    /**
     * The server understands and is willing to comply with the client's request, via the Upgrade header field, for a
     * change in the application protocol being used on this connection.
     */
    SWITCHING_PROTOCOLS(101),

    /**
     * An interim response used to inform the client that the server has accepted the complete request, but has not yet
     * completed it.
     */
    PROCESSING(102),


    /**
     * The request has succeeded.
     */
    OK(200),

    /**
     * The request has been fulfilled and has resulted in one or more new resources being created.
     */
    CREATED(201),

    /**
     * The request has been accepted for processing, but the processing has not been completed.
     */
    ACCEPTED(202),

    /**
     * The request was successful but the enclosed payload has been modified from that of the origin server's 200 OK
     * response by a transforming proxy.
     */
    NON_AUTHORITATIVE_INFORMATION(203),

    /**
     * The server has successfully fulfilled the request and that there is no additional content to send in the response
     * payload body.
     */
    NO_CONTENT(204),

    /**
     * The server has fulfilled the request and desires that the user agent reset the "document view", which caused the
     * request to be sent, to its original state as received from the origin server.
     */
    RESET_CONTENT(205),

    /**
     * The server is successfully fulfilling a range request for the target resource by transferring one or more parts
     * of the selected representation that correspond to the satisfiable ranges found in the request's Range header
     * field.
     */
    PARTIAL_CONTENT(206),

    /**
     * A Multi-Status response conveys information about multiple resources in situations where multiple status codes
     * might be appropriate.
     */
    MULTI_STATUS(207),

    /**
     * Used inside a DAV: propstat response element to avoid enumerating the internal members of multiple bindings to
     * the same collection repeatedly.
     */
    ALREADY_REPORTED(208),

    /**
     * The server has fulfilled a GET request for the resource, and the response is a representation of the result of
     * one or more instance-manipulations applied to the current instance.
     */
    IMUSED(226),


    /**
     * The target resource has more than one representation, each with its own more specific identifier, and information
     * about the alternatives is being provided so that the user (or user agent) can select a preferred representation
     * by redirecting its request to one or more of those identifiers.
     */
    MULTIPLE_CHOICES(300),

    /**
     * The target resource has been assigned a new permanent URI and any future references to this resource ought to use
     * one of the enclosed URIs.
     */
    MOVED_PERMANENTLY(301),

    /**
     * The target resource resides temporarily under a different URI. Since the redirection might be altered on
     * occasion, the client ought to continue to use the effective request URI for future requests.
     */
    FOUND(302),

    /**
     * The server is redirecting the user agent to a different resource, as indicated by a URI in the Location header
     * field, which is intended to provide an indirect response to the original request.
     */
    SEE_OTHER(303),

    /**
     * A conditional GET or HEAD request has been received and would have resulted in a 200 OK response if it were not
     * for the fact that the condition evaluated to false.
     */
    NOT_MODIFIED(304),

    /**
     * Defined in a previous version of this specification and is now deprecated, due to security concerns regarding
     * in-band configuration of a proxy.
     */
    USE_PROXY(305),

    /**
     * The target resource resides temporarily under a different URI and the user agent MUST NOT change the request
     * method if it performs an automatic redirection to that URI.
     */
    TEMPORARY_REDIRECT(307),

    /**
     * The target resource has been assigned a new permanent URI and any future references to this resource ought to use
     * one of the enclosed URIs.
     */
    PERMANENT_REDIRECT(308),


    /**
     * The server cannot or will not process the request due to something that is perceived to be a client error (e.g.,
     * malformed request syntax, invalid request message framing, or deceptive request routing).
     */
    BAD_REQUEST(400),

    /**
     * The request has not been applied because it lacks valid authentication credentials for the target resource.
     */
    UNAUTHORIZED(401),

    /**
     * Reserved for future use.
     */
    PAYMENT_REQUIRED(402),

    /**
     * The server understood the request but refuses to authorize it.
     */
    FORBIDDEN(403),

    /**
     * The origin server did not find a current representation for the target resource or is not willing to disclose
     * that one exists.
     */
    NOT_FOUND(404),

    /**
     * The method received in the request-line is known by the origin server but not supported by the target resource.
     */
    METHOD_NOT_ALLOWED(405),

    /**
     * The target resource does not have a current representation that would be acceptable to the user agent, according
     * to the proactive negotiation header fields received in the request1, and the server is unwilling to supply a
     * default representation.
     */
    NOT_ACCEPTABLE(406),

    /**
     * Similar to 401 Unauthorized, but it indicates that the client needs to authenticate itself in order to use a
     * proxy.
     */
    PROXY_AUTHENTICATION_REQUIRED(407),

    /**
     * The server did not receive a complete request message within the time that it was prepared to wait.
     */
    REQUEST_TIMEOUT(408),

    /**
     * The request could not be completed due to a conflict with the current state of the target resource. This code is
     * used in situations where the user might be able to resolve the conflict and resubmit the request.
     */
    CONFLICT(409),

    /**
     * The target resource is no longer available at the origin server and that this condition is likely to be
     * permanent.
     */
    GONE(410),

    /**
     * The server refuses to accept the request without a defined Content-Length.
     */
    LENGTH_REQUIRED(411),

    /**
     * One or more conditions given in the request header fields evaluated to false when tested on the server.
     */
    PRECONDITION_FAILED(412),

    /**
     * The server is refusing to process a request because the request payload is larger than the server is willing or
     * able to process.
     */
    REQUEST_ENTITY_TOO_LARGE(413),

    /**
     * The server is refusing to service the request because the request-target is longer than the server is willing to
     * interpret.
     */
    REQUEST_URI_TOO_LONG(414),

    /**
     * The origin server is refusing to service the request because the payload is in a format not supported by this
     * method on the target resource.
     */
    UNSUPPORTED_MEDIA_TYPE(415),

    /**
     * None of the ranges in the request's Range header field overlap the current extent of the selected resource or
     * that the set of ranges requested has been rejected due to invalid ranges or an excessive request of small or
     * overlapping ranges.
     */
    REQUESTED_RANGE_NOT_SATISFIABLE(416),

    /**
     * The expectation given in the request's Expect header field could not be met by at least one of the inbound
     * servers.
     */
    EXPECTATION_FAILED(417),

    /**
     * Any attempt to brew coffee with a teapot should result in the error code "418 I'm a teapot". The resulting entity
     * body MAY be short and stout.
     */
    IM_A_TEAPOT(418),

    /**
     * The request was directed at a server that is not able to produce a response. This can be sent by a server that is
     * not configured to produce responses for the combination of scheme and authority that are included in the request
     * URI.
     */
    MISDIRECTED_REQUEST(421),

    /**
     * The server understands the content type of the request entity (hence a 415 Unsupported Media Type status code is
     * inappropriate), and the syntax of the request entity is correct (thus a 400 Bad Request status code is
     * inappropriate) but was unable to process the contained instructions.
     */
    UNPROCESSABLE_ENTITY(422),

    /**
     * The source or destination resource of a method is locked.
     */
    LOCKED(423),

    /**
     * The method could not be performed on the resource because the requested action depended on another action and
     * that action failed.
     */
    FAILED_DEPENDENCY(424),

    /**
     * The server refuses to perform the request using the current protocol but might be willing to do so after the
     * client upgrades to a different protocol.
     */
    UPGRADE_REQUIRED(426),

    /**
     * The origin server requires the request to be conditional.
     */
    PRECONDITION_REQUIRED(428),

    /**
     * The user has sent too many requests in a given amount of time ("rate limiting").
     */
    TOO_MANY_REQUESTS(429),

    /**
     * The server is unwilling to process the request because its header fields are too large. The request MAY be
     * resubmitted after reducing the size of the request header fields.
     */
    REQUEST_HEADER_FIELDS_TOO_LARGE(431),

    /**
     * The server is denying access to the resource as a consequence of a legal demand.
     */
    UNAVAILABLE_FOR_LEGAL_REASONS(451),


    /**
     * The server encountered an unexpected condition that prevented it from fulfilling the request.
     */
    INTERNAL_SERVER_ERROR(500),

    /**
     * The server does not support the functionality required to fulfill the request.
     */
    NOT_IMPLEMENTED(501),

    /**
     * The server, while acting as a gateway or proxy, received an invalid response from an inbound server it accessed
     * while attempting to fulfill the request.
     */
    BAD_GATEWAY(502),

    /**
     * The server is currently unable to handle the request due to a temporary overload or scheduled maintenance, which
     * will likely be alleviated after some delay.
     */
    SERVICE_UNAVAILABLE(503),

    /**
     * The server, while acting as a gateway or proxy, did not receive a timely response from an upstream server it
     * needed to access in order to complete the request.
     */
    GATEWAY_TIMEOUT(504),

    /**
     * The server does not support, or refuses to support, the major version of HTTP that was used in the request
     * message.
     */
    HTTPVERSION_NOT_SUPPORTED(505),

    /**
     * The server has an internal configuration error: the chosen variant resource is configured to engage in
     * transparent content negotiation itself, and is therefore not a proper end point in the negotiation process.
     */
    VARIANT_ALSO_NEGOTIATES(506),

    /**
     * The method could not be performed on the resource because the server is unable to store the representation needed
     * to successfully complete the request.
     */
    INSUFFICIENT_STORAGE(507),

    /**
     * The server terminated an operation because it encountered an infinite loop while processing a request with
     * "Depth: infinity". This status indicates that the entire operation failed.
     */
    LOOP_DETECTED(508),

    /**
     * The policy for accessing the resource has not been met in the request. The server should send back all the
     * information necessary for the client to issue an extended request.
     */
    NOT_EXTENDED(510),

    /**
     * The client needs to authenticate to gain network access.
     */
    NETWORK_AUTHENTICATION_REQUIRED(511),

    /**
     * This status code is not specified in any RFCs, but is used by some HTTP proxies to signal a network connect
     * timeout behind the proxy to a client in front of the proxy.
     */
    NETWORK_CONNECT_TIMEOUT(599);


    private final int statusCodeValue;

    HttpStatusCode(final int theStatusCodeValue) {
        statusCodeValue = theStatusCodeValue;
    }

    /**
     * Returns a {@code HttpStatusCode} which is associated with the specified integer representation. If no appropriate
     * status code can be found, an empty optional is returned.
     *
     * @param statusCodeAsInt the integer representation of the HTTP status code.
     * @return the HTTP status code which is associated with {@code statusCodeAsInt} or an empty optional.
     */
    public static Optional<HttpStatusCode> forInt(final int statusCodeAsInt) {
        return Stream.of(values()) //
                .filter(c -> c.toInt() == statusCodeAsInt) //
                .findFirst();
    }

    /**
     * Returns the integer value of this status code.
     *
     * @return the integer value of this status code.
     */
    public int toInt() {
        return statusCodeValue;
    }

}
