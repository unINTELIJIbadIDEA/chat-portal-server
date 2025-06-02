package com.project.utils;

import com.project.ServerLauncher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class SshTunnel {
    private static final Logger logger = Logger.getLogger(ServerLauncher.class.getName());

    static {
        try {
            FileHandler fileHandler = new FileHandler("logs/tunnel.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.INFO);
        } catch (Exception e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }
    private final int localPort;
    private final int remotePort;
    private Process process;

    public SshTunnel(int remotePort, int localPort) {
        this.remotePort = remotePort;
        this.localPort = localPort;
    }

    public void openTunnel() {
        ProcessBuilder builder = new ProcessBuilder(
                "ssh",
                "-o", "StrictHostKeyChecking=no",
                "-R", remotePort + ":localhost:" + localPort,
                "serveo.net"
        );

        builder.redirectErrorStream(true);

        try {
            process = builder.start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info("[SSH TUNNEL] " + line);
                    }
                } catch (IOException e) {
                    logger.warning("[SSH TUNNEL] Error reading output: " + e.getMessage());
                }
            }).start();

            logger.info("[SSH TUNNEL] Tunnel opened: remote " + remotePort + " → local " + localPort);

        } catch (IOException e) {
            logger.warning("[SSH TUNNEL] Failed to open tunnel: " + e.getMessage());
        }
    }

    public void closeTunnel() {
        if (process != null && process.isAlive()) {
            process.destroy();
            logger.info("[SSH TUNNEL] Tunnel closed.");
        }
    }
}
