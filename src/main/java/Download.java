import model.ClientInfo;
import model.FileOwner;
import service.IDirectoryService;

import java.io.*;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class Download {

    private static final String RMI_HOST = "localhost";
    private static final int RMI_PORT = 1099;
    private static final String DOWNLOAD_DIR = "downloads";

    private static final AtomicLong downloadedBytes = new AtomicLong(0);
    private static long totalFileSize;

    public static void main(String[] args) {

        if (args.length == 0) {
            System.out.println("Usage: java Download <filename>");
            return;
        }

        String fileName = args[0];

        try {
            File dir = new File(DOWNLOAD_DIR);
            if (!dir.exists()) dir.mkdirs();

            Registry registry = LocateRegistry.getRegistry(RMI_HOST, RMI_PORT);
            IDirectoryService directory =
                    (IDirectoryService) registry.lookup("IDirectoryService");

            List<FileOwner> fileOwners = directory.getAllAvailableFiles();

            FileOwner target = null;
            for (FileOwner f : fileOwners) {
                if (f.getFileInfo().getFileName().equals(fileName)) {
                    target = f;
                    break;
                }
            }

            if (target == null) {
                System.out.println("File not found");
                return;
            }

            totalFileSize = target.getFileInfo().getFileSize();
            List<ClientInfo> owners = target.getClientInfos();

            int parts = owners.size();
            long chunkSize = totalFileSize / parts;

            ExecutorService executor = Executors.newFixedThreadPool(parts);

            for (int i = 0; i < parts; i++) {

                ClientInfo owner = owners.get(i);

                long start = i * chunkSize;
                long end = (i == parts - 1) ? totalFileSize - 1 : start + chunkSize - 1;

                int partNumber = i;

                executor.submit(() ->
                        downloadPart(owner, fileName, start, end, partNumber));
            }

            executor.shutdown();
            while (!executor.isTerminated()) Thread.sleep(500);

            mergeParts(fileName, parts);

            System.out.println("\nDownload completed");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void downloadPart(ClientInfo owner,
                                     String fileName,
                                     long start,
                                     long end,
                                     int part) {

        try (Socket socket = new Socket(owner.getSocketAddr(), owner.getSocketPort())) {

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(fileName);
            out.writeLong(start);
            out.writeLong(end);
            out.flush();

            DataInputStream in = new DataInputStream(socket.getInputStream());

            FileOutputStream fos = new FileOutputStream(
                    DOWNLOAD_DIR + "/" + fileName + ".part" + part);

            byte[] buffer = new byte[4096];
            int read;

            while ((read = in.read(buffer)) != -1) {

                fos.write(buffer, 0, read);

                long current = downloadedBytes.addAndGet(read);
                int percent = (int)((current * 100) / totalFileSize);

                System.out.print("\rDownloading: " + percent + "%");
            }

            fos.close();
            System.out.println("\nPart " + part + " done");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void mergeParts(String fileName, int parts) throws IOException {

        FileOutputStream fos =
                new FileOutputStream(DOWNLOAD_DIR + "/download_" + fileName);

        for (int i = 0; i < parts; i++) {

            FileInputStream fis =
                    new FileInputStream(DOWNLOAD_DIR + "/" + fileName + ".part" + i);

            byte[] buffer = new byte[4096];
            int read;

            while ((read = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }

            fis.close();
            new File(DOWNLOAD_DIR + "/" + fileName + ".part" + i).delete();
        }

        fos.close();
    }
}