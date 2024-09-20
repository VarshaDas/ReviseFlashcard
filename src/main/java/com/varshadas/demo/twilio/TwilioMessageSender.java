package com.varshadas.demo.twilio;

import com.twilio.Twilio;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.twilio.rest.api.v2010.account.Message;



    public class TwilioMessageSender {
        private static final Logger logger = LoggerFactory.getLogger(TwilioMessageSender.class);

        private static final String FROM_WHATSAPP_NUMBER = "whatsapp:" + System.getenv("TWILIO_WHATSAPP_NUMBER");
        private static final String TO_WHATSAPP_NUMBER = "whatsapp:" + System.getenv("TO_WHATSAPP_NUMBER");

        public TwilioMessageSender() {
            Twilio.init(System.getenv("TWILIO_ACCOUNT_SID"), System.getenv("TWILIO_AUTH_TOKEN"));
        }

        public void sendWhatsAppMessage(String mediaUrl) {
            logger.info("Sending WhatsApp message");
            try {
                Message.creator(
                                new PhoneNumber(TO_WHATSAPP_NUMBER),
                                new PhoneNumber(FROM_WHATSAPP_NUMBER),
                                "Here is your flashcard for today!")
                        .setMediaUrl(mediaUrl)
                        .create();
                logger.info("WhatsApp message sent successfully with media URL: {}", mediaUrl);
            } catch (Exception e) {
                logger.error("Error sending WhatsApp message: " + e.getMessage());
            }
        }
    }


