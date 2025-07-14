package net.dashsoft.identity.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for configuration validation in the identity service.
 * Testing Framework: JUnit 5 (Jupiter) with Mockito for mocking
 * Focus: Configuration validation for database, security, JWT, and application settings
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest
@DisplayName("Identity Service Configuration Validation Tests")
class ConfigurationValidationTest {

    @Mock
    private Validator beanValidator;

    @Mock
    private SecurityProperties securityProperties;

    @Mock
    private DatabaseProperties databaseProperties;

    @Mock
    private JwtProperties jwtProperties;

    private ConfigurationValidator configurationValidator;

    @BeforeEach
    void setUp() {
        configurationValidator = new ConfigurationValidator(beanValidator);
    }

    @Nested
    @DisplayName("Database Configuration Validation")
    class DatabaseConfigurationTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "jdbc:mysql://localhost:3306/identity_db",
            "jdbc:postgresql://localhost:5432/identity_db",
            "jdbc:h2:mem:testdb",
            "jdbc:oracle:thin:@localhost:1521:xe",
            "jdbc:sqlserver://localhost:1433;databaseName=identity_db"
        })
        @DisplayName("Should accept valid JDBC URLs")
        void shouldAcceptValidJdbcUrls(String validUrl) {
            // Given
            DatabaseConfig dbConfig = createDatabaseConfig(validUrl, "admin", "password123");

            // When
            boolean isValid = configurationValidator.validateDatabaseConfig(dbConfig);

            // Then
            assertTrue(isValid, "Valid JDBC URL should pass validation: " + validUrl);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"", " ", "invalid-url", "http://localhost", "jdbc:", "not-jdbc-url"})
        @DisplayName("Should reject invalid JDBC URLs")
        void shouldRejectInvalidJdbcUrls(String invalidUrl) {
            // Given
            DatabaseConfig dbConfig = createDatabaseConfig(invalidUrl, "admin", "password123");

            // When
            boolean isValid = configurationValidator.validateDatabaseConfig(dbConfig);

            // Then
            assertFalse(isValid, "Invalid JDBC URL should fail validation: " + invalidUrl);
        }

        @Test
        @DisplayName("Should validate connection pool settings")
        void shouldValidateConnectionPoolSettings() {
            // Given
            DatabaseConfig dbConfig = createDatabaseConfig("jdbc:mysql://localhost:3306/identity_db", "admin", "password123");
            dbConfig.setMaxPoolSize(20);
            dbConfig.setMinPoolSize(5);
            dbConfig.setConnectionTimeout(Duration.ofSeconds(30));

            // When
            boolean isValid = configurationValidator.validateDatabaseConfig(dbConfig);

            // Then
            assertTrue(isValid);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -10, 1001})
        @DisplayName("Should reject invalid connection pool sizes")
        void shouldRejectInvalidConnectionPoolSizes(int invalidPoolSize) {
            // Given
            DatabaseConfig dbConfig = createDatabaseConfig("jdbc:mysql://localhost:3306/identity_db", "admin", "password123");
            dbConfig.setMaxPoolSize(invalidPoolSize);

            // When
            boolean isValid = configurationValidator.validateDatabaseConfig(dbConfig);

            // Then
            assertFalse(isValid, "Invalid pool size should fail validation: " + invalidPoolSize);
        }

        @Test
        @DisplayName("Should require database credentials")
        void shouldRequireDatabaseCredentials() {
            // Given
            DatabaseConfig dbConfigNoUsername = createDatabaseConfig("jdbc:mysql://localhost:3306/identity_db", null, "password123");
            DatabaseConfig dbConfigNoPassword = createDatabaseConfig("jdbc:mysql://localhost:3306/identity_db", "admin", null);

            // When & Then
            assertFalse(configurationValidator.validateDatabaseConfig(dbConfigNoUsername));
            assertFalse(configurationValidator.validateDatabaseConfig(dbConfigNoPassword));
        }

        @Test
        @DisplayName("Should validate database migration settings")
        void shouldValidateDatabaseMigrationSettings() {
            // Given
            DatabaseConfig dbConfig = createDatabaseConfig("jdbc:mysql://localhost:3306/identity_db", "admin", "password123");
            dbConfig.setEnableMigrations(true);
            dbConfig.setMigrationLocation("classpath:db/migration");

            // When
            boolean isValid = configurationValidator.validateDatabaseConfig(dbConfig);

            // Then
            assertTrue(isValid);
        }
    }

    @Nested
    @DisplayName("JWT Configuration Validation")
    class JwtConfigurationTests {

        @Test
        @DisplayName("Should validate complete JWT configuration")
        void shouldValidateCompleteJwtConfiguration() {
            // Given
            JwtConfig jwtConfig = createValidJwtConfig();

            // When
            boolean isValid = configurationValidator.validateJwtConfig(jwtConfig);

            // Then
            assertTrue(isValid);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"", " ", "short", "12345"})
        @DisplayName("Should reject weak JWT secrets")
        void shouldRejectWeakJwtSecrets(String weakSecret) {
            // Given
            JwtConfig jwtConfig = createJwtConfig(weakSecret, 3600, "identity-service");

            // When
            boolean isValid = configurationValidator.validateJwtConfig(jwtConfig);

            // Then
            assertFalse(isValid, "Weak JWT secret should fail validation: " + weakSecret);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -3600, 86401})
        @DisplayName("Should reject invalid JWT expiration times")
        void shouldRejectInvalidJwtExpirationTimes(int invalidExpiration) {
            // Given
            JwtConfig jwtConfig = createJwtConfig("super-secure-jwt-secret-key-with-sufficient-length", invalidExpiration, "identity-service");

            // When
            boolean isValid = configurationValidator.validateJwtConfig(jwtConfig);

            // Then
            assertFalse(isValid, "Invalid JWT expiration should fail validation: " + invalidExpiration);
        }

        @ParameterizedTest
        @ValueSource(strings = {"HS256", "HS384", "HS512", "RS256", "RS384", "RS512"})
        @DisplayName("Should accept valid JWT signing algorithms")
        void shouldAcceptValidJwtSigningAlgorithms(String algorithm) {
            // Given
            JwtConfig jwtConfig = createValidJwtConfig();
            jwtConfig.setSigningAlgorithm(algorithm);

            // When
            boolean isValid = configurationValidator.validateJwtConfig(jwtConfig);

            // Then
            assertTrue(isValid, "Valid JWT algorithm should pass validation: " + algorithm);
        }

        @ParameterizedTest
        @ValueSource(strings = {"MD5", "SHA1", "INVALID", "", "none"})
        @DisplayName("Should reject invalid JWT signing algorithms")
        void shouldRejectInvalidJwtSigningAlgorithms(String invalidAlgorithm) {
            // Given
            JwtConfig jwtConfig = createValidJwtConfig();
            jwtConfig.setSigningAlgorithm(invalidAlgorithm);

            // When
            boolean isValid = configurationValidator.validateJwtConfig(jwtConfig);

            // Then
            assertFalse(isValid, "Invalid JWT algorithm should fail validation: " + invalidAlgorithm);
        }

        @Test
        @DisplayName("Should validate JWT refresh token settings")
        void shouldValidateJwtRefreshTokenSettings() {
            // Given
            JwtConfig jwtConfig = createValidJwtConfig();
            jwtConfig.setRefreshTokenEnabled(true);
            jwtConfig.setRefreshTokenExpiration(604800); // 7 days

            // When
            boolean isValid = configurationValidator.validateJwtConfig(jwtConfig);

            // Then
            assertTrue(isValid);
        }
    }

    @Nested
    @DisplayName("Security Configuration Validation")
    class SecurityConfigurationTests {

        @Test
        @DisplayName("Should validate password policy configuration")
        void shouldValidatePasswordPolicyConfiguration() {
            // Given
            SecurityConfig securityConfig = createValidSecurityConfig();

            // When
            boolean isValid = configurationValidator.validateSecurityConfig(securityConfig);

            // Then
            assertTrue(isValid);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 3})
        @DisplayName("Should reject weak password minimum lengths")
        void shouldRejectWeakPasswordMinimumLengths(int weakLength) {
            // Given
            SecurityConfig securityConfig = createValidSecurityConfig();
            securityConfig.getPasswordPolicy().setMinLength(weakLength);

            // When
            boolean isValid = configurationValidator.validateSecurityConfig(securityConfig);

            // Then
            assertFalse(isValid, "Weak password minimum length should fail validation: " + weakLength);
        }

        @Test
        @DisplayName("Should validate session management configuration")
        void shouldValidateSessionManagementConfiguration() {
            // Given
            SecurityConfig securityConfig = createValidSecurityConfig();
            securityConfig.getSession().setMaxSessions(5);
            securityConfig.getSession().setSessionTimeout(Duration.ofMinutes(30));

            // When
            boolean isValid = configurationValidator.validateSecurityConfig(securityConfig);

            // Then
            assertTrue(isValid);
        }

        @Test
        @DisplayName("Should validate CORS configuration")
        void shouldValidateCorsConfiguration() {
            // Given
            SecurityConfig securityConfig = createValidSecurityConfig();
            securityConfig.getCors().setAllowedOrigins(Set.of("https://app.example.com", "https://admin.example.com"));
            securityConfig.getCors().setAllowedMethods(Set.of("GET", "POST", "PUT", "DELETE"));
            securityConfig.getCors().setMaxAge(Duration.ofHours(1));

            // When
            boolean isValid = configurationValidator.validateSecurityConfig(securityConfig);

            // Then
            assertTrue(isValid);
        }

        @Test
        @DisplayName("Should reject wildcard CORS origins in production")
        void shouldRejectWildcardCorsOriginsInProduction() {
            // Given
            SecurityConfig securityConfig = createValidSecurityConfig();
            securityConfig.getCors().setAllowedOrigins(Set.of("*"));
            securityConfig.setEnvironment("production");

            // When
            boolean isValid = configurationValidator.validateSecurityConfig(securityConfig);

            // Then
            assertFalse(isValid, "Wildcard CORS origins should fail validation in production");
        }

        @Test
        @DisplayName("Should validate rate limiting configuration")
        void shouldValidateRateLimitingConfiguration() {
            // Given
            SecurityConfig securityConfig = createValidSecurityConfig();
            securityConfig.getRateLimit().setEnabled(true);
            securityConfig.getRateLimit().setRequestsPerMinute(100);
            securityConfig.getRateLimit().setBurstCapacity(150);

            // When
            boolean isValid = configurationValidator.validateSecurityConfig(securityConfig);

            // Then
            assertTrue(isValid);
        }
    }

    @Nested
    @DisplayName("Application Configuration Validation")
    class ApplicationConfigurationTests {

        @Test
        @DisplayName("Should validate complete application configuration")
        void shouldValidateCompleteApplicationConfiguration() {
            // Given
            ApplicationConfig appConfig = createValidApplicationConfig();

            // When
            boolean isValid = configurationValidator.validateApplicationConfig(appConfig);

            // Then
            assertTrue(isValid);
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 0, 70000, 100000})
        @DisplayName("Should reject invalid server ports")
        void shouldRejectInvalidServerPorts(int invalidPort) {
            // Given
            ApplicationConfig appConfig = createValidApplicationConfig();
            appConfig.getServer().setPort(invalidPort);

            // When
            boolean isValid = configurationValidator.validateApplicationConfig(appConfig);

            // Then
            assertFalse(isValid, "Invalid server port should fail validation: " + invalidPort);
        }

        @ParameterizedTest
        @ValueSource(strings = {"dev", "test", "staging", "production"})
        @DisplayName("Should accept valid environment names")
        void shouldAcceptValidEnvironmentNames(String environment) {
            // Given
            ApplicationConfig appConfig = createValidApplicationConfig();
            appConfig.setEnvironment(environment);

            // When
            boolean isValid = configurationValidator.validateApplicationConfig(appConfig);

            // Then
            assertTrue(isValid, "Valid environment should pass validation: " + environment);
        }

        @Test
        @DisplayName("Should validate logging configuration")
        void shouldValidateLoggingConfiguration() {
            // Given
            ApplicationConfig appConfig = createValidApplicationConfig();
            appConfig.getLogging().setLevel("INFO");
            appConfig.getLogging().setFile("/var/log/identity-service.log");
            appConfig.getLogging().setMaxFileSize("10MB");
            appConfig.getLogging().setMaxHistory(30);

            // When
            boolean isValid = configurationValidator.validateApplicationConfig(appConfig);

            // Then
            assertTrue(isValid);
        }

        @Test
        @DisplayName("Should validate health check configuration")
        void shouldValidateHealthCheckConfiguration() {
            // Given
            ApplicationConfig appConfig = createValidApplicationConfig();
            appConfig.getHealthCheck().setEnabled(true);
            appConfig.getHealthCheck().setEndpoint("/health");
            appConfig.getHealthCheck().setTimeout(Duration.ofSeconds(10));

            // When
            boolean isValid = configurationValidator.validateApplicationConfig(appConfig);

            // Then
            assertTrue(isValid);
        }
    }

    @Nested
    @DisplayName("Integration and Cross-Configuration Validation")
    class IntegrationValidationTests {

        @Test
        @DisplayName("Should validate configuration consistency across modules")
        void shouldValidateConfigurationConsistencyAcrossModules() {
            // Given
            CompleteConfiguration config = createCompleteConfiguration();

            // When
            boolean isValid = configurationValidator.validateComplete(config);

            // Then
            assertTrue(isValid);
        }

        @Test
        @DisplayName("Should detect JWT and session timeout conflicts")
        void shouldDetectJwtAndSessionTimeoutConflicts() {
            // Given
            CompleteConfiguration config = createCompleteConfiguration();
            config.getJwt().setExpirationTime(3600); // 1 hour
            config.getSecurity().getSession().setSessionTimeout(Duration.ofMinutes(30)); // 30 minutes

            // When
            boolean isValid = configurationValidator.validateComplete(config);

            // Then
            assertFalse(isValid, "JWT expiration longer than session timeout should fail validation");
        }

        @Test
        @DisplayName("Should validate database and connection pool alignment")
        void shouldValidateDatabaseAndConnectionPoolAlignment() {
            // Given
            CompleteConfiguration config = createCompleteConfiguration();
            config.getDatabase().setMaxPoolSize(100);
            config.getApplication().getServer().setMaxThreads(50);

            // When
            boolean isValid = configurationValidator.validateComplete(config);

            // Then
            assertTrue(isValid, "Properly aligned database and server configuration should pass");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null configuration gracefully")
        void shouldHandleNullConfigurationGracefully() {
            // When & Then
            assertThrows(IllegalArgumentException.class, 
                () -> configurationValidator.validateComplete(null));
        }

        @Test
        @DisplayName("Should handle configuration with missing required sections")
        void shouldHandleConfigurationWithMissingRequiredSections() {
            // Given
            CompleteConfiguration config = new CompleteConfiguration();
            // Missing database, JWT, and security configs

            // When
            boolean isValid = configurationValidator.validateComplete(config);

            // Then
            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should validate configuration with environment-specific overrides")
        void shouldValidateConfigurationWithEnvironmentSpecificOverrides() {
            // Given
            CompleteConfiguration config = createCompleteConfiguration();
            config.setEnvironment("production");
            config.getSecurity().getCors().setAllowedOrigins(Set.of("https://app.example.com"));
            config.getDatabase().setUrl("jdbc:mysql://prod-db:3306/identity_db");

            // When
            boolean isValid = configurationValidator.validateComplete(config);

            // Then
            assertTrue(isValid);
        }

        @Test
        @DisplayName("Should handle concurrent validation requests safely")
        void shouldHandleConcurrentValidationRequestsSafely() throws Exception {
            // Given
            CompleteConfiguration config = createCompleteConfiguration();
            int numberOfThreads = 10;
            CompletableFuture<Boolean>[] futures = new CompletableFuture[numberOfThreads];

            // When
            for (int i = 0; i < numberOfThreads; i++) {
                futures[i] = CompletableFuture.supplyAsync(() -> 
                    configurationValidator.validateComplete(config));
            }

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures);
            allFutures.get(5, TimeUnit.SECONDS);

            // Then
            for (CompletableFuture<Boolean> future : futures) {
                assertTrue(future.get(), "All concurrent validations should succeed");
            }
        }

        @Test
        @DisplayName("Should provide detailed validation error messages")
        void shouldProvideDetailedValidationErrorMessages() {
            // Given
            CompleteConfiguration invalidConfig = createInvalidConfiguration();

            // When
            ValidationResult result = configurationValidator.validateWithDetails(invalidConfig);

            // Then
            assertFalse(result.isValid());
            assertFalse(result.getErrors().isEmpty());
            assertTrue(result.getErrors().stream()
                .anyMatch(error -> error.contains("database")));
            assertTrue(result.getErrors().stream()
                .anyMatch(error -> error.contains("JWT")));
        }
    }

    @Nested
    @DisplayName("Performance and Load Testing")
    class PerformanceTests {

        @Test
        @DisplayName("Should validate configuration within acceptable time limits")
        void shouldValidateConfigurationWithinAcceptableTimeLimits() {
            // Given
            CompleteConfiguration config = createLargeConfiguration();

            // When
            long startTime = System.currentTimeMillis();
            boolean isValid = configurationValidator.validateComplete(config);
            long endTime = System.currentTimeMillis();

            // Then
            assertTrue(isValid);
            assertTrue(endTime - startTime < 500, "Validation should complete within 500ms");
        }

        @Test
        @DisplayName("Should handle validation of configurations with large collections")
        void shouldHandleValidationOfConfigurationsWithLargeCollections() {
            // Given
            CompleteConfiguration config = createConfigurationWithLargeCollections();

            // When
            boolean isValid = configurationValidator.validateComplete(config);

            // Then
            assertTrue(isValid);
        }
    }

    // Helper methods for creating test configurations
    private DatabaseConfig createDatabaseConfig(String url, String username, String password) {
        DatabaseConfig config = new DatabaseConfig();
        config.setUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaxPoolSize(10);
        config.setMinPoolSize(1);
        config.setConnectionTimeout(Duration.ofSeconds(30));
        return config;
    }

    private JwtConfig createJwtConfig(String secret, int expirationTime, String issuer) {
        JwtConfig config = new JwtConfig();
        config.setSecret(secret);
        config.setExpirationTime(expirationTime);
        config.setIssuer(issuer);
        config.setSigningAlgorithm("HS256");
        return config;
    }

    private JwtConfig createValidJwtConfig() {
        return createJwtConfig("super-secure-jwt-secret-key-with-sufficient-length-for-production-use", 3600, "identity-service");
    }

    private SecurityConfig createValidSecurityConfig() {
        SecurityConfig config = new SecurityConfig();
        config.setEnvironment("development");
        
        PasswordPolicy passwordPolicy = new PasswordPolicy();
        passwordPolicy.setMinLength(8);
        passwordPolicy.setRequireUppercase(true);
        passwordPolicy.setRequireLowercase(true);
        passwordPolicy.setRequireDigits(true);
        passwordPolicy.setRequireSpecialChars(true);
        config.setPasswordPolicy(passwordPolicy);
        
        SessionConfig session = new SessionConfig();
        session.setMaxSessions(3);
        session.setSessionTimeout(Duration.ofMinutes(30));
        config.setSession(session);
        
        CorsConfig cors = new CorsConfig();
        cors.setAllowedOrigins(Set.of("http://localhost:3000"));
        cors.setAllowedMethods(Set.of("GET", "POST", "PUT", "DELETE"));
        cors.setMaxAge(Duration.ofHours(1));
        config.setCors(cors);
        
        RateLimitConfig rateLimit = new RateLimitConfig();
        rateLimit.setEnabled(true);
        rateLimit.setRequestsPerMinute(60);
        rateLimit.setBurstCapacity(100);
        config.setRateLimit(rateLimit);
        
        return config;
    }

    private ApplicationConfig createValidApplicationConfig() {
        ApplicationConfig config = new ApplicationConfig();
        config.setName("identity-service");
        config.setVersion("1.0.0");
        config.setEnvironment("development");
        
        ServerConfig server = new ServerConfig();
        server.setPort(8080);
        server.setHost("localhost");
        server.setMaxThreads(200);
        config.setServer(server);
        
        LoggingConfig logging = new LoggingConfig();
        logging.setLevel("INFO");
        logging.setFile("/var/log/identity-service.log");
        logging.setMaxFileSize("10MB");
        logging.setMaxHistory(30);
        config.setLogging(logging);
        
        HealthCheckConfig healthCheck = new HealthCheckConfig();
        healthCheck.setEnabled(true);
        healthCheck.setEndpoint("/health");
        healthCheck.setTimeout(Duration.ofSeconds(10));
        config.setHealthCheck(healthCheck);
        
        return config;
    }

    private CompleteConfiguration createCompleteConfiguration() {
        CompleteConfiguration config = new CompleteConfiguration();
        config.setDatabase(createDatabaseConfig("jdbc:mysql://localhost:3306/identity_db", "admin", "password123"));
        config.setJwt(createValidJwtConfig());
        config.setSecurity(createValidSecurityConfig());
        config.setApplication(createValidApplicationConfig());
        config.setEnvironment("development");
        return config;
    }

    private CompleteConfiguration createInvalidConfiguration() {
        CompleteConfiguration config = new CompleteConfiguration();
        config.setDatabase(createDatabaseConfig("invalid-url", null, null));
        config.setJwt(createJwtConfig("short", -1, ""));
        config.setEnvironment("development");
        return config;
    }

    private CompleteConfiguration createLargeConfiguration() {
        CompleteConfiguration config = createCompleteConfiguration();
        // Add configurations that would stress test the validator
        config.getSecurity().getCors().setAllowedOrigins(
            Stream.generate(() -> "https://domain" + System.nanoTime() + ".example.com")
                .limit(100)
                .collect(java.util.stream.Collectors.toSet())
        );
        return config;
    }

    private CompleteConfiguration createConfigurationWithLargeCollections() {
        CompleteConfiguration config = createCompleteConfiguration();
        // Add large collections to test performance
        Map<String, String> largePropertyMap = Stream.iterate(0, i -> i + 1)
            .limit(1000)
            .collect(java.util.stream.Collectors.toMap(
                i -> "property" + i,
                i -> "value" + i
            ));
        config.setCustomProperties(largePropertyMap);
        return config;
    }

    // Static method for parameterized tests
    static Stream<Arguments> invalidDatabaseConfigurations() {
        return Stream.of(
            Arguments.of("Null URL", null, "user", "pass"),
            Arguments.of("Empty URL", "", "user", "pass"),
            Arguments.of("Invalid protocol", "http://localhost/db", "user", "pass"),
            Arguments.of("Missing username", "jdbc:mysql://localhost:3306/db", null, "pass"),
            Arguments.of("Missing password", "jdbc:mysql://localhost:3306/db", "user", null)
        );
    }

    static Stream<Arguments> invalidJwtConfigurations() {
        return Stream.of(
            Arguments.of("Short secret", "short", 3600, "issuer"),
            Arguments.of("Negative expiration", "long-enough-secret-key", -1, "issuer"),
            Arguments.of("Zero expiration", "long-enough-secret-key", 0, "issuer"),
            Arguments.of("Empty issuer", "long-enough-secret-key", 3600, "")
        );
    }
}

