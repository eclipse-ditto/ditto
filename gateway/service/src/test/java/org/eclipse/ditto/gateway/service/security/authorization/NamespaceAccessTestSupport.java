/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.security.authorization;

import java.util.List;
import java.util.Base64;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.util.config.security.DefaultNamespaceAccessConfig;
import org.eclipse.ditto.gateway.service.util.config.security.NamespaceAccessConfig;
import org.eclipse.ditto.jwt.model.ImmutableJsonWebToken;
import org.eclipse.ditto.jwt.model.JsonWebToken;

import com.typesafe.config.ConfigFactory;

final class NamespaceAccessTestSupport {

    private NamespaceAccessTestSupport() {
        throw new AssertionError();
    }

    static DittoHeaders newHeaders() {
        return DittoHeaders.newBuilder()
                .authorizationContext(AuthorizationContext.newInstance(
                        DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance("test:subject")))
                .build();
    }

    static DittoHeaders newHeadersWithHeader(final String key, final String value) {
        return newHeaders().toBuilder().putHeader(key, value).build();
    }

    static NamespaceAccessConfig config(final List<String> conditions,
            final List<String> allowedNamespaces,
            final List<String> blockedNamespaces) {
        final String configString = String.format(
                "{ conditions = [%s], allowed-namespaces = [%s], blocked-namespaces = [%s] }",
                toConfigArray(conditions),
                toConfigArray(allowedNamespaces),
                toConfigArray(blockedNamespaces)
        );
        return DefaultNamespaceAccessConfig.of(ConfigFactory.parseString(configString));
    }

    static JsonWebToken jwt(final String body) {
        final String header = "{\"alg\":\"none\"}";
        final String signature = "sig";
        final String token = base64UrlEncode(header) + "." + base64UrlEncode(body) + "." + base64UrlEncode(signature);
        return ImmutableJsonWebToken.fromToken(token);
    }

    private static String toConfigArray(final List<String> items) {
        return items.stream()
                .map(item -> "\"" + item + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private static String base64UrlEncode(final String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes());
    }
}
