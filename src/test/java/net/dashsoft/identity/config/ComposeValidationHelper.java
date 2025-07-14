package net.dashsoft.identity.config;

import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

/**
 * Helper utility class for Docker Compose validation operations.
 * Provides common validation methods used across multiple test classes.
 */
public class ComposeValidationHelper {

    private static final Yaml yaml = new Yaml();

    /**
     * Validates that a compose configuration contains required postgres environment variables.
     */
    public static boolean hasRequiredPostgresEnvironment(Map<String, Object> compose) {
        if (!compose.containsKey("services")) {
            return false;
        }
        
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        if (!services.containsKey("postgres")) {
            return false;
        }
        
        Map<String, Object> postgres = (Map<String, Object>) services.get("postgres");
        if (!postgres.containsKey("environment")) {
            return false;
        }
        
        List<String> environment = (List<String>) postgres.get("environment");
        
        boolean hasDb = environment.stream().anyMatch(env -> env.startsWith("POSTGRES_DB="));
        boolean hasPassword = environment.stream().anyMatch(env -> env.startsWith("POSTGRES_PASSWORD="));
        boolean hasUser = environment.stream().anyMatch(env -> env.startsWith("POSTGRES_USER="));
        
        return hasDb && hasPassword && hasUser;
    }

    /**
     * Validates that postgres service has proper port configuration.
     */
    public static boolean hasValidPostgresPorts(Map<String, Object> compose) {
        if (!compose.containsKey("services")) {
            return false;
        }
        
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        if (!services.containsKey("postgres")) {
            return false;
        }
        
        Map<String, Object> postgres = (Map<String, Object>) services.get("postgres");
        if (!postgres.containsKey("ports")) {
            return false;
        }
        
        List<String> ports = (List<String>) postgres.get("ports");
        return ports.stream().anyMatch(port -> port.contains("5432"));
    }

    /**
     * Parses a YAML string and returns the compose configuration.
     */
    public static Map<String, Object> parseComposeFile(String yamlContent) {
        return yaml.load(yamlContent);
    }

    /**
     * Validates that the compose file uses official postgres image.
     */
    public static boolean usesOfficialPostgresImage(Map<String, Object> compose) {
        if (!compose.containsKey("services")) {
            return false;
        }
        
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        if (!services.containsKey("postgres")) {
            return false;
        }
        
        Map<String, Object> postgres = (Map<String, Object>) services.get("postgres");
        if (!postgres.containsKey("image")) {
            return false;
        }
        
        String image = (String) postgres.get("image");
        return image.startsWith("postgres:");
    }
}