package com.scanid.app.controller;

import com.scanid.app.model.StudentProfile;
import com.scanid.app.service.BarcodeDecoderService;
import com.scanid.app.service.CollegePortalService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Controller
public class ScanIdController {

    private final CollegePortalService collegePortalService;
    private final BarcodeDecoderService barcodeDecoderService;

    public ScanIdController(CollegePortalService collegePortalService, BarcodeDecoderService barcodeDecoderService) {
        this.collegePortalService = collegePortalService;
        this.barcodeDecoderService = barcodeDecoderService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("student", null);
        model.addAttribute("pageTitle", "Scan ID");
        return "index";
    }

    @PostMapping("/scan")
    public String scan(@RequestParam(value = "barcodeFile", required = false) MultipartFile barcodeFile,
                       @RequestParam(value = "manualRoll", required = false) String manualRoll,
                       Model model) {
        try {
            String extractedRoll = null;

            if (barcodeFile != null && !barcodeFile.isEmpty()) {
                extractedRoll = barcodeDecoderService.decodeRollNumber(barcodeFile);
            }

            if ((extractedRoll == null || extractedRoll.isBlank()) && manualRoll != null && !manualRoll.isBlank()) {
                extractedRoll = manualRoll.trim();
            }

            if (extractedRoll == null || extractedRoll.isBlank()) {
                model.addAttribute("error", "No barcode could be detected. Please allow camera access or enter the roll number manually.");
                model.addAttribute("pageTitle", "Scan ID");
                return "index";
            }

            StudentProfile student = collegePortalService.fetchStudentByRollNumber(extractedRoll);
            model.addAttribute("student", student);
            model.addAttribute("pageTitle", "Student Details");
            model.addAttribute("rawRollNumber", extractedRoll.toUpperCase(Locale.ROOT));
            return "index";
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("pageTitle", "Scan ID");
            return "index";
        } catch (Exception e) {
            model.addAttribute("error", "Unable to scan the barcode. Please use a clear image of the ID card or enter the roll number manually.");
            model.addAttribute("pageTitle", "Scan ID");
            return "index";
        }
    }

}
