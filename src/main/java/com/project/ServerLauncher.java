package com.project;

import com.project.server.ApiServer;
import com.project.server.Server;
import com.project.config.ConfigProperties;
import com.project.utils.SshTunnel;
import com.project.server.BattleshipServer;

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
        var battleshipServer = BattleshipServer.getInstance();


        var tcpTunnel = new SshTunnel(ConfigProperties.getREMOTE_SERVER_PORT(), ConfigProperties.getLOCAL_SERVER_PORT());
        var apiTunnel = new SshTunnel(ConfigProperties.getREMOTE_API_PORT(), ConfigProperties.getLOCAL_API_PORT());
        var battleshipTunnel = new SshTunnel(Config.getREMOTE_BATTLESHIP_PORT(), Config.getBATTLESHIP_SERVER_PORT());


        executor.submit(() -> {
            Thread.currentThread().setName("TCP-Server-Thread");
            try {
                tcpServer.runServer();
            } catch (Exception e) {
                System.err.println("TCP Server crashed: " + e.getMessage());
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

        executor.submit(() -> {
            Thread.currentThread().setName("Battleship-Server-Thread");
            try {
                battleshipServer.runServer();
            } catch (Exception e) {
                System.err.println("Battleship Server crashed: " + e.getMessage());
            }
        });


        apiTunnel.openTunnel();
        tcpTunnel.openTunnel();
        battleshipTunnel.openTunnel();
        logger.info("Tunnels opened, servers running.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down servers...");

            try {
                tcpServer.stopServer();
                apiServer.stopServer();
                battleshipServer.stopServer();
            } catch (Exception e) {
                logger.warning("Error while stopping servers: " + e.getMessage());
            }

            executor.shutdown();
            logger.info("Servers stopped.");

            apiTunnel.closeTunnel();
            tcpTunnel.closeTunnel();
            battleshipTunnel.closeTunnel();
            logger.info("Tunnels closed.");
        }));
    }
}
