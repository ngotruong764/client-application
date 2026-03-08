package dto;

import model.FileInfo;

import java.io.File;

public class FileInfoDto {
    /**
     * Method used to convert File to FileInfo object
     */
    public static FileInfo toFileInfo(File file) {
        String fileName = file.getName();
        String filePath = file.getPath();
        long fileSize = file.length();

        return new FileInfo(fileSize, null, filePath, fileName);
    }
}
