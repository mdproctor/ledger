package io.casehub.ledger.runtime.repository.jpa;

import io.casehub.ledger.test.PostgreSQLTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(JpaSequenceNumberPgIT.Profile.class)
class JpaSequenceNumberPgIT extends JpaSequenceNumberIT {

    public static class Profile extends PostgreSQLTestProfile {
        @Override
        public String getConfigProfile() {
            return "sequence-number-test";
        }
    }
}
