package com.dmigouno;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.xml.bind.DatatypeConverter;

class ReplicationVerifier {
    static final Logger LOGGER = LoggerFactory.getLogger(ReplicationVerifier.class);

    public static void main(String args[]) {
        String src = "tmp/src";
        String dest = "tmp/dest";
        Path srcDir = Paths.get(src);
        Path destDir = Paths.get(dest);

        createDirs();
        createFile("tmp/src/file1");
        createFile("tmp/src/file2");
        createFile("tmp/src/dir1/file3");
        createFile("tmp/src/dir1/file4");

        /* For unsuccessful replication comment the next line line out.
         If you're feeling really frisky, try deleting some files in the destination folder
         and then re-running the app.*/
        copyDir(src, dest);

        Map<Path, Map<String, String>> srcFiles = getDirContents(srcDir);
        Map<Path, Map<String, String>> destFiles = getDirContents(destDir);

        boolean dirsAreEqual = compareDirs(srcFiles, destFiles);
        if (dirsAreEqual) {
            System.out.println("All files are replicated!");
        } else {
            System.out.println("Error replicating files!");
        }
    }

    private static void createDirs() {
        Path path = Paths.get("tmp\\src\\dir1\\..\\..\\dest");
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    private static void createFile(String file) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            byte[] bytes = new byte[new Random().nextInt(1000000)];
            new SecureRandom().nextBytes(bytes);
            out.write(bytes);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private static void copyDir(String src, String dest) {
        try {
            FileUtils.copyDirectory(new File(src), new File(dest));
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private static Map<Path, Map<String, String>> getDirContents(Path path) {
        try {
            return Files.walk(path)
                    .collect(Collectors.toMap(p -> path.relativize(p), ReplicationVerifier::getFileAttrs));
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }

    private static boolean compareDirs(Map<Path, Map<String, String>> srcFiles,
                                       Map<Path, Map<String, String>> destFiles) {
        boolean areEqual = true;
        for (Map.Entry<Path, Map<String, String>> entry : srcFiles.entrySet()) {
            Path fileName = entry.getKey();
            Map<String, String> srcFileAttrs = entry.getValue();
            Map<String, String> destFileAttrs = destFiles.get(fileName);
            if (destFileAttrs == null) {
                System.out.printf("Source file \"%s\" does not exist in the destination directory!%n", fileName);
                areEqual = false;
            } else {
                for (Map.Entry<String, String> attrList : srcFileAttrs.entrySet()) {
                    String srcAttributeName = attrList.getKey();
                    String srcAttributeValue = attrList.getValue();
                    String destAttributeValue = destFileAttrs.get(srcAttributeName);
                    if (destAttributeValue == null) {
                        System.out.printf("Target file \"%s\" is missing attribute \"%s\"!%n",
                                                        fileName, srcAttributeName);
                        areEqual = false;
                    } else if (!destAttributeValue.equals(srcAttributeValue)) {
                        System.out.printf("Attribute \"%s\" for file \"%s\" does not match!%n",
                                                        srcAttributeName, fileName);
                        areEqual = false;
                    }
                }
            }
        }
        return areEqual;
    }

    private static Map<String, String> getFileAttrs(Path path) {
        DosFileAttributes attrs = null;
        long size = 0;
        String checkSum = "";

        try {
            attrs = Files.getFileAttributeView(path, DosFileAttributeView.class)
                    .readAttributes();
            size = Files.size(path);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

        if (attrs.isRegularFile()) {
            checkSum = calculateChecksum(path);
        }

        Map<String, String> attrList = new HashMap<>();

        if (attrs != null) {
            attrList.put("Checksum", checkSum);
            attrList.put("Size", String.valueOf(size));
            attrList.put("Dir", String.valueOf(attrs.isDirectory()));
            attrList.put("File", String.valueOf(attrs.isRegularFile()));
            attrList.put("Read-only", String.valueOf(attrs.isReadOnly()));
        }
        return attrList;
    }

    private static String calculateChecksum(Path path) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error(e.getMessage(), e);
        }
        try {
            md.update(Files.readAllBytes(path));
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
        byte[] digest = md.digest();
        return DatatypeConverter.printHexBinary(digest).toUpperCase();
    }
}