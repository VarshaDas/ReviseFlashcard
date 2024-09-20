package com.varshadas.demo;

import com.amazonaws.services.lambda.runtime.*;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FlashcardLambda implements RequestHandler<ScheduledEvent, String> {
    private static final Logger logger = LoggerFactory.getLogger(FlashcardLambda.class);

    private static final String BUCKET_NAME = System.getenv("S3_BUCKET_NAME");

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
        Twilio.init(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN);
        logger.info("Twilio initialized");

        try {
            AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion(AWS_REGION)
                    .build();
            logger.info("S3 client initialized");

            String todayFlagKey = getTodayProcessedFlagKey();
            if (!isSlidesPreprocessed(s3Client, todayFlagKey)) {
                String pptFileName = PPT_FILENAME;
                logger.info("Downloading file {} from bucket {}", pptFileName, BUCKET_NAME);

                File pptFile = downloadFileFromS3(s3Client, pptFileName);
                if (pptFile == null) {
                    return "Error: Failed to download PowerPoint file";
                }

                preprocessSlides(s3Client, pptFile, todayFlagKey);
                markSlidesAsProcessed(s3Client, todayFlagKey);
                cleanupPreviousDayProcessedSlides(s3Client);
            }

            // Process slides for the day
            processSlidesForTheDay(s3Client, todayFlagKey);

        } catch (Exception e) {
            logger.error("Error processing PowerPoint file: " + e.getMessage());
            return "Error: " + e.getMessage();
        }

        return "Messages sent successfully!";
    }

    private boolean isSlidesPreprocessed(AmazonS3 s3Client, String todayFlagKey) {
        try {
            s3Client.getObjectMetadata(BUCKET_NAME, todayFlagKey);
            logger.info("Slides already preprocessed for today");
            return true;
        } catch (Exception e) {
            logger.info("Slides not preprocessed yet for today");
            return false;
        }
    }

    private void markSlidesAsProcessed(AmazonS3 s3Client, String todayFlagKey) {
        try {
            s3Client.putObject(BUCKET_NAME, todayFlagKey, "processed");
            logger.info("Marked slides as processed for today");
        } catch (Exception e) {
            logger.error("Error marking slides as processed: " + e.getMessage());
        }
    }

    private String getTodayProcessedFlagKey() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMyy_HHmmss");
        return PROCESSED_FLAG_KEY_PREFIX + dateFormat.format(new Date());
    }


    private String getYesterdayProcessedFlagKey() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMyy");
        Calendar calendar = Calendar.getInstance();

        // Subtract 1 day from the current date
        calendar.add(Calendar.DATE, -1);

        // Format the date as "ddMMyy"
        String formattedDate = dateFormat.format(calendar.getTime());

        // Return the processed flag key for yesterday
        return PROCESSED_FLAG_KEY_PREFIX + formattedDate;
    }


    public void cleanupPreviousDayProcessedSlides(AmazonS3 s3Client) {
        logger.info("Cleaning up previous day's slides");
        String yesterdayProcessedFlagKey = getYesterdayProcessedFlagKey(); // Today's processed folder key
        String processedFolderPrefix = "images/"+ yesterdayProcessedFlagKey;

        try {
            ListObjectsV2Request listRequest = new ListObjectsV2Request()
                    .withBucketName(BUCKET_NAME)
                    .withPrefix(processedFolderPrefix);

            ListObjectsV2Result result;
            List<String> keysToDelete = new ArrayList<>();

            do {
                result = s3Client.listObjectsV2(listRequest);

                for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
                    String key = objectSummary.getKey();

                    // If the key doesn't match today's processed flag, mark it for deletion
                    if (!key.contains(yesterdayProcessedFlagKey)) {
                        keysToDelete.add(key);
                    }
                }

                listRequest.setContinuationToken(result.getNextContinuationToken());
            } while (result.isTruncated());

            // Delete all keys that don't belong to today's processed folder
            if (!keysToDelete.isEmpty()) {
                DeleteObjectsRequest deleteRequest = new DeleteObjectsRequest(BUCKET_NAME)
                        .withKeys(keysToDelete.toArray(new String[0]));
                s3Client.deleteObjects(deleteRequest);
                logger.info("Deleted old processed slides: " + keysToDelete.size() + " files");
            } else {
                logger.info("No old slides to delete");
            }
        } catch (Exception e) {
            logger.error("Error cleaning up old processed slides: " + e.getMessage());
        }
    }

    private File downloadFileFromS3(AmazonS3 s3Client, String fileName) {
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


    private List<Integer> selectRandomSlides(int totalSlides, int count) {
        List<Integer> indices = IntStream.range(0, totalSlides)
                .boxed()
                .collect(Collectors.toList());
        Collections.shuffle(indices);
        return indices.stream().limit(count).collect(Collectors.toList());
    }

    private void processSlidesForTheDay(AmazonS3 s3Client, String todayFlagKey) {
        List<Integer> selectedIndices = readIndicesFromTextFile(s3Client, todayFlagKey);

        List<Integer> selectedIndicesForTransaction = selectedIndices.stream().limit(SLIDES_PER_MESSAGE).collect(Collectors.toList());
        logger.info("selected indices to process for the day are - {}", selectedIndices);

        for (int index : selectedIndicesForTransaction) {
            String imageKey = "images/" + todayFlagKey + "/slide_" + index + ".png";
            sendWhatsAppMessage(imageKey);
        }
    }

    private File convertSlideToImage(XSLFSlide slide) throws IOException {
        BufferedImage img = new BufferedImage(960, 540, BufferedImage.TYPE_INT_ARGB);
        slide.draw(img.createGraphics());
        File imageFile = File.createTempFile("slide", ".png");
        ImageIO.write(img, "png", imageFile);
        logger.info("Converted slide to image: {}", imageFile.getAbsolutePath());
        return imageFile;
    }

    private void uploadFileToS3(AmazonS3 s3Client, String bucketName, String key, File file) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.length());
        try (InputStream is = new FileInputStream(file)) {
            s3Client.putObject(new PutObjectRequest(bucketName, key, is, metadata));
            logger.info("Uploaded file to S3: s3://{}/{}", bucketName, key);
        } catch (IOException e) {
            logger.error("Error uploading file to S3: " + e.getMessage());
        }
    }

    private void sendWhatsAppMessage(String imageKey) {
        String mediaUrl = generateS3Url(BUCKET_NAME, imageKey);

        logger.info("Sending WhatsApp message");
        try {
            Message.creator(
                            new PhoneNumber(TO_WHATSAPP_NUMBER),
                            new PhoneNumber(FROM_WHATSAPP_NUMBER),
                            "Here is your flashcard for today!")
                    .setMediaUrl(mediaUrl)
                    .create();
            logger.info("WhatsApp message sent successfully with media URL: {}", mediaUrl);
        } catch (ApiException e) {
            logger.error("Twilio API exception: " + e.getMessage());
        }
    }

    private void preprocessSlides(AmazonS3 s3Client, File pptFile, String todayFlagKey) {
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
            uploadTextToS3(s3Client, BUCKET_NAME, indicesFileName, indicesString);
            logger.info("Stored indices in file {}", indicesFileName);

            for (int index : selectedIndices) {
                XSLFSlide slide = slides.get(index);
                File imageFile = convertSlideToImage(slide);

                String imageKey = "images/" + todayFlagKey + "/slide_" + index + ".png";
                uploadFileToS3(s3Client, BUCKET_NAME, imageKey, imageFile);
                logger.info("Uploaded file to S3 {}", imageKey);
            }

        } catch (IOException e) {
            logger.error("Error preprocessing slides: " + e.getMessage());
        }
    }

    private void uploadTextToS3(AmazonS3 s3Client, String bucketName, String key, String text) {
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


    private String generateS3Url(String bucketName, String key) {
        return String.format("https://%s.s3.amazonaws.com/%s", bucketName, key);
    }

    public static void main(String[] args) {
        FlashcardLambda handler = new FlashcardLambda();
        ScheduledEvent event = new ScheduledEvent(); // Create a mock ScheduledEvent
        Context context = new TestContext(); // Create a mock Context if necessary
        String result = handler.handleRequest(event, context);
        System.out.println(result);
    }

    // Mock Context class for testing purposes
    static class TestContext implements Context {
        @Override
        public String getAwsRequestId() {
            return "mock-request-id";
        }

        @Override
        public String getLogGroupName() {
            return "mock-log-group";
        }

        @Override
        public String getLogStreamName() {
            return "mock-log-stream";
        }

        @Override
        public String getFunctionName() {
            return "mock-function-name";
        }

        @Override
        public String getFunctionVersion() {
            return "mock-function-version";
        }

        @Override
        public String getInvokedFunctionArn() {
            return "mock-function-arn";
        }

        @Override
        public CognitoIdentity getIdentity() {
            return null;
        }

        @Override
        public ClientContext getClientContext() {
            return null;
        }

        @Override
        public int getRemainingTimeInMillis() {
            return 30000;
        }

        @Override
        public int getMemoryLimitInMB() {
            return 512;
        }

        @Override
        public LambdaLogger getLogger() {
            return new LambdaLogger() {
                @Override
                public void log(String message) {
                    System.out.println(message); // Redirect Lambda logger output to console
                }

                @Override
                public void log(byte[] message) {
                    System.out.println(new String(message)); // Redirect Lambda logger output to console
                }
            };
        }
    }
}
