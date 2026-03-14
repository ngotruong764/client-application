package service;

import dto.FileInfoDto;
import model.FileInfo;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileService {
    private static final Logger logger = Logger.getLogger(FileService.class.getName());
    /**
     * Get all available files in a directory
     * @param path is the path of the directory where the files located
     */
    public List<FileInfo> getAllFiles(String path){
        List<FileInfo> files = new ArrayList<>();

        File dir = new File(path);
        // If the path is not directory => throw exception
        if (!dir.isDirectory() || !dir.exists()) {
            throw new IllegalArgumentException("Path is not a directory");
        }
        if (dir.listFiles() == null) {
            throw new IllegalArgumentException("No files found");
        }

        // If it is a dir
        for (File file : dir.listFiles()) {
            files.add(FileInfoDto.toFileInfo(file));
        }
        return files;
    }

    /**
     * Method used to read N bytes from a file
     * @param fileName file we want to read
     * @param offSet position in the file we want to read from
     * @param buffer number of bytes we want to read
     * @return array of bytes we read from the file
     *         null if the file does not exist, reach the end of the file or exception occurs
     */
    public byte[] fileToByte(String fileName, int offSet, int buffer){
        byte[] bytes = new byte[buffer];
        File file = new File(fileName);
        if (!file.exists() || !file.isFile()) {
            logger.info("File " + fileName + " does not exist");
            return null;
        }
        try(FileInputStream fileInputStream = new FileInputStream(file)) {
            int readBytes = fileInputStream.read(bytes, offSet, buffer);
            if (readBytes == -1) {
                logger.info("The end of the stream has been reached.");
                return null;
            }
        } catch (Exception e) {
            logger.log(Level.FINER, e.toString());
            return null;
        }
        return bytes;
    }
}
