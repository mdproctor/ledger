package io.casehub.ledger.service;

import io.casehub.ledger.test.PostgreSQLTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(LedgerHealthJobPgIT.Profile.class)
class LedgerHealthJobPgIT extends LedgerHealthJobIT {

    public static class Profile extends PostgreSQLTestProfile {
        @Override
        public String getConfigProfile() {
            return "health-test";
        }
    }
}