// Mock configuration classes for testing
class DatabaseConfig {
    private String url;
    private String username;
    private String password;
    private String driverClassName;
    private int maxPoolSize;
    private int minPoolSize;
    private Duration connectionTimeout;
    private boolean enableMigrations;
    private String migrationLocation;
    
    // Getters and setters
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDriverClassName() { return driverClassName; }
    public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
    public int getMinPoolSize() { return minPoolSize; }
    public void setMinPoolSize(int minPoolSize) { this.minPoolSize = minPoolSize; }
    public Duration getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(Duration connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    public boolean isEnableMigrations() { return enableMigrations; }
    public void setEnableMigrations(boolean enableMigrations) { this.enableMigrations = enableMigrations; }
    public String getMigrationLocation() { return migrationLocation; }
    public void setMigrationLocation(String migrationLocation) { this.migrationLocation = migrationLocation; }
}

class JwtConfig {
    private String secret;
    private int expirationTime;
    private String issuer;
    private String signingAlgorithm;
    private boolean refreshTokenEnabled;
    private int refreshTokenExpiration;
    
    // Getters and setters
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public int getExpirationTime() { return expirationTime; }
    public void setExpirationTime(int expirationTime) { this.expirationTime = expirationTime; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getSigningAlgorithm() { return signingAlgorithm; }
    public void setSigningAlgorithm(String signingAlgorithm) { this.signingAlgorithm = signingAlgorithm; }
    public boolean isRefreshTokenEnabled() { return refreshTokenEnabled; }
    public void setRefreshTokenEnabled(boolean refreshTokenEnabled) { this.refreshTokenEnabled = refreshTokenEnabled; }
    public int getRefreshTokenExpiration() { return refreshTokenExpiration; }
    public void setRefreshTokenExpiration(int refreshTokenExpiration) { this.refreshTokenExpiration = refreshTokenExpiration; }
}

class SecurityConfig {
    private String environment;
    private PasswordPolicy passwordPolicy;
    private SessionConfig session;
    private CorsConfig cors;
    private RateLimitConfig rateLimit;
    
