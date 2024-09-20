package com.varshadas.demo.utils;

import org.apache.poi.xslf.usermodel.XSLFSlide;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;


public class SlideImageConverter {

    public File convertSlideToImage(XSLFSlide slide) throws IOException {
        BufferedImage img = new BufferedImage(960, 540, BufferedImage.TYPE_INT_ARGB);
        slide.draw(img.createGraphics());
        File imageFile = File.createTempFile("slide", ".png");
        ImageIO.write(img, "png", imageFile);
        return imageFile;
    }
}


