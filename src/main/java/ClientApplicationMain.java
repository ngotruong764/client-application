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
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

public class ClientApplicationMain {

    private static final Logger logger = Logger.getLogger(ClientApplicationMain.class.getName());

    private static final ThreadPoolExecutor executor =
            (ThreadPoolExecutor) Executors.newFixedThreadPool(10);

    private static final String RMI_HOST = "RMI_HOST";
    private static final String RMI_PORT = "RMI_PORT";
    private static final String CLIENT_ID = "CLIENT_ID";
    private static final String FILES_DIRECTORY_PATH = "files";

    private static final FileService fileService = new FileService();

    public static void main(String[] args) {

        Map<String, String> env = System.getenv();

        String rmiHost = env.getOrDefault(RMI_HOST, "localhost");
        int rmiPort = Integer.parseInt(env.getOrDefault(RMI_PORT, "1099"));
        String clientId = env.getOrDefault(CLIENT_ID, UUID.randomUUID().toString());

        if (clientId.isEmpty()) {
            logger.info("Client ID is empty");
            return;
        }

        logger.info("Client ID: " + clientId);

        try (ServerSocket serverSocket = new ServerSocket(0)) {

            List<FileInfo> clientFiles = fileService.getAllFiles(FILES_DIRECTORY_PATH);

            int assignedPort = serverSocket.getLocalPort();
            String socketAddress = InetAddress.getLocalHost().getHostAddress();

            logger.info(String.format("Socket established %s:%s", socketAddress, assignedPort));

            ClientInfo clientInfo = new ClientInfo();
            clientInfo.setId(clientId);
            clientInfo.setSocketAddr(socketAddress);
            clientInfo.setSocketPort(assignedPort);

            Registry registry = LocateRegistry.getRegistry(rmiHost, rmiPort);
            IDirectoryService stub =
                    (IDirectoryService) registry.lookup("IDirectoryService");

            stub.registryFile(clientInfo, clientFiles);

            List<FileOwner> filesFromServer = stub.getAllAvailableFiles();

            if (filesFromServer == null || filesFromServer.isEmpty()) {
                logger.info("There are no available files!");
            } else {

                filesFromServer.forEach(fileOwner -> {
                    System.out.printf(
                            "File: %s | Owner: %s%n",
                            fileOwner.getFileInfo().getFileName(),
                            fileOwner.getClientInfos()
                                    .stream()
                                    .map(ClientInfo::getId)
                                    .collect(Collectors.joining(", "))
                    );
                });
            }

            while (true) {

                Socket socket = serverSocket.accept();

                executor.execute(() -> {

                    try {

                        InetSocketAddress remote =
                                (InetSocketAddress) socket.getRemoteSocketAddress();

                        logger.info("Client IP: " +
                                remote.getAddress().getHostAddress());

                        logger.info("Client port: " + remote.getPort());

                        DataInputStream in =
                                new DataInputStream(socket.getInputStream());

                        String fileName = in.readUTF();
                        long start = in.readLong();
                        long end = in.readLong();

                        logger.info("Download request: " +
                                fileName + " [" + start + " - " + end + "]");

                        File file = new File("files/" + fileName);

                        if (!file.exists()) {
                            logger.warning("File not found");
                            socket.close();
                            return;
                        }

                        RandomAccessFile raf =
                                new RandomAccessFile(file, "r");

                        raf.seek(start);

                        GZIPOutputStream gzipOut =
                                new GZIPOutputStream(socket.getOutputStream());

                        byte[] buffer = new byte[4096];
                        long remaining = end - start + 1;

                        while (remaining > 0) {

                            int read = raf.read(
                                    buffer,
                                    0,
                                    (int) Math.min(buffer.length, remaining)
                            );

                            if (read == -1) break;

                            gzipOut.write(buffer, 0, read);

                            remaining -= read;
                        }

                        gzipOut.finish();
                        gzipOut.flush();

                        raf.close();
                        socket.close();

                        logger.info("Compressed chunk sent");

                    } catch (Exception e) {
                        logger.log(Level.SEVERE, e.getMessage());
                    }

                });
            }

        } catch (Exception e) {

            logger.log(Level.FINER, e.toString());
            e.printStackTrace();
        }
    }
}