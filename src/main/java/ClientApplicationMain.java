import model.ClientInfo;
import model.FileInfo;
import model.FileOwner;
import service.FileService;
import service.IDirectoryService;
import service.SocketService;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClientApplicationMain {
    private static final Logger logger = Logger.getLogger(ClientApplicationMain.class.getName());

    // Thread pool
    private static final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);

    private static final String RMI_HOST = "RMI_HOST";
    private static final String RMI_PORT = "RMI_PORT";
    private static final String CLIENT_ID = "CLIENT_ID";
    private static final String FILES_DIRECTORY_PATH = "src/main/resources/static";

    // Services
    private static final FileService fileService = new FileService();

    public static void main(String[] args) {
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

        SocketService socketService = new SocketService();

        // Establish socket connection
        try (ServerSocket serverSocket = new ServerSocket(0)) {
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
                filesFromServer.forEach(fileOwner -> {
                    System.out.printf(
                            "File: %s | Owner: %s%n",
                            fileOwner.getFileInfo().getFileName(),
                            fileOwner.getClientInfos().stream().map(ClientInfo::getId).collect(Collectors.joining(", "))
                    );
                });

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
                // Creating a socket and waiting for client connection
                Socket socket = serverSocket.accept();
                InetSocketAddress remoteAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
                logger.info("Connected client IP: " + remoteAddress.getAddress().getHostAddress());
                logger.info("Connected client port: " + remoteAddress.getPort());
                // Read from socket
                InputStream os = socket.getInputStream();
            }

        } catch (Exception e) {
            logger.log(Level.FINER, e.toString());
            e.printStackTrace();
        }

    }


}
