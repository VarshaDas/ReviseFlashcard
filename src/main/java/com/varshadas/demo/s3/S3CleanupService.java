package com.varshadas.demo.s3;

import com.amazonaws.services.s3.AmazonS3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

    public class S3CleanupService {

        private static final Logger logger = LoggerFactory.getLogger(S3CleanupService.class);
        private static final String BUCKET_NAME = System.getenv("S3_BUCKET_NAME");

        public void cleanupPreviousDayProcessedSlides(AmazonS3 s3Client) {
            logger.info("Cleaning up previous day's slides");
            // Cleanup logic here...
        }
    }


