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
package org.eclipse.ditto.gateway.service.endpoints;

import static akka.http.javadsl.model.ContentTypes.APPLICATION_JSON;
import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.service.config.http.DefaultHttpProxyConfig;
import org.eclipse.ditto.gateway.service.endpoints.routes.RootRouteExceptionHandler;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationFactory;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthorizationSubjectsProvider;
import org.eclipse.ditto.gateway.service.util.config.endpoints.CloudEventsConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.DefaultClaimMessageConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.DefaultCloudEventsConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.DefaultCommandConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.DefaultMessageConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.DefaultPublicHealthConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.GatewayHttpConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.MessageConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.PublicHealthConfig;
import org.eclipse.ditto.gateway.service.util.config.health.DefaultHealthCheckConfig;
import org.eclipse.ditto.gateway.service.util.config.health.HealthCheckConfig;
import org.eclipse.ditto.gateway.service.util.config.security.AuthenticationConfig;
import org.eclipse.ditto.gateway.service.util.config.security.DefaultAuthenticationConfig;
import org.eclipse.ditto.gateway.service.util.config.streaming.DefaultStreamingConfig;
import org.eclipse.ditto.gateway.service.util.config.streaming.StreamingConfig;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.internal.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.internal.utils.http.DefaultHttpClientFacade;
import org.eclipse.ditto.internal.utils.http.HttpClientFacade;
import org.eclipse.ditto.internal.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.internal.utils.protocol.config.DefaultProtocolConfig;
import org.eclipse.ditto.internal.utils.protocol.config.ProtocolConfig;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRouteResult;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.TestProbe;

/**
 * Abstract base class for Endpoint tests for the gateway.
 */
public abstract class EndpointTestBase extends JUnitRouteTest {

    private static final JsonValue DEFAULT_DUMMY_ENTITY_JSON = JsonValue.of("dummy");

    private static final Function<Jsonifiable<?>, Optional<Object>> DUMMY_THING_MODIFY_RESPONSE_PROVIDER =
            DummyThingModifyCommandResponse::echo;

    protected static HttpConfig httpConfig;
    protected static HealthCheckConfig healthCheckConfig;
    protected static CommandConfig commandConfig;
    protected static MessageConfig messageConfig;
    protected static MessageConfig claimMessageConfig;
    protected static AuthenticationConfig authConfig;
    protected static CacheConfig cacheConfig;
    protected static StreamingConfig streamingConfig;
    protected static PublicHealthConfig publicHealthConfig;
    protected static ProtocolConfig protocolConfig;
    protected static CloudEventsConfig cloudEventsConfig;
    protected static JwtAuthenticationFactory jwtAuthenticationFactory;
    protected static HttpClientFacade httpClientFacade;
    protected static JwtAuthorizationSubjectsProvider authorizationSubjectsProvider;

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    protected TestProbe connectivityShardRegionProxyActor;
    protected HeaderTranslator httpHeaderTranslator;
    protected RouteBaseProperties routeBaseProperties;
    protected DittoHeaders dittoHeaders;

    @BeforeClass
    public static void initTestFixture() {
        final var dittoScopedConfig = DefaultScopedConfig.dittoScoped(createTestConfig());
        final var gatewayScopedConfig = DefaultScopedConfig.newInstance(dittoScopedConfig, "gateway");
        final var actorSystem = ActorSystem.create(EndpointTestBase.class.getSimpleName(), createTestConfig());
        httpConfig = GatewayHttpConfig.of(gatewayScopedConfig);
        healthCheckConfig = DefaultHealthCheckConfig.of(gatewayScopedConfig);
        commandConfig = DefaultCommandConfig.of(gatewayScopedConfig);
        messageConfig = DefaultMessageConfig.of(gatewayScopedConfig);
        claimMessageConfig = DefaultClaimMessageConfig.of(gatewayScopedConfig);
        authConfig = DefaultAuthenticationConfig.of(gatewayScopedConfig);
        cacheConfig = DefaultCacheConfig.of(gatewayScopedConfig, "cache.publickeys");
        streamingConfig = DefaultStreamingConfig.of(gatewayScopedConfig);
        publicHealthConfig = DefaultPublicHealthConfig.of(gatewayScopedConfig);
        protocolConfig = DefaultProtocolConfig.of(dittoScopedConfig);
        cloudEventsConfig = DefaultCloudEventsConfig.of(gatewayScopedConfig);
        httpClientFacade =
                DefaultHttpClientFacade.getInstance(actorSystem,
                        DefaultHttpProxyConfig.ofProxy(DefaultScopedConfig.empty("/")));
        authorizationSubjectsProvider = JwtAuthorizationSubjectsProvider.get(actorSystem, ConfigFactory.empty());
        jwtAuthenticationFactory = JwtAuthenticationFactory.newInstance(authConfig.getOAuthConfig(),
                cacheConfig,
                httpClientFacade,
                actorSystem);
    }