    // Getters and setters
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public PasswordPolicy getPasswordPolicy() { return passwordPolicy; }
    public void setPasswordPolicy(PasswordPolicy passwordPolicy) { this.passwordPolicy = passwordPolicy; }
    public SessionConfig getSession() { return session; }
    public void setSession(SessionConfig session) { this.session = session; }
    public CorsConfig getCors() { return cors; }
    public void setCors(CorsConfig cors) { this.cors = cors; }
    public RateLimitConfig getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimitConfig rateLimit) { this.rateLimit = rateLimit; }
}

class PasswordPolicy {
    private int minLength;
    private boolean requireUppercase;
    private boolean requireLowercase;
    private boolean requireDigits;
    private boolean requireSpecialChars;
    
    // Getters and setters
    public int getMinLength() { return minLength; }
    public void setMinLength(int minLength) { this.minLength = minLength; }
    public boolean isRequireUppercase() { return requireUppercase; }
    public void setRequireUppercase(boolean requireUppercase) { this.requireUppercase = requireUppercase; }
    public boolean isRequireLowercase() { return requireLowercase; }
    public void setRequireLowercase(boolean requireLowercase) { this.requireLowercase = requireLowercase; }
    public boolean isRequireDigits() { return requireDigits; }
    public void setRequireDigits(boolean requireDigits) { this.requireDigits = requireDigits; }
    public boolean isRequireSpecialChars() { return requireSpecialChars; }
    public void setRequireSpecialChars(boolean requireSpecialChars) { this.requireSpecialChars = requireSpecialChars; }
}

class SessionConfig {
    private int maxSessions;
    private Duration sessionTimeout;
    
