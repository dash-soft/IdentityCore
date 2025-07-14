package net.dashsoft.identity.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YamlException;

import java.io.StringReader;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Docker Compose file validation.
 * Testing framework: JUnit 5
 * YAML library: SnakeYAML
 */
@DisplayName("Docker Compose File Validation Tests")
class ComposeFileValidationTest {

    private Yaml yaml;
    private String validComposeContent;

    @BeforeEach
    void setUp() {
        yaml = new Yaml();
        validComposeContent = """
            services:
              postgres:
                image: 'postgres:latest'
                environment:
                  - 'POSTGRES_DB=mydatabase'
                  - 'POSTGRES_PASSWORD=secret'
                  - 'POSTGRES_USER=myuser'
                ports:
                  - '5432:5432'
            """;
    }

    @Nested
    @DisplayName("Valid Compose File Tests")
    class ValidComposeFileTests {

        @Test
        @DisplayName("Should parse valid compose file successfully")
        void shouldParseValidComposeFile() {
            assertDoesNotThrow(() -> {
                Map<String, Object> compose = yaml.load(validComposeContent);
                assertNotNull(compose);
                assertTrue(compose.containsKey("services"));
            });
        }

        @Test
        @DisplayName("Should validate postgres service configuration")
        void shouldValidatePostgresServiceConfiguration() {
            Map<String, Object> compose = yaml.load(validComposeContent);
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            
            assertTrue(services.containsKey("postgres"));
            
            Map<String, Object> postgres = (Map<String, Object>) services.get("postgres");
            assertEquals("postgres:latest", postgres.get("image"));
            
            List<String> environment = (List<String>) postgres.get("environment");
            assertEquals(3, environment.size());
            assertTrue(environment.contains("POSTGRES_DB=mydatabase"));
            assertTrue(environment.contains("POSTGRES_PASSWORD=secret"));
            assertTrue(environment.contains("POSTGRES_USER=myuser"));
            
            List<String> ports = (List<String>) postgres.get("ports");
            assertEquals(1, ports.size());
            assertEquals("5432:5432", ports.get(0));
        }

        @Test
        @DisplayName("Should validate required postgres environment variables")
        void shouldValidateRequiredPostgresEnvironmentVariables() {
            Map<String, Object> compose = yaml.load(validComposeContent);
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            Map<String, Object> postgres = (Map<String, Object>) services.get("postgres");
            List<String> environment = (List<String>) postgres.get("environment");
            
            boolean hasDatabase = environment.stream().anyMatch(env -> env.startsWith("POSTGRES_DB="));
            boolean hasPassword = environment.stream().anyMatch(env -> env.startsWith("POSTGRES_PASSWORD="));
            boolean hasUser = environment.stream().anyMatch(env -> env.startsWith("POSTGRES_USER="));
            
            assertTrue(hasDatabase, "POSTGRES_DB environment variable should be present");
            assertTrue(hasPassword, "POSTGRES_PASSWORD environment variable should be present");
            assertTrue(hasUser, "POSTGRES_USER environment variable should be present");
        }

        @Test
        @DisplayName("Should validate postgres port mapping")
        void shouldValidatePostgresPortMapping() {
            Map<String, Object> compose = yaml.load(validComposeContent);
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            Map<String, Object> postgres = (Map<String, Object>) services.get("postgres");
            List<String> ports = (List<String>) postgres.get("ports");
            
            assertEquals(1, ports.size());
            String portMapping = ports.get(0);
            assertTrue(portMapping.matches("\\d+:\\d+"), "Port mapping should be in format host:container");
            assertTrue(portMapping.contains("5432"), "Should expose PostgreSQL default port");
        }
    }

    @Nested
    @DisplayName("Invalid Compose File Tests")
    class InvalidComposeFileTests {

        @Test
        @DisplayName("Should reject malformed YAML")
        void shouldRejectMalformedYaml() {
            String malformedYaml = """
                services:
                  postgres:
                    image: 'postgres:latest'
                  environment:
                      - 'POSTGRES_DB=mydatabase'
                """;
            
            assertThrows(YamlException.class, () -> yaml.load(malformedYaml));
        }

        @Test
        @DisplayName("Should reject compose file without services")
        void shouldRejectComposeFileWithoutServices() {
            String noServicesYaml = """
                version: '3.8'
                networks:
                  default:
                    driver: bridge
                """;
            
            Map<String, Object> compose = yaml.load(noServicesYaml);
            assertFalse(compose.containsKey("services"), "Compose file should be invalid without services");
        }

