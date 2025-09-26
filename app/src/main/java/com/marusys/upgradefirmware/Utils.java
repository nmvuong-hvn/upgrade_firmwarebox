package com.marusys.upgradefirmware;


import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Created by amitshekhar on 13/11/17.
 */

public final class Utils {

    private final static int MAX_REDIRECTION = 10;

    private Utils() {
        // no instance
    }

    public static String getPath(String dirPath, String fileName) {
        return dirPath + File.separator + fileName;
    }

    public static String getTempPath(String dirPath, String fileName) {
        return getPath(dirPath, fileName) + ".temp";
    }

    public static void renameFileName(String oldPath, String newPath) throws IOException {
        final File oldFile = new File(oldPath);
        try {
            final File newFile = new File(newPath);
            if (newFile.exists()) {
                if (!newFile.delete()) {
                    throw new IOException("Deletion Failed");
                }
            }
            if (!oldFile.renameTo(newFile)) {
                throw new IOException("Rename Failed");
            }
        } finally {
            if (oldFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                oldFile.delete();
            }
        }
    }

    public static int getUniqueId(String url, String dirPath, String fileName) {

        String string = url + File.separator + dirPath + File.separator + fileName;

        byte[] hash;

        try {
            hash = MessageDigest.getInstance("MD5").digest(string.getBytes("UTF-8"));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("NoSuchAlgorithmException", e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UnsupportedEncodingException", e);
        }

        StringBuilder hex = new StringBuilder(hash.length * 2);

        for (byte b : hash) {
            if ((b & 0xFF) < 0x10) hex.append("0");
            hex.append(Integer.toHexString(b & 0xFF));
        }

        return hex.toString().hashCode();

    }

}