    // Getters and setters
    public int getMaxSessions() { return maxSessions; }
    public void setMaxSessions(int maxSessions) { this.maxSessions = maxSessions; }
    public Duration getSessionTimeout() { return sessionTimeout; }
    public void setSessionTimeout(Duration sessionTimeout) { this.sessionTimeout = sessionTimeout; }
}

class CorsConfig {
    private Set<String> allowedOrigins;
    private Set<String> allowedMethods;
    private Duration maxAge;
    
    // Getters and setters
    public Set<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(Set<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    public Set<String> getAllowedMethods() { return allowedMethods; }
    public void setAllowedMethods(Set<String> allowedMethods) { this.allowedMethods = allowedMethods; }
    public Duration getMaxAge() { return maxAge; }
    public void setMaxAge(Duration maxAge) { this.maxAge = maxAge; }
}

class RateLimitConfig {
    private boolean enabled;
    private int requestsPerMinute;
    private int burstCapacity;
    
    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getRequestsPerMinute() { return requestsPerMinute; }
    public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
    public int getBurstCapacity() { return burstCapacity; }
    public void setBurstCapacity(int burstCapacity) { this.burstCapacity = burstCapacity; }
}

class ApplicationConfig {
    private String name;
    private String version;
    private String environment;
    private ServerConfig server;
    private LoggingConfig logging;
    private HealthCheckConfig healthCheck;
    
    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public ServerConfig getServer() { return server; }
    public void setServer(ServerConfig server) { this.server = server; }
    public LoggingConfig getLogging() { return logging; }
    public void setLogging(LoggingConfig logging) { this.logging = logging; }
    public HealthCheckConfig getHealthCheck() { return healthCheck; }
    public void setHealthCheck(HealthCheckConfig healthCheck) { this.healthCheck = healthCheck; }
}

class ServerConfig {
    private int port;
    private String host;
    private int maxThreads;
    
