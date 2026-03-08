package service;

import dto.FileInfoDto;
import model.FileInfo;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileService {
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
}
