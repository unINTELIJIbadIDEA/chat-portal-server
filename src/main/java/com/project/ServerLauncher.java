package com.project;

import com.project.server.ApiServer;
import com.project.server.Server;
import com.project.config.ConfigProperties;
import com.project.utils.SshTunnel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.*;

public class ServerLauncher {

    private static final Logger logger = Logger.getLogger(ServerLauncher.class.getName());

    static {
        try {
            FileHandler fileHandler = new FileHandler("logs/serverlauncher.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.INFO);
        } catch (Exception e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(false);
            return thread;
        });

        var tcpServer = new Server();
        var apiServer = new ApiServer();

        var tcpTunnel = new SshTunnel(ConfigProperties.getREMOTE_SERVER_PORT(), ConfigProperties.getLOCAL_SERVER_PORT());
        var apiTunnel = new SshTunnel(ConfigProperties.getREMOTE_API_PORT(), ConfigProperties.getLOCAL_API_PORT());

        executor.submit(() -> {
            Thread.currentThread().setName("TCP-Server-Thread");
            try {
                tcpServer.runServer();
            } catch (Exception e) {
                logger.severe("TCP Server crashed: " + e.getMessage());
            }
        });

        executor.submit(() -> {
            Thread.currentThread().setName("API-Server-Thread");
            try {
                apiServer.runServer();
            } catch (Exception e) {
                logger.severe("API Server crashed: " + e.getMessage());
            }
        });

        apiTunnel.openTunnel();
        tcpTunnel.openTunnel();
        logger.info("Tunnels opened, servers running.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down servers...");

            try {
                tcpServer.stopServer();
                apiServer.stopServer();
            } catch (Exception e) {
                logger.warning("Error while stopping servers: " + e.getMessage());
            }

            executor.shutdown();
            logger.info("Servers stopped.");

            apiTunnel.closeTunnel();
            tcpTunnel.closeTunnel();
            logger.info("Tunnels closed.");
        }));
    }
}