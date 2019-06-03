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
package org.eclipse.ditto.services.gateway.endpoints;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.gateway.endpoints.config.AuthenticationConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.DefaultAuthenticationConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.DefaultClaimMessageConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.DefaultMessageConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.DefaultPublicHealthConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.DefaultWebSocketConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.GatewayHttpConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.MessageConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.PublicHealthConfig;
import org.eclipse.ditto.services.gateway.endpoints.config.WebSocketConfig;
import org.eclipse.ditto.services.gateway.health.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.services.gateway.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.cache.config.CacheConfig;
import org.eclipse.ditto.services.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.health.StatusInfo;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.services.utils.protocol.config.DefaultProtocolConfig;
import org.eclipse.ditto.services.utils.protocol.config.ProtocolConfig;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponse;
import org.eclipse.ditto.signals.commands.base.WithEntity;
import org.junit.BeforeClass;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRouteResult;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;

/**
 * Abstract base class for Endpoint tests for the gateway.
 */
public abstract class EndpointTestBase extends JUnitRouteTest {

    public static final JsonValue DEFAULT_DUMMY_ENTITY_JSON = JsonValue.of("dummy");
    public static final String DEFAULT_DUMMY_ENTITY = DEFAULT_DUMMY_ENTITY_JSON.toString();

    private static final Function<Object, Optional<Object>> DUMMY_RESPONSE_PROVIDER = m -> Optional.of(new
            DummyCommandResponse("bumlux",
            HttpStatusCode.forInt(EndpointTestConstants.DUMMY_COMMAND_SUCCESS.intValue())
                    .orElse(HttpStatusCode.INTERNAL_SERVER_ERROR),
            DittoHeaders.empty()));

    protected static HttpConfig httpConfig;
    protected static HealthCheckConfig healthCheckConfig;
    protected static MessageConfig messageConfig;
    protected static MessageConfig claimMessageConfig;
    protected static AuthenticationConfig authConfig;
    protected static CacheConfig cacheConfig;
    protected static WebSocketConfig webSocketConfig;
    protected static PublicHealthConfig publicHealthConfig;
    protected static ProtocolConfig protocolConfig;

    @BeforeClass
    public static void initTestFixture() {
        final DefaultScopedConfig dittoScopedConfig = DefaultScopedConfig.dittoScoped(createTestConfig());
        final DefaultScopedConfig gatewayScopedConfig = DefaultScopedConfig.newInstance(dittoScopedConfig, "gateway");
        httpConfig = GatewayHttpConfig.of(gatewayScopedConfig);
        healthCheckConfig = DefaultHealthCheckConfig.of(gatewayScopedConfig);
        messageConfig = DefaultMessageConfig.of(gatewayScopedConfig);
        claimMessageConfig = DefaultClaimMessageConfig.of(gatewayScopedConfig);
        authConfig = DefaultAuthenticationConfig.of(gatewayScopedConfig);
        cacheConfig = DefaultCacheConfig.of(gatewayScopedConfig, "cache.publickeys");
        webSocketConfig = DefaultWebSocketConfig.of(gatewayScopedConfig);
        publicHealthConfig = DefaultPublicHealthConfig.of(gatewayScopedConfig);
        protocolConfig = DefaultProtocolConfig.of(dittoScopedConfig);
    }

    @Override
    public Config additionalConfig() {
        return createTestConfig();
    }

    /**
     * Constructs a test config object.
     *
     * @return a Config object
     */
    protected static Config createTestConfig() {
        return ConfigFactory.load("test.conf");
    }

    /**
     * Returns the config used for testing.
     *
     * @return the config
     */
    protected Config getConfig() {
        return systemResource().config();
    }


    protected ActorMaterializer actorMaterializer() {
        // materializer is always of type ActorMaterializer (for akka-http-testkit_${scala.version}-10.0.4)
        return (ActorMaterializer) materializer();
    }

    /**
     * Creates a actor which creates a dummy response message as response to all received messages.
     *
     * @return the actor
     */
    protected ActorRef createDummyResponseActor() {
        return system().actorOf(DummyResponseAnswer.props(DUMMY_RESPONSE_PROVIDER));
    }

    /**
     * Creates a actor which creates a dummy response message as response to all received messages.
     *
     * @return the actor
     */
    protected ActorRef createDummyResponseActor(final Function<Object, Optional<Object>> responseProvider) {
        return system().actorOf(DummyResponseAnswer.props(responseProvider));
    }

    protected Supplier<ClusterStatus> createClusterStatusSupplierMock() {
        return () -> ClusterStatus.of(Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet(), "leader", Collections.emptySet(), Collections.emptySet());
    }

    protected ActorRef createHealthCheckingActorMock() {
        return createDummyResponseActor(message -> Optional.of(StatusInfo.fromStatus(StatusInfo.Status.UP)));
    }

    protected HttpRequest withDevopsCredentials(final HttpRequest httpRequest) {
        return httpRequest.addCredentials(EndpointTestConstants.DEVOPS_CREDENTIALS);
    }

    protected String entityToString(final HttpEntity entity) {
        try {
            final int timeoutMillis = 10_000;
            return entity.toStrict(timeoutMillis, materializer())
                    .toCompletableFuture()
                    .get(timeoutMillis, TimeUnit.MILLISECONDS)
                    .getData()
                    .utf8String();
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static void assertWebsocketUpgradeExpectedResult(final TestRouteResult result) {
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
        result.assertEntity("Expected WebSocket Upgrade request");
    }

    private static final class DummyResponseAnswer extends AbstractActor {

        private final Function<Object, Optional<Object>> responseProvider;

        private DummyResponseAnswer(final Function<Object, Optional<Object>> responseProvider) {
            this.responseProvider = requireNonNull(responseProvider);
        }

        private static Props props(final Function<Object, Optional<Object>> responseProvider) {
            return Props.create(DummyResponseAnswer.class, responseProvider);
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .matchAny(o -> responseProvider.apply(o)
                            .ifPresent(response -> getSender().tell(response, getSelf())))
                    .build();
        }
    }

    private static final class DummyCommandResponse extends AbstractCommandResponse<DummyCommandResponse>
            implements WithEntity<DummyCommandResponse> {

        private JsonValue dummyEntity = DEFAULT_DUMMY_ENTITY_JSON;

        private DummyCommandResponse(final String responseType, final HttpStatusCode statusCode,
                final DittoHeaders dittoHeaders) {

            super(responseType, statusCode, dittoHeaders);
        }

        @Override
        protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
                final Predicate<JsonField> predicate) {
            // do nothing
        }

        @Override
        public DummyCommandResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
            return new DummyCommandResponse(getType(), getStatusCode(), dittoHeaders);
        }

        @Override
        public JsonPointer getResourcePath() {
            return null;
        }

        @Override
        public String getResourceType() {
            return null;
        }

        @Override
        public DummyCommandResponse setEntity(final JsonValue entity) {
            this.dummyEntity = entity;
            return new DummyCommandResponse(getType(), getStatusCode(), getDittoHeaders());
        }

        @Override
        public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
            return dummyEntity;
        }

        @Override
        public String getId() {
            return null;
        }

    }

}
