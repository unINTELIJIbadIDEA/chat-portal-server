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
            System.out.println("[SSH TUNNEL] Opening tunnel: remote " + remotePort + " → local " + localPort);
            process = builder.start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[SSH TUNNEL " + remotePort + "] " + line);

                        // SZUKAJ tej linii w logach!
                        if (line.contains("Forwarding HTTP traffic from")) {
                            System.out.println("✅ TUNNEL READY: " + remotePort);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("[SSH TUNNEL] Error reading output: " + e.getMessage());
                }
            }).start();

            System.out.println("[SSH TUNNEL] Tunnel process started: remote " + remotePort + " → local " + localPort);

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
