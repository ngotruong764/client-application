import model.ClientInfo;
import model.FileOwner;
import service.IDirectoryService;

import java.io.*;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import java.util.logging.Logger;

public class Download {
    private static final Logger logger = Logger.getLogger(Download.class.getName());
    private static final String RMI_HOST = "localhost";
    private static final int RMI_PORT = 1099;
    private static final String DOWNLOAD_DIR = "src/main/resources/static";

    private static long[] partProgress;
    private static long[] partSizeArr;
    private static long[] partStartTime;

    private static volatile boolean downloading = true;
    private static long totalStartTime;

    public static void main(String[] args) {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT [%4$s] %5$s %n");

        // If the user does not provide a file name -> return
        if (args.length == 0) {
            logger.info("You need provide a file name: java Download <filename>");
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

            // Find the target file and its owner
            FileOwner target = null;
            for (FileOwner f : fileOwners) {
                if (f.getFileInfo().getFileName().equals(fileName)) {
                    target = f;
                    break;
                }
            }

            if (target == null) {
                logger.info("No file found at " + fileName);
                return;
            }

            long totalFileSize = target.getFileInfo().getFileSize();
            List<ClientInfo> owners = target.getClientInfos();

            int parts = owners.size();

            partProgress = new long[parts];
            partSizeArr = new long[parts];
            partStartTime = new long[parts];

            long chunkSize = totalFileSize / parts;

            totalStartTime = System.currentTimeMillis();

            ExecutorService executor = Executors.newFixedThreadPool(parts);

            for (int i = 0; i < parts; i++) {

                ClientInfo owner = owners.get(i);

                long start = i * chunkSize;
                long end = (i == parts - 1) ? totalFileSize - 1 : start + chunkSize - 1;

                partSizeArr[i] = end - start + 1;

                partStartTime[i] = System.currentTimeMillis();

                int partNumber = i;

                executor.submit(() ->
                        downloadPart(owner, fileName, start, end, partNumber));
            }

            // UI Thread
            Thread ui = new Thread(() -> {
                while (downloading) {
                    printProgress();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {}
                }
                printProgress();
            });

            ui.start();

            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

            downloading = false;
            ui.join();

            mergeParts(fileName, parts);

            long totalTime = (System.currentTimeMillis() - totalStartTime) / 1000;

            System.out.println("\nDownload completed");
            System.out.println("Total download time: " + formatTime(totalTime));

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

            DataOutputStream request = new DataOutputStream(socket.getOutputStream());

            request.writeUTF(fileName);
            request.writeLong(start);
            request.writeLong(end);
            request.flush();

            GZIPInputStream in = new GZIPInputStream(socket.getInputStream());

            FileOutputStream fos =
                    new FileOutputStream(DOWNLOAD_DIR + "/" + fileName + ".part" + part);

            byte[] buffer = new byte[4096];
            int read;

            while ((read = in.read(buffer)) != -1) {

                fos.write(buffer, 0, read);
                partProgress[part] += read;
            }

            fos.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printProgress() {

        int parts = partProgress.length;

        System.out.print("\033[H\033[2J");
        System.out.flush();

        for (int i = 0; i < parts; i++) {

            int barWidth = 20;

            double progress = (double) partProgress[i] / partSizeArr[i];
            int pos = (int) (barWidth * progress);

            long time = System.currentTimeMillis() - partStartTime[i];

            double speed = (time > 0)
                    ? (partProgress[i] / 1024.0 / 1024.0) / (time / 1000.0)
                    : 0;

            StringBuilder bar = new StringBuilder();

            bar.append("Part ").append(i).append(" [");

            for (int j = 0; j < barWidth; j++) {
                if (j < pos) bar.append("#");
                else bar.append("-");
            }

            int percent = (int) (progress * 100);

            bar.append("] ")
                    .append(percent)
                    .append("%  ")
                    .append(formatSize(partProgress[i]))
                    .append(" / ")
                    .append(formatSize(partSizeArr[i]))
                    .append("  ")
                    .append(String.format("%.2f MB/s", speed))
                    .append("  Time: ")
                    .append(formatTime(time / 1000));

            System.out.println(bar);
        }

        long totalTime = (System.currentTimeMillis() - totalStartTime) / 1000;
        System.out.println("\nTotal running time: " + formatTime(totalTime));
    }

    private static String formatSize(long bytes) {

        double kb = bytes / 1024.0;
        double mb = kb / 1024.0;

        if (mb >= 1) return String.format("%.2f MB", mb);
        if (kb >= 1) return String.format("%.2f KB", kb);

        return bytes + " B";
    }

    private static String formatTime(long seconds) {

        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;

        if (h > 0)
            return String.format("%02d:%02d:%02d", h, m, s);

        return String.format("%02d:%02d", m, s);
    }

    private static void mergeParts(String fileName, int parts) throws IOException {

        FileOutputStream output =
                new FileOutputStream(DOWNLOAD_DIR + "/download_" + fileName);

        for (int i = 0; i < parts; i++) {

            FileInputStream input =
                    new FileInputStream(DOWNLOAD_DIR + "/" + fileName + ".part" + i);

            byte[] buffer = new byte[4096];
            int read;

            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }

            input.close();

            new File(DOWNLOAD_DIR + "/" + fileName + ".part" + i).delete();
        }

        output.close();
    }
}