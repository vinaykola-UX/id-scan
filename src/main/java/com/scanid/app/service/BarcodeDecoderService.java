package com.scanid.app.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

@Service
public class BarcodeDecoderService {

    public String decodeRollNumber(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            return null;
        }

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        try {
            Result result = new MultiFormatReader().decode(bitmap);
            if (result == null || result.getText() == null) {
                return null;
            }
            return result.getText().trim();
        } catch (NotFoundException e) {
            return null;
        }
    }
}
