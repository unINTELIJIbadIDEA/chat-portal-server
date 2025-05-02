package com.project.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class SshTunnel {

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
                        System.out.println("[SSH TUNNEL] " + line);
                    }
                } catch (IOException e) {
                    System.err.println("[SSH TUNNEL] Error reading output: " + e.getMessage());
                }
            }).start();

            System.out.println("[SSH TUNNEL] Tunnel opened: remote " + remotePort + " → local " + localPort);

        } catch (IOException e) {
            System.err.println("[SSH TUNNEL] Failed to open tunnel: " + e.getMessage());
        }
    }

    public void closeTunnel() {
        if (process != null && process.isAlive()) {
            process.destroy();
            System.out.println("[SSH TUNNEL] Tunnel closed.");
        }
    }
}
