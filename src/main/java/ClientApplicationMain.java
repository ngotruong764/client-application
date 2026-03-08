import model.FileInfo;
import service.FileService;
import service.IDirectoryService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ClientApplicationMain {
    private static final Logger logger = Logger.getLogger(ClientApplicationMain.class.getName());

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
        String clientId = env.getOrDefault(CLIENT_ID, "");

        // If client ID is not provided -> return
        if (!clientId.isEmpty()) {
            logger.info("Client ID is empty");
            return;
        }

        try {
            List<FileInfo> files = fileService.getAllFiles(FILES_DIRECTORY_PATH);

            // Getting the RMI registry
            Registry registry = LocateRegistry.getRegistry(rmiHost, rmiPort);

            // Looking up the remote object in the registry
            IDirectoryService stub = (IDirectoryService) registry.lookup("IDirectoryService");

            // Registry files to the central directory server
            stub.registryFile(clientId, files);

        } catch (Exception e) {
            System.err.println("Client exception: " + e);
            e.printStackTrace();
        }

    }


}