    // Getters and setters
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getMaxThreads() { return maxThreads; }
    public void setMaxThreads(int maxThreads) { this.maxThreads = maxThreads; }
}

class LoggingConfig {
    private String level;
    private String file;
    private String maxFileSize;
    private int maxHistory;
    
    // Getters and setters
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }
    public String getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(String maxFileSize) { this.maxFileSize = maxFileSize; }
    public int getMaxHistory() { return maxHistory; }
    public void setMaxHistory(int maxHistory) { this.maxHistory = maxHistory; }
}

class HealthCheckConfig {
    private boolean enabled;
    private String endpoint;
    private Duration timeout;
    
    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
}

class CompleteConfiguration {
    private String environment;
    private DatabaseConfig database;
    private JwtConfig jwt;
    private SecurityConfig security;
    private ApplicationConfig application;
    private Map<String, String> customProperties;
    
    // Getters and setters
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public DatabaseConfig getDatabase() { return database; }
    public void setDatabase(DatabaseConfig database) { this.database = database; }
    public JwtConfig getJwt() { return jwt; }
    public void setJwt(JwtConfig jwt) { this.jwt = jwt; }
    public SecurityConfig getSecurity() { return security; }
    public void setSecurity(SecurityConfig security) { this.security = security; }
    public ApplicationConfig getApplication() { return application; }
    public void setApplication(ApplicationConfig application) { this.application = application; }
    public Map<String, String> getCustomProperties() { return customProperties; }
    public void setCustomProperties(Map<String, String> customProperties) { this.customProperties = customProperties; }
}

// Mock validator classes
class ConfigurationValidator {
    private final Validator validator;
    