    @Before
    public void before() {
        connectivityShardRegionProxyActor = new TestProbe(system());

        final var adapterProvider = ProtocolAdapterProvider.load(protocolConfig, system());
        httpHeaderTranslator = adapterProvider.getHttpHeaderTranslator();

        routeBaseProperties = RouteBaseProperties.newBuilder()
                .proxyActor(createDummyResponseActor(getResponseProvider()))
                .actorSystem(system())
                .httpConfig(httpConfig)
                .commandConfig(commandConfig)
                .headerTranslator(httpHeaderTranslator)
                .build();
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
    }
    protected Function<Jsonifiable<?>, Optional<Object>> getResponseProvider(){
        return DUMMY_THING_MODIFY_RESPONSE_PROVIDER;
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
     * Creates a actor which creates a dummy response message as response to all received messages.
     *
     * @return the actor
     */
    protected ActorRef createDummyResponseActor() {
        return system().actorOf(DummyResponseAnswer.props(DUMMY_THING_MODIFY_RESPONSE_PROVIDER));
    }

    /**
     * Creates a actor which creates a dummy response message as response to all received messages.
     *
     * @return the actor
     */
    protected ActorRef createDummyResponseActor(final Function<Jsonifiable<?>, Optional<Object>> responseProvider) {
        return system().actorOf(DummyResponseAnswer.props(responseProvider));
    }

    protected <T extends Command<?>> ActorRef startEchoActor(
            final Class<? extends T> commandClass,
            final Function<T, ? extends CommandResponse<?>> responseFactory
    ) {
        final var actorSystem = system();
        return actorSystem.actorOf(EchoActor.props(commandClass, responseFactory));
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

    protected HttpRequest withStatusCredentials(final HttpRequest httpRequest) {
        return httpRequest.addCredentials(EndpointTestConstants.STATUS_CREDENTIALS);
    }

    protected Route handleExceptions(final Supplier<Route> inner) {
        return handleExceptions(
                RootRouteExceptionHandler.getInstance(exception ->
                        HttpResponse.create().withStatus(exception.getHttpStatus().getCode())
                                .withEntity(HttpEntities.create(APPLICATION_JSON, exception.toJsonString()))
                ),
                inner);
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

        private static Props props(final Function<Jsonifiable<?>, Optional<Object>> responseProvider) {
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

    protected static final class DummyThingModifyCommandResponse
            extends AbstractCommandResponse<DummyThingModifyCommandResponse>
            implements ThingCommandResponse<DummyThingModifyCommandResponse>, WithOptionalEntity {

        private JsonValue dummyEntity = DEFAULT_DUMMY_ENTITY_JSON;

        private DummyThingModifyCommandResponse(final String responseType,
                final HttpStatus httpStatus,
                final DittoHeaders dittoHeaders,
                @Nullable final JsonValue dummyEntity) {

            super(responseType, httpStatus, dittoHeaders);
            if (null != dummyEntity) {
                this.dummyEntity = dummyEntity;
            }
        }

         public static Optional<Object> echo(@Nullable final Object m) {
            final DittoHeaders dittoHeaders;
            if (m instanceof WithDittoHeaders) {
                dittoHeaders = ((WithDittoHeaders) m).getDittoHeaders();
            } else {
                dittoHeaders = DittoHeaders.empty();
            }
            final DummyThingModifyCommandResponse response =
                    new DummyThingModifyCommandResponse("testonly.response.type",
                            HttpStatus.tryGetInstance(EndpointTestConstants.DUMMY_COMMAND_SUCCESS.intValue())
                                    .orElse(HttpStatus.INTERNAL_SERVER_ERROR),
                            dittoHeaders, m instanceof Jsonifiable ? ((Jsonifiable<?>) m).toJson() : null);
            return Optional.of(response);
        }

        @Override
        protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
                final Predicate<JsonField> predicate) {
            // do nothing
        }

        @Override
        public DummyThingModifyCommandResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
            return new DummyThingModifyCommandResponse(getType(), getHttpStatus(), dittoHeaders, dummyEntity);
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
        public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
            return Optional.of(dummyEntity);
        }

        @Override
        public ThingId getEntityId() {
            return EndpointTestConstants.KNOWN_THING_ID;
        }

    }

    private static final class EchoActor<T> extends AbstractActor {

        private final Class<T> handledMessageType;
        private final Function<T, ?> responseFactory;

        private EchoActor(final Class<T> handledMessageType, final Function<T, ?> responseFactory) {
            this.handledMessageType = handledMessageType;
            this.responseFactory = responseFactory;
        }

        private static <T> Props props(final Class<? extends T> handledMessageType,
                final Function<? extends T, ?> responseFunction) {

            return Props.create(EchoActor.class, handledMessageType, responseFunction);
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(handledMessageType, message -> {
                        final var sender = getSender();
                        sender.tell(responseFactory.apply(message), getSelf());
                    })
                    .build();
        }

    }

}
