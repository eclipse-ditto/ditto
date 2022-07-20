package org.eclipse.ditto.connectivity.service.messaging.hono;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.HonoAddressAlias;
import org.eclipse.ditto.connectivity.model.ImmutableHeaderMapping;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.config.DefaultHonoConfig;
import org.eclipse.ditto.connectivity.service.config.HonoConfig;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import akka.actor.ActorSystem;

public abstract class HonoConnectionFactory {
    protected final ActorSystem actorSystem;
    protected final Connection connection;
    protected final HonoConfig honoConfig;

    protected HonoConnectionFactory(ActorSystem actorSystem, Connection connection) {
        this.actorSystem = actorSystem;
        this.connection = connection;
        this.honoConfig = new DefaultHonoConfig(actorSystem);
    }

    protected abstract UserPasswordCredentials getCredentials();
    protected abstract String getTenantId();

    public Connection enrichConnection() {
        final var connectionId = connection.getId();
        return ConnectivityModelFactory.newConnectionBuilder(connection.getId(),
                        connection.getConnectionType(),
                        connection.getConnectionStatus(),
                        honoConfig.getBaseUri().toString())
                .validateCertificate(honoConfig.isValidateCertificates())
                .specificConfig(Map.of(
                        "saslMechanism", honoConfig.getSaslMechanism().toString(),
                        "bootstrapServers", getBootstrapServerUrisAsCommaSeparatedListString(honoConfig),
                        "groupId", (getTenantId().isEmpty() ? "" : getTenantId() + "_") + connectionId)
                )
                .credentials(getCredentials())
                .sources(connection.getSources()
                        .stream()
                        .map(source -> ConnectivityModelFactory.sourceFromJson(
                                resolveSourceAliases(source, getTenantId()), 1))
                        .toList())
                .targets(connection.getTargets()
                        .stream()
                        .map(target -> ConnectivityModelFactory.targetFromJson(resolveTargetAlias(target, getTenantId())))
                        .toList())
                .build();
    }

    private static String getBootstrapServerUrisAsCommaSeparatedListString(final HonoConfig honoConfig) {
        return honoConfig.getBootstrapServerUris()
                .stream()
                .map(URI::toString)
                .collect(Collectors.joining(","));
    }

    private static JsonObject resolveSourceAliases(final Source source, final String tenantId) {
        final var sourceBuilder = JsonFactory.newObjectBuilder(source.toJson())
                .set(Source.JsonFields.ADDRESSES, JsonArray.of(source.getAddresses().stream()
                        .map(address -> HonoAddressAlias.resolve(address, tenantId))
                        .map(JsonValue::of)
                        .toList()));
        source.getReplyTarget().ifPresent(replyTarget -> {
            var headerMapping = replyTarget.getHeaderMapping().toJson()
                    .setValue("correlation-id", "{{ header:correlation-id }}");
            if (HonoAddressAlias.COMMAND.getName().equals(replyTarget.getAddress())) {
                headerMapping = headerMapping
                        .setValue("device_id", "{{ thing:id }}")
                        .setValue("subject",
                                "{{ header:subject | fn:default(topic:action-subject) | fn:default(topic:criterion) }}-response");
            }
            sourceBuilder.set("replyTarget", replyTarget.toBuilder()
                    .address(HonoAddressAlias.resolve(replyTarget.getAddress(), tenantId, true))
                    .headerMapping(ImmutableHeaderMapping.fromJson(headerMapping))
                    .build().toJson());
        });
        if (source.getAddresses().contains(HonoAddressAlias.COMMAND_RESPONSE.getName())) {
            sourceBuilder.set("headerMapping", source.getHeaderMapping().toJson()
                    .setValue("correlation-id", "{{ header:correlation-id }}")
                    .setValue("status", "{{ header:status }}"));
        }
        return sourceBuilder.build();
    }

    private static JsonObject resolveTargetAlias(final Target target, final String tenantId) {
        final var targetBuilder = JsonFactory.newObjectBuilder(target.toJson())
                .set(Target.JsonFields.ADDRESS, Optional.of(target.getAddress())
                        .map(address -> HonoAddressAlias.resolve(address, tenantId, true))
                        .orElse(null), jsonField -> !jsonField.getValue().asString().isEmpty());
        var headerMapping = target.getHeaderMapping().toJson()
                .setValue("device_id", "{{ thing:id }}")
                .setValue("correlation-id", "{{ header:correlation-id }}")
                .setValue("subject", "{{ header:subject | fn:default(topic:action-subject) }}");
        if (target.getTopics().stream()
                .anyMatch(topic -> topic.getTopic() == Topic.LIVE_MESSAGES ||
                        topic.getTopic() == Topic.LIVE_COMMANDS)) {
            headerMapping = headerMapping.setValue("response-required", "{{ header:response-required }}");
        }
        targetBuilder.set("headerMapping", headerMapping);
        return targetBuilder.build();
    }

}