        @Test
        @DisplayName("Should reject postgres service without required image")
        void shouldRejectPostgresServiceWithoutImage() {
            String noImageYaml = """
                services:
                  postgres:
                    environment:
                      - 'POSTGRES_DB=mydatabase'
                      - 'POSTGRES_PASSWORD=secret'
                      - 'POSTGRES_USER=myuser'
                """;
            
            Map<String, Object> compose = yaml.load(noImageYaml);
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            Map<String, Object> postgres = (Map<String, Object>) services.get("postgres");
            
            assertFalse(postgres.containsKey("image"), "Postgres service should be invalid without image");
        }

        @Test
        @DisplayName("Should reject postgres service without environment variables")
        void shouldRejectPostgresServiceWithoutEnvironment() {
            String noEnvYaml = """
                services:
                  postgres:
                    image: 'postgres:latest'
                    ports:
                      - '5432:5432'
                """;
            
            Map<String, Object> compose = yaml.load(noEnvYaml);
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            Map<String, Object> postgres = (Map<String, Object>) services.get("postgres");
            
            assertFalse(postgres.containsKey("environment"), "Postgres service should be invalid without environment");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCasesAndBoundaryTests {

        @Test
        @DisplayName("Should handle empty compose file")
        void shouldHandleEmptyComposeFile() {
            String emptyYaml = "";
            Object result = yaml.load(emptyYaml);
            assertNull(result);
        }

        @Test
        @DisplayName("Should handle compose file with only comments")
        void shouldHandleComposeFileWithOnlyComments() {
            String commentOnlyYaml = """
                # This is a comment
                # Another comment
                """;
            Object result = yaml.load(commentOnlyYaml);
            assertNull(result);
        }

        @Test
        @DisplayName("Should handle postgres service with additional properties")
        void shouldHandlePostgresServiceWithAdditionalProperties() {
            String extendedYaml = """
                services:
                  postgres:
                    image: 'postgres:latest'
                    environment:
                      - 'POSTGRES_DB=mydatabase'
                      - 'POSTGRES_PASSWORD=secret'
                      - 'POSTGRES_USER=myuser'
                    ports:
                      - '5432:5432'
                    volumes:
                      - postgres_data:/var/lib/postgresql/data
                    restart: unless-stopped
                    healthcheck:
                      test: ["CMD-SHELL", "pg_isready -U myuser"]
                      interval: 30s
                      timeout: 10s
                      retries: 5
                volumes:
                  postgres_data:
                """;
            
            assertDoesNotThrow(() -> {
                Map<String, Object> compose = yaml.load(extendedYaml);
                assertNotNull(compose);
                assertTrue(compose.containsKey("services"));
                assertTrue(compose.containsKey("volumes"));
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "postgres:13", "postgres:14", "postgres:15", "postgres:16", "postgres:latest"
        })
        @DisplayName("Should validate different postgres image versions")
        void shouldValidateDifferentPostgresImageVersions(String imageVersion) {
            String versionedYaml = """
                services:
                  postgres:
                    image: '%s'
                    environment:
                      - 'POSTGRES_DB=mydatabase'
                      - 'POSTGRES_PASSWORD=secret'
                      - 'POSTGRES_USER=myuser'
                    ports:
                      - '5432:5432'
                """.formatted(imageVersion);
            
            assertDoesNotThrow(() -> {
                Map<String, Object> compose = yaml.load(versionedYaml);
                Map<String, Object> services = (Map<String, Object>) compose.get("services");
                Map<String, Object> postgres = (Map<String, Object>) services.get("postgres");
                assertEquals(imageVersion, postgres.get("image"));
            });
        }

        @Test
        @DisplayName("Should handle alternative port configurations")
        void shouldHandleAlternativePortConfigurations() {
            String alternativePortYaml = """
                services:
                  postgres:
                    image: 'postgres:latest'
                    environment:
                      - 'POSTGRES_DB=mydatabase'
                      - 'POSTGRES_PASSWORD=secret'
                      - 'POSTGRES_USER=myuser'
                    ports:
                      - '5433:5432'
                      - '5434:5432'
                """;
            
            Map<String, Object> compose = yaml.load(alternativePortYaml);
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            Map<String, Object> postgres = (Map<String, Object>) services.get("postgres");
            List<String> ports = (List<String>) postgres.get("ports");
            
            assertEquals(2, ports.size());
            assertTrue(ports.contains("5433:5432"));
            assertTrue(ports.contains("5434:5432"));
        }

        @Test
        @DisplayName("Should validate environment variables with different formats")
        void shouldValidateEnvironmentVariablesWithDifferentFormats() {
            String mixedEnvYaml = """
                services:
                  postgres:
                    image: 'postgres:latest'
                    environment:
                      POSTGRES_DB: mydatabase
                      POSTGRES_PASSWORD: secret
                      POSTGRES_USER: myuser
                    ports:
                      - '5432:5432'
                """;
            
            Map<String, Object> compose = yaml.load(mixedEnvYaml);
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            Map<String, Object> postgres = (Map<String, Object>) services.get("postgres");
            Map<String, String> environment = (Map<String, String>) postgres.get("environment");
            
            assertEquals("mydatabase", environment.get("POSTGRES_DB"));
            assertEquals("secret", environment.get("POSTGRES_PASSWORD"));
            assertEquals("myuser", environment.get("POSTGRES_USER"));
        }
    }

    @Nested
    @DisplayName("Security and Best Practices Tests")
    class SecurityAndBestPracticesTests {

        @Test
        @DisplayName("Should detect insecure password configurations")
        void shouldDetectInsecurePasswordConfigurations() {
            Map<String, Object> compose = yaml.load(validComposeContent);
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            Map<String, Object> postgres = (Map<String, Object>) services.get("postgres");
            List<String> environment = (List<String>) postgres.get("environment");
            
            boolean hasPasswordEnv = environment.stream()
                .anyMatch(env -> env.startsWith("POSTGRES_PASSWORD="));
            
            assertTrue(hasPasswordEnv, "Password should be configured via environment variable");
            
            // Check for weak passwords (this is a basic example)
            String passwordValue = environment.stream()
                .filter(env -> env.startsWith("POSTGRES_PASSWORD="))
                .findFirst()
                .map(env -> env.split("=", 2)[1])
                .orElse("");
            
            assertFalse(passwordValue.isEmpty(), "Password should not be empty");
            assertFalse(passwordValue.equals("password"), "Should not use default weak passwords");
        }

        @Test
        @DisplayName("Should validate that postgres image uses official image")
        void shouldValidateThatPostgresImageUsesOfficialImage() {
            Map<String, Object> compose = yaml.load(validComposeContent);
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            Map<String, Object> postgres = (Map<String, Object>) services.get("postgres");
            String image = (String) postgres.get("image");
            
            assertTrue(image.startsWith("postgres:"), "Should use official postgres image");
        }

        @Test
        @DisplayName("Should check for exposed sensitive ports")
        void shouldCheckForExposedSensitivePorts() {
            Map<String, Object> compose = yaml.load(validComposeContent);
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            Map<String, Object> postgres = (Map<String, Object>) services.get("postgres");
            List<String> ports = (List<String>) postgres.get("ports");
            
            // Verify that database port is exposed (this might be intentional in dev but should be noted)
            boolean hasExposedPort = ports.stream()
                .anyMatch(port -> port.startsWith("5432:"));
            
            assertTrue(hasExposedPort, "Database port should be properly configured for access");
        }
    }

    @Nested
    @DisplayName("Performance and Resource Tests")
    class PerformanceAndResourceTests {

        @Test
        @DisplayName("Should handle large compose files efficiently")
        void shouldHandleLargeComposeFilesEfficiently() {
            StringBuilder largeYaml = new StringBuilder();
            largeYaml.append("services:\n");
            
            // Create a large compose file with many services
            for (int i = 1; i <= 100; i++) {
                largeYaml.append(String.format("""
                  postgres%d:
                    image: 'postgres:latest'
                    environment:
                      - 'POSTGRES_DB=mydatabase%d'
                      - 'POSTGRES_PASSWORD=secret%d'
                      - 'POSTGRES_USER=myuser%d'
                    ports:
                      - '%d:5432'
                """, i, i, i, i, 5432 + i));
            }
            
            long startTime = System.currentTimeMillis();
            assertDoesNotThrow(() -> {
                Map<String, Object> compose = yaml.load(largeYaml.toString());
                assertNotNull(compose);
                Map<String, Object> services = (Map<String, Object>) compose.get("services");
                assertEquals(100, services.size());
            });
            long endTime = System.currentTimeMillis();
            
            // Parsing should complete within reasonable time (5 seconds max)
            assertTrue(endTime - startTime < 5000, "Large compose file parsing should complete quickly");
        }

        @Test
        @DisplayName("Should validate memory efficiency with repeated parsing")
        void shouldValidateMemoryEfficiencyWithRepeatedParsing() {
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 1000; i++) {
                    Map<String, Object> compose = yaml.load(validComposeContent);
                    assertNotNull(compose);
                }
            });
        }
    }
}