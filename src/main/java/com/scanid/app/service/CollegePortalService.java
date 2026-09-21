package com.scanid.app.service;

import com.scanid.app.model.StudentProfile;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CollegePortalService {

    private static final String LOGIN_URL = "https://www.bvcecautonomous.com/SBLogin.aspx";

    public StudentProfile fetchStudentByRollNumber(String rollNumber) {
        String normalizedRoll = normalizeRoll(rollNumber);
        if (normalizedRoll == null || normalizedRoll.isBlank()) {
            throw new IllegalArgumentException("Roll number is required.");
        }

        try {
            Connection sessionConnection = Jsoup.connect(LOGIN_URL)
                    .userAgent("Mozilla/5.0")
                    .method(Connection.Method.GET)
                    .timeout(30000);

            Connection.Response loginPage = sessionConnection.execute();
            Document pageDoc = loginPage.parse();

            String viewState = getInputValue(pageDoc, "__VIEWSTATE");
            String viewStateGenerator = getInputValue(pageDoc, "__VIEWSTATEGENERATOR");
            String eventValidation = getInputValue(pageDoc, "__EVENTVALIDATION");

            Connection.Response loggedInResponse = Jsoup.connect(LOGIN_URL)
                    .userAgent("Mozilla/5.0")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .cookies(loginPage.cookies())
                    .data("__VIEWSTATE", viewState)
                    .data("__VIEWSTATEGENERATOR", viewStateGenerator)
                    .data("__EVENTVALIDATION", eventValidation)
                    .data("txtUserName", normalizedRoll)
                    .data("txtPassword", normalizedRoll)
                    .data("btnSubmit", "Login")
                    .data("__EVENTTARGET", "")
                    .data("__EVENTARGUMENT", "")
                    .data("__SCROLLPOSITIONX", "0")
                    .data("__SCROLLPOSITIONY", "0")
                    .method(Connection.Method.POST)
                    .timeout(30000)
                    .execute();

            Document postLoginDoc = loggedInResponse.parse();

            if (isLoginFailure(postLoginDoc)) {
                throw new IllegalArgumentException("The provided roll number could not be detected.");
            }

            StudentProfile profile = new StudentProfile();
            profile.setRollNumber(normalizedRoll);
            profile.setCollegeName("BVC Group of Institutions (Autonomous)");
            profile.setStatus("Logged in successfully via portal credentials");
            profile.setLastLogin("Auto captured from portal");

            String rollLabel = textOf(postLoginDoc, "#lblHTNo");
            if (rollLabel != null && !rollLabel.isBlank()) {
                profile.setRollNumber(rollLabel.trim());
            }

            profile.setName(extractByPatterns(postLoginDoc, List.of("#lblStudentName", "#Stud_lblName", "#ctl00_Stud_lblName", "#lblName")));
            profile.setDepartment(extractByPatterns(postLoginDoc, List.of("#lblDepartment", "#Stud_lblDepartment", "#ctl00_Stud_lblDepartment", "#lblDept")));
            profile.setClassName(extractByPatterns(postLoginDoc, List.of("#lblClass", "#Stud_lblClass", "#ctl00_Stud_lblClass", "#lblCourse")));
            profile.setSection(extractByPatterns(postLoginDoc, List.of("#lblSection", "#Stud_lblSection", "#ctl00_Stud_lblSection")));
            profile.setYear(extractByPatterns(postLoginDoc, List.of("#lblYear", "#Stud_lblYear", "#ctl00_Stud_lblYear")));
            profile.setProfileImageUrl(extractImage(postLoginDoc, "#imgStudUser"));

            profile.setModules(findModules(postLoginDoc));

            if (profile.getName() == null || profile.getName().isBlank()) {
                profile.setName("Student");
            }
            if (profile.getClassName() == null || profile.getClassName().isBlank()) {
                profile.setClassName("N/A");
            }
            if (profile.getDepartment() == null || profile.getDepartment().isBlank()) {
                profile.setDepartment("N/A");
            }
            if (profile.getSection() == null || profile.getSection().isBlank()) {
                profile.setSection("N/A");
            }
            if (profile.getYear() == null || profile.getYear().isBlank()) {
                profile.setYear("N/A");
            }

            return profile;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to reach the college portal right now. Please try again in a moment.", e);
        }
    }

    private String normalizeRoll(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.toUpperCase(Locale.ROOT);
    }

    private boolean isLoginFailure(Document document) {
        String bodyText = document.body() != null ? document.body().text() : "";
        String lower = bodyText.toLowerCase(Locale.ROOT);
        return lower.contains("invalid") || lower.contains("incorrect") || lower.contains("login failed") || lower.contains("password") && lower.contains("not") && lower.contains("match");
    }

    private String getInputValue(Document document, String name) {
        Element input = document.selectFirst("input[name=" + name + "]");
        if (input == null) {
            return "";
        }
        return input.attr("value");
    }

    private String extractByPatterns(Document document, List<String> selectors) {
        for (String selector : selectors) {
            String text = textOf(document, selector);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        Elements labels = document.select("span, td, div, label");
        for (Element label : labels) {
            String text = label.text();
            if (text != null && text.length() > 2 && text.matches("(?i).*(name|department|year|section|class|course).*")) {
                return text;
            }
        }
        return "N/A";
    }

    private String textOf(Document document, String selector) {
        if (selector == null || selector.isBlank()) {
            return null;
        }
        Element el = document.selectFirst(selector);
        if (el == null) {
            return null;
        }
        return el.text().trim();
    }

    private String extractImage(Document document, String selector) {
        Element image = document.selectFirst(selector);
        if (image == null) {
            return "";
        }
        return image.attr("src");
    }

    private List<String> findModules(Document document) {
        List<String> modules = new ArrayList<>();
        Pattern p = Pattern.compile("(?i)(STUDENT-INFO|ONLINE-FEE|ACADEMICS|EXAMINATIONS|PLACEMENTS|MORE)");
        Elements inputs = document.select("input[type=image]");
        for (Element input : inputs) {
            String src = input.attr("src");
            if (src != null && !src.isBlank()) {
                Matcher m = p.matcher(src);
                if (m.find()) {
                    modules.add(m.group(0).toUpperCase());
                }
            }
        }
        return modules;
    }
}
