package community.store.ui.desktop.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Client-side mirror of server-auth's RegistrationPolicy, so the form can explain problems before submitting.
 * The server remains the source of truth. Override the domain with {@code -Dcommunity.university-email-domain=...}.
 */
public final class RegistrationRules {

    public static final String UNIVERSITY_DOMAIN =
            System.getProperty("community.university-email-domain", "university.com").toLowerCase(Locale.ROOT);

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern BUSINESS_NUMBER = Pattern.compile("^[A-Za-z0-9/ -]{4,50}$");

    private RegistrationRules() {
    }

    public static boolean requiresUniversityEmail(String userType) {
        return "STUDENT".equals(userType) || "FACULTY".equals(userType);
    }

    public static List<String> validate(String userType, String firstName, String lastName, String email,
                                        String confirmEmail, String businessNumber, String password) {
        List<String> problems = new ArrayList<>();
        if (firstName.isBlank()) {
            problems.add("First name is required.");
        }
        if (lastName.isBlank()) {
            problems.add("Last name is required.");
        }
        String normalisedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalisedEmail).matches()) {
            problems.add("Enter a valid email address.");
        } else if (requiresUniversityEmail(userType)
                && !normalisedEmail.substring(normalisedEmail.lastIndexOf('@') + 1).equals(UNIVERSITY_DOMAIN)) {
            problems.add("Students and faculty must use an @" + UNIVERSITY_DOMAIN + " address.");
        }
        if (!confirmEmail.trim().equalsIgnoreCase(email.trim())) {
            problems.add("Email addresses do not match.");
        }
        if ("VENDOR".equals(userType)) {
            if (businessNumber.isBlank()) {
                problems.add("Vendors must enter a business registration number.");
            } else if (!BUSINESS_NUMBER.matcher(businessNumber.trim()).matches()) {
                problems.add("Business registration number: use 4-50 letters, digits, spaces, '/' or '-'.");
            }
        }
        if (password.length() < 8) {
            problems.add("Password must be at least 8 characters.");
        }
        return problems;
    }
}
