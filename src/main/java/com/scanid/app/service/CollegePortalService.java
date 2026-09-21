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
    private static final String PROFILE_URL = "https://www.bvcecautonomous.com/STUDENTLOGIN/Frm_StudentProfile.aspx";

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

            Document profileDoc = fetchProfilePage(loggedInResponse);
            Document detailsDoc = profileDoc != null ? profileDoc : postLoginDoc;

            StudentProfile profile = new StudentProfile();
            profile.setRollNumber(normalizedRoll);
            profile.setCollegeName("BVC Group of Institutions (Autonomous)");
            profile.setStatus("Logged in successfully via portal credentials");
            profile.setLastLogin("Auto captured from portal");

            String rollLabel = firstValue(profileDoc, postLoginDoc, "#lblHTNo", "#Stud_lblHTNo", "#ctl00_Stud_lblHTNo");
            if (rollLabel != null && !rollLabel.isBlank()) {
                profile.setRollNumber(rollLabel.trim());
            }

            profile.setName(extractByPatterns(detailsDoc, List.of("#lblStudentName", "#Stud_lblName", "#ctl00_Stud_lblName", "#lblName")));
            profile.setGender(extractProfileValue(profileDoc, postLoginDoc, List.of("#lblGender", "#Stud_lblGender", "#ctl00_Stud_lblGender", "#lblSex"), "gender|sex"));
            profile.setDateOfBirth(extractProfileValue(profileDoc, postLoginDoc, List.of("#lblDOB", "#Stud_lblDOB", "#ctl00_Stud_lblDOB", "#lblDateOfBirth"), "date.?of.?birth|dob"));
            profile.setCaste(extractProfileValue(profileDoc, postLoginDoc, List.of("#lblCaste", "#Stud_lblCaste", "#ctl00_Stud_lblCaste"), "caste|community"));
            profile.setReligion(extractProfileValue(profileDoc, postLoginDoc, List.of("#lblReligion", "#Stud_lblReligion", "#ctl00_Stud_lblReligion"), "religion"));
            profile.setParentName(extractProfileValue(profileDoc, postLoginDoc, List.of("#lblParentName", "#Stud_lblParentName", "#ctl00_Stud_lblParentName", "#lblFatherName"), "father.?name|parent.?name|guardian.?name"));
            profile.setPhoneNumber(extractProfileValue(profileDoc, postLoginDoc, List.of("#lblMobile", "#Stud_lblMobile", "#ctl00_Stud_lblMobile", "#lblPhone"), "mobile|phone|contact"));
            profile.setEmail(extractProfileValue(profileDoc, postLoginDoc, List.of("#lblEmail", "#Stud_lblEmail", "#ctl00_Stud_lblEmail"), "email|e.?mail"));
            profile.setAddress(extractProfileValue(profileDoc, postLoginDoc, List.of("#lblAddress", "#Stud_lblAddress", "#ctl00_Stud_lblAddress"), "address"));
            profile.setDepartment(extractByPatterns(detailsDoc, List.of("#lblDepartment", "#Stud_lblDepartment", "#ctl00_Stud_lblDepartment", "#lblDept")));
            profile.setClassName(extractByPatterns(detailsDoc, List.of("#lblClass", "#Stud_lblClass", "#ctl00_Stud_lblClass", "#lblCourse")));
            profile.setSection(extractByPatterns(detailsDoc, List.of("#lblSection", "#Stud_lblSection", "#ctl00_Stud_lblSection")));
            profile.setYear(extractByPatterns(detailsDoc, List.of("#lblYear", "#Stud_lblYear", "#ctl00_Stud_lblYear")));
            profile.setProfileImageUrl(extractImage(detailsDoc, "#imgStudUser"));

            profile.setModules(findModules(postLoginDoc));

            if (profile.getName() == null || profile.getName().isBlank()) {
                profile.setName("Student");
            }
            profile.setGender(defaultValue(profile.getGender()));
            profile.setDateOfBirth(defaultValue(profile.getDateOfBirth()));
            profile.setCaste(defaultValue(profile.getCaste()));
            profile.setReligion(defaultValue(profile.getReligion()));
            profile.setParentName(defaultValue(profile.getParentName()));
            profile.setPhoneNumber(defaultValue(profile.getPhoneNumber()));
            profile.setEmail(defaultValue(profile.getEmail()));
            profile.setAddress(defaultValue(profile.getAddress()));
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

    private Document fetchProfilePage(Connection.Response loggedInResponse) throws IOException {
        return Jsoup.connect(PROFILE_URL)
                .userAgent("Mozilla/5.0")
                .cookies(loggedInResponse.cookies())
                .referrer(LOGIN_URL)
                .method(Connection.Method.GET)
                .timeout(30000)
                .execute()
                .parse();
    }

    private String firstValue(Document primary, Document fallback, String... selectors) {
        if (primary != null) {
            String value = textOfAny(primary, selectors);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return textOfAny(fallback, selectors);
    }

    private String textOfAny(Document document, String... selectors) {
        if (document == null) {
            return null;
        }
        for (String selector : selectors) {
            String value = textOf(document, selector);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String extractProfileValue(Document profileDoc, Document fallbackDoc, List<String> selectors, String labelPattern) {
        if (profileDoc != null) {
            String value = extractByPatterns(profileDoc, selectors, labelPattern);
            if (!"N/A".equals(value)) {
                return value;
            }
        }
        return extractByPatterns(fallbackDoc, selectors, labelPattern);
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
        return extractByPatterns(document, selectors, null);
    }

    private String extractByPatterns(Document document, List<String> selectors, String labelPattern) {
        if (document == null) {
            return "N/A";
        }
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            String text = valueOf(element);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        if (labelPattern != null) {
            Pattern labelRegex = Pattern.compile("(?i)" + labelPattern);
            for (Element label : document.select("th, td, span, label")) {
                String labelText = label.text().trim();
                if (!labelRegex.matcher(labelText).find() || labelContainsValue(labelText, labelRegex)) {
                    continue;
                }
                Element sibling = label.nextElementSibling();
                String siblingValue = valueOf(sibling);
                if (siblingValue != null && !siblingValue.isBlank() && !labelRegex.matcher(siblingValue).find()) {
                    return siblingValue;
                }
                Element parent = label.parent();
                if (parent != null) {
                    Elements siblings = parent.children();
                    int labelIndex = siblings.indexOf(label);
                    if (labelIndex >= 0 && labelIndex + 1 < siblings.size()) {
                        String nextValue = valueOf(siblings.get(labelIndex + 1));
                        if (nextValue != null && !nextValue.isBlank() && !labelRegex.matcher(nextValue).find()) {
                            return nextValue;
                        }
                    }
                }
            }
        }
        return "N/A";
    }

    private String valueOf(Element element) {
        if (element == null) {
            return null;
        }
        String value = element.hasAttr("value") ? element.attr("value") : element.text();
        return value == null ? null : value.trim();
    }

    private boolean labelContainsValue(String text, Pattern labelRegex) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        Matcher matcher = labelRegex.matcher(normalized);
        if (!matcher.find()) {
            return false;
        }
        String remainder = normalized.substring(matcher.end()).replaceFirst("^[:\\-\\s]+", "").trim();
        return !remainder.isBlank();
    }

    private String defaultValue(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
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
        String absoluteUrl = image.absUrl("src");
        return absoluteUrl.isBlank() ? image.attr("src") : absoluteUrl;
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
