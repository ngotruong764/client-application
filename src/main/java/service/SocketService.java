package service;

import model.ClientInfo;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SocketService {
    private static final Logger logger = Logger.getLogger(SocketService.class.getName());
    public static ServerSocket serverSocket;

    /**
     * Method used to establish socket connection to a destination
     * @param host is the address of another client we want to connect
     * @param port is the port of the address we connect
     */
    public void connect(String host, int port) {
        try(Socket socket = new Socket(host, port);) {
        } catch (Exception e) {
            logger.log(Level.FINER, e.toString());
        }
    }

    /**
     * Method used to send the message to another
     * @param message is the address of another client we want to connect
     */
    public void sendRawMessage(Socket socket, String message) {
        if (socket == null) {
            logger.info("Socket connection does not initialize");
        }

        try(OutputStream os = socket.getOutputStream()) {
            //write to a socket
            os.write(message.getBytes());
            logger.info("Sending request to Socket Server");
        } catch (Exception e){
            logger.log(Level.FINER, e.toString());
        }
    }

    /**
     * waiting for client connection
     */
    public ServerSocket establishSocket() {
        // Start a server socket to get port
        // 0: any free port
        try(ServerSocket server = new ServerSocket(0)) {
            new Thread(() -> {
                // Keep listens indefinitely
                try {
                    while (true) {
                        // Creating a socket and waiting for client connection
                        Socket socket = server.accept();
                        // Read from socket
                        InputStream os = socket.getInputStream();
                    }
                } catch (Exception e) {
                    logger.log(Level.FINER, e.toString());
                }
            }).start();
            return server;
        } catch (Exception e){
            logger.log(Level.FINER, e.toString());
        }
        return null;
    }

    /**
     * Method used to download a file in parallel
     * @param fileName the file we want to download
     * @param clients numbers of users hold the file we want to download
     * @param des destination of the downloaded file
     */
//    public void downLoadFile(String fileName, List<ClientInfo> clients, String des, int buffer){
//        // TODO: Get file size
//        long fileSize = 100000L;
//        long readBytes = 0;
//        int offSet = 0;
//
//        List<Socket> sockets = new ArrayList<>();
//
//        try {
//            // Connects socket
//            for (ClientInfo clientInfo : clients) {
//                sockets.add(connect(clientInfo.getSocketAddr(), clientInfo.getSocketPort()));
//            }
//
//            // Send the request to download files
//
//
//            FileOutputStream os = new FileOutputStream(des+"/"+fileName);
//            while (fileSize > readBytes) {
//
////                os.write();
//
//            }
//        } catch (Exception e){
//            logger.log(Level.FINER, e.toString());
//        }
//    }
}
