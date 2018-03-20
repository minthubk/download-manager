package com.novoda.downloadmanager;

import java.io.File;

final class MigrationPathExtractor {

    private static final String PATH_SEPARATOR = "/";
    private static final String EMPTY = "";

    static FilePath extractMigrationPath(String basePath, String assetPath, DownloadBatchId downloadBatchId) {
        String relativePath = extractRelativePath(basePath, assetPath);
        String relativePathWithBatchId = prependBatchIdTo(relativePath, downloadBatchId);
        String fileName = extractFileName(assetPath);
        String absolutePath = basePath + PATH_SEPARATOR + relativePathWithBatchId + fileName;
        String sanitizedAbsolutePath = absolutePath.replaceAll("//", "/");
        return new LiteFilePath(sanitizedAbsolutePath);
    }

    private static String extractRelativePath(String rootUrlPath, String assetUrl) {
        String subPathWithFileName = removeSubstring(assetUrl, rootUrlPath);
        String fileName = extractFileName(subPathWithFileName);
        return removeSubstring(subPathWithFileName, fileName);
    }

    private static String prependBatchIdTo(String filePath, DownloadBatchId downloadBatchId) {
        return sanitizeBatchIdPath(downloadBatchId.rawId()) + File.separatorChar + filePath;
    }

    private static String sanitizeBatchIdPath(String batchIdPath) {
        return batchIdPath.replaceAll("[:\\\\/*?|<>]", "_");
    }

    private static String extractFileName(String assetUri) {
        String[] subPaths = assetUri.split(PATH_SEPARATOR);
        return subPaths.length == 0 ? assetUri : subPaths[subPaths.length - 1];
    }

    private static String removeSubstring(String source, String subString) {
        return source.replaceAll(subString, EMPTY);
    }

}
