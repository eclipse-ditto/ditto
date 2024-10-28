package org.eclipse.ditto.policies.api.commands.sudo;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.PolicyId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

import java.util.Map;

/**
 * Unit test for {@link CheckPolicyPermissionsResponse}.
 */
public final class CheckPolicyPermissionsResponseTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CheckPolicyPermissionsResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void testSerialization() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();

        final CheckPolicyPermissionsResponse underTest =
                CheckPolicyPermissionsResponse.of(
                        PolicyId.of("org.eclipse.ditto:some-policy-1"),
                        Map.of("thing:/features/lamp/properties/on", true, "thing:/features/door/locked", false),
                        dittoHeaders
                );

        final CheckPolicyPermissionsResponse deserialized =
                CheckPolicyPermissionsResponse.fromJson(underTest.toJson(), dittoHeaders);

        assertThat(deserialized).isEqualTo(underTest);
    }
}
