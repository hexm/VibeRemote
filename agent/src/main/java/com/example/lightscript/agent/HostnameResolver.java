package com.example.lightscript.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

final class HostnameResolver {
    private static final String UNKNOWN_HOST = "unknown-host";

    private HostnameResolver() {
    }

    static String resolve() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac")) {
            return resolveMacHostname();
        }
        if (osName.contains("win")) {
            return resolveWindowsHostname();
        }
        if (osName.contains("nux") || osName.contains("nix") || osName.contains("aix")) {
            return resolveLinuxHostname();
        }

        String systemHostname = normalize(runCommand("hostname"));
        if (systemHostname != null) {
            return systemHostname;
        }

        String inetHostName = normalize(getInetHostName());
        if (inetHostName != null) {
            return inetHostName;
        }

        return UNKNOWN_HOST;
    }

    private static String resolveMacHostname() {
        String localHostName = normalize(runCommand("scutil", "--get", "LocalHostName"));
        if (localHostName != null) {
            return ensureLocalSuffix(localHostName);
        }

        String systemHostname = normalize(runCommand("hostname"));
        if (systemHostname != null) {
            return systemHostname;
        }

        String inetHostName = normalize(getInetHostName());
        if (inetHostName != null) {
            return inetHostName;
        }

        return UNKNOWN_HOST;
    }

    private static String resolveLinuxHostname() {
        String staticHostname = normalize(runCommand("hostnamectl", "--static"));
        if (staticHostname != null) {
            return staticHostname;
        }

        String fqdnHostname = normalize(runCommand("hostname", "-f"));
        if (fqdnHostname != null) {
            return fqdnHostname;
        }

        String systemHostname = normalize(runCommand("hostname"));
        if (systemHostname != null) {
            return systemHostname;
        }

        String inetHostName = normalize(getInetHostName());
        if (inetHostName != null) {
            return inetHostName;
        }

        return UNKNOWN_HOST;
    }

    private static String resolveWindowsHostname() {
        String computerName = normalize(System.getenv("COMPUTERNAME"));
        if (computerName != null) {
            return computerName;
        }

        String systemHostname = normalize(runCommand("hostname"));
        if (systemHostname != null) {
            return systemHostname;
        }

        String inetHostName = normalize(getInetHostName());
        if (inetHostName != null) {
            return inetHostName;
        }

        return UNKNOWN_HOST;
    }

    private static String ensureLocalSuffix(String host) {
        return host.endsWith(".local") ? host : host + ".local";
    }

    private static String getInetHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase();
        if ("localhost".equals(lower) || "localhost.localdomain".equals(lower) || UNKNOWN_HOST.equals(lower)) {
            return null;
        }
        if (looksLikeIpv4(trimmed) || looksLikeIpv6(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static boolean looksLikeIpv4(String value) {
        return value.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
    }

    private static boolean looksLikeIpv6(String value) {
        return value.contains(":");
    }

    private static String runCommand(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
            if (!process.waitFor(2, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                return line == null ? null : line.trim();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (process != null) {
                process.destroy();
                try {
                    process.getInputStream().close();
                } catch (IOException ignored) {
                }
                try {
                    process.getOutputStream().close();
                } catch (IOException ignored) {
                }
                try {
                    process.getErrorStream().close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
