package com.spendsense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate"
})
class SchemaValidationSmokeTest {

    @Test
    @DisplayName("Application context initializes and Hibernate validates all JPA entities against live MySQL schema with zero errors")
    void contextLoadsAndSchemaValidates() {
        // Successful context startup confirms complete agreement between Flyway schema and JPA mappings
    }
}
