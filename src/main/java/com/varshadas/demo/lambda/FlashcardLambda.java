package com.varshadas.demo.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.varshadas.demo.s3.S3CleanupService;
import com.varshadas.demo.s3.S3FileHandler;
import com.varshadas.demo.twilio.TwilioMessageSender;
import com.varshadas.demo.utils.SlidePreprocessor;

import java.io.File;


public class FlashcardLambda implements RequestHandler<ScheduledEvent, String> {

        private static final Logger logger = LoggerFactory.getLogger(FlashcardLambda.class);

        private final S3FileHandler s3FileHandler = new S3FileHandler();
        private final SlidePreprocessor slidePreprocessor = new SlidePreprocessor();
        private final S3CleanupService cleanupService = new S3CleanupService();
        private final TwilioMessageSender messageSender = new TwilioMessageSender();

    private static final String AWS_REGION = System.getenv("AWS_REGION");

    private static final String TWILIO_ACCOUNT_SID = System.getenv("TWILIO_ACCOUNT_SID");
    private static final String TWILIO_AUTH_TOKEN = System.getenv("TWILIO_AUTH_TOKEN");

    private static final String PPT_FILENAME = System.getenv("PPT_FILENAME");

    private static final String FROM_WHATSAPP_NUMBER = "whatsapp:" + System.getenv("TWILIO_WHATSAPP_NUMBER");
    private static final String TO_WHATSAPP_NUMBER = "whatsapp:" + System.getenv("TO_WHATSAPP_NUMBER");
    private static final int SLIDES_PER_DAY = Integer.parseInt(System.getenv("SLIDES_PER_DAY"));
    private static final int SLIDES_PER_MESSAGE = Integer.parseInt(System.getenv("SLIDES_PER_MESSAGE"));
    private static final String PROCESSED_FLAG_KEY_PREFIX = "processed_flag_";


    @Override
        public String handleRequest(ScheduledEvent event, Context context) {
            logger.info("event = {}", event);

            try {
                AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();
                String todayFlagKey = s3FileHandler.getTodayProcessedFlagKey();

                if (!s3FileHandler.isSlidesPreprocessed(s3Client, todayFlagKey)) {
                    String pptFileName = PPT_FILENAME;
                    logger.info("Downloading file {} from bucket", pptFileName);

                    File pptFile = s3FileHandler.downloadFileFromS3(s3Client, pptFileName);
                    if (pptFile == null) {
                        return "Error: Failed to download PowerPoint file";
                    }
                    // Download the PPT and preprocess slides
                    slidePreprocessor.preprocessSlides(s3Client, pptFile, todayFlagKey);
                    s3FileHandler.markSlidesAsProcessed(s3Client, todayFlagKey);
                    cleanupService.cleanupPreviousDayProcessedSlides(s3Client);
                }

                // Process slides for the day
                slidePreprocessor.processSlidesForTheDay(s3Client, todayFlagKey);

            } catch (Exception e) {
                logger.error("Error processing PowerPoint file: " + e.getMessage());
                return "Error: " + e.getMessage();
            }

            return "Messages sent successfully!";
        }
    }


