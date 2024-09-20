package com.varshadas.demo.utils;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.varshadas.demo.s3.S3FileHandler;
import com.varshadas.demo.twilio.TwilioMessageSender;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class SlidePreprocessor {
    private static final Logger logger = LoggerFactory.getLogger(SlidePreprocessor.class);

    private final SlideImageConverter imageConverter = new SlideImageConverter();
    private final S3FileHandler s3FileHandler = new S3FileHandler();

    private final TwilioMessageSender twilioMessageSender = new TwilioMessageSender();

    private static final String BUCKET_NAME = System.getenv("S3_BUCKET_NAME");

    private static final int SLIDES_PER_DAY = Integer.parseInt(System.getenv("SLIDES_PER_DAY"));

    private static final int SLIDES_PER_MESSAGE = Integer.parseInt(System.getenv("SLIDES_PER_MESSAGE"));




    public void preprocessSlides(AmazonS3 s3Client, File pptFile, String todayFlagKey) {
        try (FileInputStream fis = new FileInputStream(pptFile);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {

            List<XSLFSlide> slides = ppt.getSlides();
            List<Integer> selectedIndices = selectRandomSlides(slides.size(), SLIDES_PER_DAY);

            logger.info("selected indices to PRE-PROCESS for the day are - {}", selectedIndices);

            String indicesString = selectedIndices.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(","));

            // Write indices to a text file
            String indicesFileName = "indices/" + todayFlagKey + "/processed_indices.txt";
            s3FileHandler.uploadTextFileToS3(s3Client, BUCKET_NAME, indicesFileName, indicesString);
            logger.info("Stored indices in file {}", indicesFileName);

            for (int index : selectedIndices) {
                XSLFSlide slide = slides.get(index);
                File imageFile = imageConverter.convertSlideToImage(slide);

                String imageKey = "images/" + todayFlagKey + "/slide_" + index + ".png";
                s3FileHandler.uploadFileToS3(s3Client, BUCKET_NAME, imageKey, imageFile);
                logger.info("Uploaded file to S3 {}", imageKey);
            }

        } catch (IOException e) {
            logger.error("Error preprocessing slides: " + e.getMessage());
        }
    }


    public void processSlidesForTheDay(AmazonS3 s3Client, String todayFlagKey) {
        List<Integer> selectedIndices = readIndicesFromTextFile(s3Client, todayFlagKey);

        List<Integer> selectedIndicesForTransaction = selectedIndices.stream().limit(SLIDES_PER_MESSAGE).collect(Collectors.toList());
        logger.info("selected indices to process for the day are - {}", selectedIndices);

        for (int index : selectedIndicesForTransaction) {
            String imageKey = "images/" + todayFlagKey + "/slide_" + index + ".png";

            String mediaUrl = generateS3Url(BUCKET_NAME, imageKey);

            twilioMessageSender.sendWhatsAppMessage(mediaUrl);
        }
    }

    private String generateS3Url(String bucketName, String imageKey) {
            return String.format("https://%s.s3.amazonaws.com/%s", bucketName, imageKey);
        }



    private List<Integer> selectRandomSlides(int totalSlides, int count) {
        List<Integer> indices = IntStream.range(0, totalSlides)
                .boxed()
                .collect(Collectors.toList());
        Collections.shuffle(indices);
        return indices.stream().limit(count).collect(Collectors.toList());
    }



    private List<Integer> readIndicesFromTextFile(AmazonS3 s3Client, String todayFlagKey) {
        List<Integer> indices = new ArrayList<>();
        try {
            String indicesFileName = "indices/" + todayFlagKey + "/processed_indices.txt";
            S3Object object = s3Client.getObject(BUCKET_NAME, indicesFileName);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(object.getObjectContent()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    for (String part : parts) {
                        indices.add(Integer.parseInt(part.trim()));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error reading indices from file: " + e.getMessage());
        }
        return indices;
    }
}


