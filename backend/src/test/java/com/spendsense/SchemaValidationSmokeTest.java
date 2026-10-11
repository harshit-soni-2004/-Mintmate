package com.spendsense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/spendsense?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8",
        "spring.datasource.username=root",
        "spring.datasource.password=Harshit#$04",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class SchemaValidationSmokeTest {

    @Test
    @DisplayName("Application context initializes and Hibernate validates all JPA entities against live MySQL schema with zero errors")
    void contextLoadsAndSchemaValidates() {
        // Successful context startup confirms complete agreement between Flyway schema and JPA mappings
    }
}
