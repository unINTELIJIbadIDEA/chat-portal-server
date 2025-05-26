package com.project;

import com.project.server.ApiServer;
import com.project.server.Server;
import com.project.utils.Config;
import com.project.utils.SshTunnel;
import com.project.server.BattleshipServer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerLauncher {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(false);
            return thread;
        });

        var tcpServer = new Server();
        var apiServer = new ApiServer();
        var battleshipServer = BattleshipServer.getInstance();

        // TUNELE - DODAJ battleshipTunnel
        var tcpTunnel = new SshTunnel(Config.getREMOTE_SERVER_PORT(), Config.getLOCAL_SERVER_PORT());
        var apiTunnel = new SshTunnel(Config.getREMOTE_API_PORT(), Config.getLOCAL_API_PORT());
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
                System.err.println("API Server crashed: " + e.getMessage());
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

        // OTWÓRZ TUNELE - DODAJ battleshipTunnel
        apiTunnel.openTunnel();
        tcpTunnel.openTunnel();
        battleshipTunnel.openTunnel();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Zamykanie serwerów...");

            try {
                tcpServer.stopServer();
                apiServer.stopServer();
                battleshipServer.stopServer();
            } catch (Exception e) {
                System.out.println("Błąd przy zamykaniu serwerów: " + e.getMessage());
            }

            executor.shutdown();
            System.out.println("Serwery zamknięte.");

            // ZAMKNIJ TUNELE - DODAJ battleshipTunnel
            apiTunnel.closeTunnel();
            tcpTunnel.closeTunnel();
            battleshipTunnel.closeTunnel();
        }));
    }
}