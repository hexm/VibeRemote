package com.example.lightscript.agent;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class AgentPaths {
    private static final String AGENT_HOME_PROPERTY = "agent.home";

    private AgentPaths() {
    }

    static Path getAgentHome() {
        String configuredHome = System.getProperty(AGENT_HOME_PROPERTY);
        if (configuredHome != null && !configuredHome.trim().isEmpty()) {
            return Paths.get(configuredHome).toAbsolutePath().normalize();
        }

        try {
            URI location = AgentPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path codeSource = Paths.get(location).toAbsolutePath().normalize();
            if (Files.isRegularFile(codeSource)) {
                return codeSource.getParent();
            }
        } catch (Exception ignored) {
        }

        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    static Path getLogsDir() {
        return getAgentHome().resolve("logs");
    }

    static Path getPidFile() {
        return getAgentHome().resolve("agent.pid");
    }
}
