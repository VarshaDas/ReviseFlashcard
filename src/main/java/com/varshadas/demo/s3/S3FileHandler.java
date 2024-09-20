package com.varshadas.demo.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;


public class S3FileHandler {
    private static final Logger logger = LoggerFactory.getLogger(S3FileHandler.class);

    private static final String BUCKET_NAME = System.getenv("S3_BUCKET_NAME");
    private static final String PROCESSED_FLAG_KEY_PREFIX = "processed_flag_";

    public boolean isSlidesPreprocessed(AmazonS3 s3Client, String todayFlagKey) {
        try {
            s3Client.getObjectMetadata(BUCKET_NAME, todayFlagKey);
            logger.info("Slides already preprocessed for today");
            return true;
        } catch (Exception e) {
            logger.info("Slides not preprocessed yet for today");
            return false;
        }
    }

    public void markSlidesAsProcessed(AmazonS3 s3Client, String todayFlagKey) {
        try {
            s3Client.putObject(BUCKET_NAME, todayFlagKey, "processed");
            logger.info("Marked slides as processed for today");
        } catch (Exception e) {
            logger.error("Error marking slides as processed: " + e.getMessage());
        }
    }

    public String getTodayProcessedFlagKey() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMyy_HHmmss");
        return PROCESSED_FLAG_KEY_PREFIX + dateFormat.format(new Date());
    }

    public void uploadFileToS3(AmazonS3 s3Client, String bucketName, String key, File file) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.length());
        try (InputStream is = new FileInputStream(file)) {
            s3Client.putObject(new PutObjectRequest(bucketName, key, is, metadata));
            logger.info("Uploaded file to S3: s3://{}/{}", bucketName, key);
        } catch (IOException e) {
            logger.error("Error uploading file to S3: " + e.getMessage());
        }
    }

    public void uploadTextFileToS3(AmazonS3 s3Client, String bucketName, String key, String text) {
        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            s3Client.putObject(bucketName, key, inputStream, metadata);
        } catch (Exception e) {
            logger.error("Error uploading text to S3: " + e.getMessage());
        }
    }

    public File downloadFileFromS3(AmazonS3 s3Client, String fileName) {
        try {
            File tempFile = File.createTempFile(fileName, ".tmp");
            s3Client.getObject(new GetObjectRequest(BUCKET_NAME, fileName), tempFile);
            logger.info("File downloaded to {}", tempFile.getAbsolutePath());
            return tempFile;
        } catch (IOException e) {
            logger.error("Error downloading file from S3: " + e.getMessage());
            return null;
        }
    }
}


