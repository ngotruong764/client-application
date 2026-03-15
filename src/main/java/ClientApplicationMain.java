import model.ClientInfo;
import model.FileInfo;
import model.FileOwner;
import service.FileService;
import service.IDirectoryService;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClientApplicationMain {
    private static final Logger logger = Logger.getLogger(ClientApplicationMain.class.getName());

    private static final String RMI_HOST = "RMI_HOST";
    private static final String RMI_PORT = "RMI_PORT";
    private static final String CLIENT_ID = "CLIENT_ID";
    private static final String FILES_DIRECTORY_PATH = "src/main/resources/static/";

    // Services
    private static final FileService fileService = new FileService();

    public static void main(String[] args) {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT [%4$s] %5$s %n");
        // Get environment variables
        Map<String, String> env = System.getenv();
        String rmiHost = env.getOrDefault(RMI_HOST, "localhost");
        int rmiPort = Integer.parseInt(env.getOrDefault(RMI_PORT, "1099"));
        String clientId = env.getOrDefault(CLIENT_ID, UUID.randomUUID().toString());

        // If client ID is not provided -> return
        if (clientId.isEmpty()) {
            logger.info("Client ID is empty");
            return;
        }
        logger.info("Client ID: " + clientId);

        // Establish socket connection
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            // Get all available files of current user
            List<FileInfo> clientFiles = fileService.getAllFiles(FILES_DIRECTORY_PATH);

            int assignedPort = serverSocket.getLocalPort();
            String socketAddress = InetAddress.getLocalHost().getHostAddress();
            logger.info( String.format("Socket established %s:%s", socketAddress, assignedPort));

            ClientInfo clientInfo = new ClientInfo();
            clientInfo.setId(clientId);
            clientInfo.setSocketAddr(socketAddress);
            clientInfo.setSocketPort(assignedPort);

            // Getting the RMI registry
            Registry registry = LocateRegistry.getRegistry(rmiHost, rmiPort);

            // Looking up the remote object in the registry
            IDirectoryService stub = (IDirectoryService) registry.lookup("IDirectoryService");

            // Registry files to the central directory server
            stub.registryFile(clientInfo, clientFiles);

            // When a client node starts -> Get all available files with and clientInfo from the server
            List<FileOwner> filesFromServer = stub.getAllAvailableFiles();

            // In the case, no files founded
            if (filesFromServer == null || filesFromServer.isEmpty()) {
                logger.info("There are no available files!");
            } else {
                // In the case, files are found
                filesFromServer.forEach(fileOwner ->
                        logger.info(String.format("File: %s | Owner: %s",
                            fileOwner.getFileInfo().getFileName(),
                            fileOwner.getClientInfos().stream().map(ClientInfo::getId).collect(Collectors.joining(", ")))));

//                // If we have filesFromServer, but the current client has it -> ignore the files
//                List<FileOwner> fileOwners = filesFromServer.stream()
//                        .filter(fileOwner -> fileOwner.getClientInfos().stream().noneMatch(client -> client.getId().equals(clientId)))
//                        .toList();
//
//                if (fileOwners.isEmpty()) {
//                    logger.info("There are no available files!");
//                } else {
//                    fileOwners.forEach(fileOwner -> {
//                        System.out.printf(
//                                "File: %s | Owner: %s%n",
//                                fileOwner.getFileInfo().getFileName(),
//                                fileOwner.getClientInfos().stream().map(ClientInfo::getId).collect(Collectors.joining(", "))
//                        );
//                    });
//                }

            }

            // Connect
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> {
                    try {
                        InetSocketAddress remote =
                                (InetSocketAddress) socket.getRemoteSocketAddress();

                        logger.info("Client IP: " + remote.getAddress().getHostAddress());
                        logger.info("Client port: " + remote.getPort());

                        DataInputStream in =
                                new DataInputStream(socket.getInputStream());
                        DataOutputStream out =
                                new DataOutputStream(socket.getOutputStream());

                        // read request
                        String fileName = in.readUTF();
                        long start = in.readLong();
                        long end = in.readLong();

                        logger.info("Download request: " + fileName +
                                " [" + start + " - " + end + "]");

                        File file = new File(FILES_DIRECTORY_PATH + fileName);

                        if (!file.exists()) {
                            logger.warning("File not found");
                            socket.close();
                            return;
                        }

                        RandomAccessFile raf = new RandomAccessFile(file, "r");

                        raf.seek(start);

                        byte[] buffer = new byte[4096];
                        long remaining = end - start + 1;

                        while (remaining > 0) {

                            int read = raf.read(buffer, 0,
                                    (int) Math.min(buffer.length, remaining));

                            if (read == -1) break;

                            out.write(buffer, 0, read);
                            remaining -= read;
                        }

                        out.flush();
                        raf.close();
                        socket.close();

                        logger.info("Chunk sent");

                    } catch (Exception e) {
                        logger.severe(e.getMessage());
                    }
                }).start();
            }

        } catch (Exception e) {
            logger.log(Level.FINER, e.toString());
        }

    }


}