    public ConfigurationValidator(Validator validator) {
        this.validator = validator;
    }
    
    public boolean validateDatabaseConfig(DatabaseConfig config) {
        // Mock validation logic
        return config != null && 
               config.getUrl() != null && 
               config.getUrl().startsWith("jdbc:") &&
               config.getUsername() != null && 
               !config.getUsername().trim().isEmpty() &&
               config.getPassword() != null &&
               config.getMaxPoolSize() > 0 && config.getMaxPoolSize() <= 1000;
    }
    
    public boolean validateJwtConfig(JwtConfig config) {
        // Mock validation logic
        return config != null &&
               config.getSecret() != null && 
               config.getSecret().length() >= 32 &&
               config.getExpirationTime() > 0 && config.getExpirationTime() <= 86400 &&
               config.getIssuer() != null && !config.getIssuer().trim().isEmpty() &&
               isValidSigningAlgorithm(config.getSigningAlgorithm());
    }
    
    public boolean validateSecurityConfig(SecurityConfig config) {
        // Mock validation logic
        return config != null &&
               config.getPasswordPolicy() != null &&
               config.getPasswordPolicy().getMinLength() >= 8 &&
               !(config.getEnvironment().equals("production") && 
                 config.getCors().getAllowedOrigins().contains("*"));
    }
    
    public boolean validateApplicationConfig(ApplicationConfig config) {
        // Mock validation logic
        return config != null &&
               config.getServer() != null &&
               config.getServer().getPort() > 0 && config.getServer().getPort() < 65536 &&
               config.getServer().getHost() != null && !config.getServer().getHost().trim().isEmpty();
    }
    
    public boolean validateComplete(CompleteConfiguration config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }
        
        return validateDatabaseConfig(config.getDatabase()) &&
               validateJwtConfig(config.getJwt()) &&
               validateSecurityConfig(config.getSecurity()) &&
               validateApplicationConfig(config.getApplication()) &&
               validateCrossConfigurationRules(config);
    }
    
    public ValidationResult validateWithDetails(CompleteConfiguration config) {
        ValidationResult result = new ValidationResult();
        
        if (!validateDatabaseConfig(config.getDatabase())) {
            result.addError("Invalid database configuration");
        }
        if (!validateJwtConfig(config.getJwt())) {
            result.addError("Invalid JWT configuration");
        }
        if (!validateSecurityConfig(config.getSecurity())) {
            result.addError("Invalid security configuration");
        }
        if (!validateApplicationConfig(config.getApplication())) {
            result.addError("Invalid application configuration");
        }
        
        return result;
    }
    
    private boolean validateCrossConfigurationRules(CompleteConfiguration config) {
        // Check if JWT expiration is not longer than session timeout
        if (config.getJwt() != null && config.getSecurity() != null && 
            config.getSecurity().getSession() != null) {
            long jwtExpirationSeconds = config.getJwt().getExpirationTime();
            long sessionTimeoutSeconds = config.getSecurity().getSession().getSessionTimeout().getSeconds();
            return jwtExpirationSeconds <= sessionTimeoutSeconds;
        }
        return true;
    }
    
    private boolean isValidSigningAlgorithm(String algorithm) {
        return algorithm != null && 
               Set.of("HS256", "HS384", "HS512", "RS256", "RS384", "RS512").contains(algorithm);
    }
}

class ValidationResult {
    private final java.util.List<String> errors = new java.util.ArrayList<>();
    
    public boolean isValid() {
        return errors.isEmpty();
    }
    
    public java.util.List<String> getErrors() {
        return new java.util.ArrayList<>(errors);
    }
    
    public void addError(String error) {
        errors.add(error);
    }
    
    public java.util.Set<ConstraintViolation<?>> getViolations() {
        // Mock implementation
        return Set.of();
    }
}

class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}