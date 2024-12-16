package krpaivin.telcal.telegram;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class for handling text responses from GPT, extracting relevant details for event creation, 
 * analytics, and searches.
 */
public class TextHandler {

    private TextHandler() {}

    /**
     * Extracts analytic details from the given GPT response.
     *
     * @param gptResponse the response string from GPT containing date and keyword information.
     * @return an array of strings containing the start date, end date, and keyword extracted from the response.
     *         The array structure is as follows:
     *         - Index 0: Start date in "yyyy-MM-dd HH:mm" format.
     *         - Index 1: End date in "yyyy-MM-dd HH:mm" format.
     *         - Index 2: Keyword extracted from the response.
     */
    protected static String[] extractAnalyticDetails(String gptResponse) {
        String[] details = new String[3];
        // Template for date in yyyy-MM-dd HH:mm
        Pattern startDatePattern = Pattern.compile("Start date: (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\b");
        // Template for time in yyyy-MM-dd HH:mm
        Pattern endDatePattern = Pattern.compile("End date: (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\b");
        // Template for Keyword
        Pattern keywordPattern = Pattern.compile("Keyword = ");

        Matcher startDateMatcher = startDatePattern.matcher(gptResponse);
        Matcher endDateMatcher = endDatePattern.matcher(gptResponse);

        if (startDateMatcher.find()) {
            details[0] = startDateMatcher.group(1); // Start date
        }
        if (endDateMatcher.find()) {
            details[1] = endDateMatcher.group(1); // End date
        }

        // Remove Date, Keyword and simbols '/'
        String remainingText = gptResponse.replaceAll(startDatePattern.pattern(), "")
                .replaceAll(endDatePattern.pattern(), "")
                .replaceAll(keywordPattern.pattern(), "")
                .replace("/", "")
                .trim();
        details[2] = remainingText; // Keyword

        return details;
    }

    /**
     * Extracts event details from the given GPT response.
     *
     * @param gptResponse the response string from GPT containing event details.
     * @return an array of strings containing the date, time, duration, and description extracted from the response.
     *         The array structure is as follows:
     *         - Index 0: Date in "yyyy-MM-dd" format.
     *         - Index 1: Time in "HH:mm" format.
     *         - Index 2: Duration in integer format.
     *         - Index 3: Description of the event.
     */
    protected static String[] extractEventDetails(String gptResponse) {
        String[] details = new String[4];
        // Template for date in yyyy-MM-dd
        Pattern datePattern = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
        // Template for time in HH:mm
        Pattern timePattern = Pattern.compile("\\b(\\d{2}:\\d{2})\\b");
        // Template for duration in integer
        Pattern durationPattern = Pattern.compile("Duration=(\\d+)");

        Matcher dateMatcher = datePattern.matcher(gptResponse);
        Matcher timeMatcher = timePattern.matcher(gptResponse);
        Matcher durationMatcher = durationPattern.matcher(gptResponse);

        if (dateMatcher.find()) {
            details[0] = dateMatcher.group(1); // Date
        }
        if (timeMatcher.find()) {
            details[1] = timeMatcher.group(1); // Time
        }
        if (durationMatcher.find()) {
            details[2] = durationMatcher.group(1); // Duration
        }

        // Remove Date, Time, Duration and simbols '/'
        String remainingText = gptResponse.replaceAll(datePattern.pattern(), "")
                .replaceAll(timePattern.pattern(), "")
                .replaceAll(durationPattern.pattern(), "")
                .replace("/", "")
                .trim();
        details[3] = remainingText; // Description

        return details;
    }

    /**
     * Extracts search details from the given GPT response.
     *
     * @param gptResponse the response string from GPT containing search criteria.
     * @return an array of strings containing the start date, end date, search type, and keyword extracted from the response.
     *         The array structure is as follows:
     *         - Index 0: Start date in "yyyy-MM-dd HH:mm" format.
     *         - Index 1: End date in "yyyy-MM-dd HH:mm" format.
     *         - Index 2: Search type (first, last, all).
     *         - Index 3: Keyword extracted from the response.
     */
    public static String[] extractSearchDetails(String gptResponse) {
        String[] details = new String[4];
        // Template for date in yyyy-MM-dd HH:mm
        Pattern startDatePattern = Pattern.compile("Start date: (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\b");
        // Template for time in yyyy-MM-dd HH:mm
        Pattern endDatePattern = Pattern.compile("End date: (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\b");
        // Template for Search type
        Pattern searchTypePattern = Pattern.compile("Search type = (\\w+)");
        // Template for Keyword
        Pattern keywordPattern = Pattern.compile("Keyword = ");

        Matcher startDateMatcher = startDatePattern.matcher(gptResponse);
        Matcher endDateMatcher = endDatePattern.matcher(gptResponse);
        Matcher searchTypeMatcher = searchTypePattern.matcher(gptResponse);

        if (startDateMatcher.find()) {
            details[0] = startDateMatcher.group(1); // Start date
        }
        if (endDateMatcher.find()) {
            details[1] = endDateMatcher.group(1); // End date
        }
        if (searchTypeMatcher.find()) {
            details[2] = searchTypeMatcher.group(1); // Search type
        }

        // Remove Date, Keyword and simbols '/'
        String remainingText = gptResponse.replaceAll(startDatePattern.pattern(), "")
                .replaceAll(endDatePattern.pattern(), "")
                .replaceAll(searchTypePattern.pattern(), "")
                .replaceAll(keywordPattern.pattern(), "")
                .replace("/", "")
                .trim();
        details[3] = remainingText; // Keyword

        return details;
    }

    /**
     * Provides a help text detailing the functionalities available to the user.
     *
     * @return a string containing instructions on how to use the bot, including how to add events,
     *         search for events, and view analytics.
     */
    protected static String getTextHepl() {
        return "You can:\n" + 
        "- add events using a text message\n" +
        "- add events using an audio message\n" +
        "- search events\n" +
        "- view analytics for events\n\n" +
        "1. For add events using a text message send text in the format: \"Date Time Description\"." +
        "Where date and time are set in dd.MM.yyyy HH:mm format. For example: 31.01.2024 12:00 Some description\n\n" +
        "2. For add events using an audio message send audio message in a free format. " +
        "You must somehow specify the start date and description. Optionally, you can " +
        "specify the duration (default is 1 hour) and start time (default is 9.00)" +
        "To indicate duration, you can either state the start and end times (e.g., \"from eight to ten\") " +
        "or say the word 'duration' (any language) followed by the duration (e.g., \"duration two hours\").\n\n" +
        "3. You can send a request to search for an event in text or audio format. " +
        "Required period, keyword (optional) and type search (optional, value: first/last/all). " +
        "In text format: dd.MM.yyyy dd.MM.yyyy Keyword TypeSearch (Value: first/last/all). " +
        "In voice format: State the period in free format. If a keyword is required, " +
        "say in any language \"keyword\" followed by the name. If a type search required say it in free format (by default value = all).\n\n" +
        "4. You can send a request to view analytics for an event in text or audio format. " +
        "Enter period and keyword (optional). In text format: dd.MM.yyyy dd.MM.yyyy Keyword (if you want). " +
        "In voice format: State the period in free format and if a keyword is required, say \"keyword\" followed by the name." +
        "5. Bot settings are set at startup (command /start). You can also change the settings using the command /setting";
    }

    /**
     * Checks if the provided message text is in the correct format for event creation.
     *
     * @param messageText the text message to check.
     * @return true if the format is correct; false otherwise.
     */
    public static boolean checkFormatEventCreation(String messageText) {
        boolean res = true;
        String pattern = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2} .+$";

        if (!Pattern.matches(pattern, messageText)) {
            res = false;
        }

        return res;
    }

    /**
     * Checks if the provided message text is in the correct format for analytics requests.
     *
     * @param messageText the text message to check.
     * @return true if the format is correct; false otherwise.
     */
    public static boolean checkFormatAnalyticsRequest(String messageText) {
        boolean res = true;
        String pattern = "(\\d{4}-\\d{2}-\\d{2}) (\\d{4}-\\d{2}-\\d{2}) (.+)$";
        String patternOnlyDate = "(\\d{4}-\\d{2}-\\d{2}) (\\d{4}-\\d{2}-\\d{2})$";

        if (!Pattern.matches(pattern, messageText) && !Pattern.matches(patternOnlyDate, messageText)) {
            res = false;
        }

        return res;
    }

    /**
     * Checks if the provided message text is in the correct format for search requests.
     *
     * @param messageText the text message to check.
     * @return true if the format is correct; false otherwise.
     */
    public static boolean checkFormatSearchRequest(String messageText) {
        //"yyyy-MM-dd / yyyy-MM-dd / TypeSearch / Keyword"
        boolean res = true;
        String patternAll = "(\\d{4}-\\d{2}-\\d{2}) / (\\d{4}-\\d{2}-\\d{2}) / (first|last|all) / (.+)$";
        String patternKeyword = "(\\d{4}-\\d{2}-\\d{2}) / (\\d{4}-\\d{2}-\\d{2}) / (.+)$";
        String patternTypeSearch = "(\\d{4}-\\d{2}-\\d{2}) / (\\d{4}-\\d{2}-\\d{2}) / (first|last|all)$";
        String patternOnlyDate = "(\\d{4}-\\d{2}-\\d{2}) / (\\d{4}-\\d{2}-\\d{2})$";


        if (!Pattern.matches(patternAll, messageText) && Pattern.matches(patternKeyword, messageText)
            && Pattern.matches(patternTypeSearch, messageText) && Pattern.matches(patternOnlyDate, messageText)) {
            res = false;
        }

        return res;
    }

    /**
     * Constructs a response message for search requests based on the extracted search details.
     *
     * @param searchDetails an array of strings containing search details including start date, end date,
     *                      search type, and keyword.
     * @return a formatted response message summarizing the search request.
     */
    public static String getSearchMessageForResponse(String[] searchDetails) {
        String[] startDate = searchDetails[0].split(" ");
        String[] endDate = searchDetails[1].split(" ");
        return "Your request: Start date: " + startDate[0] + " / End date: " + endDate[0] 
            + " / Search type = " + searchDetails[2] + " / Keyword = " + searchDetails[3];
    }

    /**
     * Constructs a response message for analytics requests based on the extracted analytic details.
     *
     * @param analyticDetails an array of strings containing analytic details including start date,
     *                        end date, and keyword.
     * @return a formatted response message summarizing the analytics request.
     */
    public static String getAnalyticsMessageForResponse(String[] analyticDetails) {
        String[] startDate = analyticDetails[0].split(" ");
        String[] endDate = analyticDetails[1].split(" ");
        return "Your request: Start date: " + startDate[0] + " / End date: " + endDate[0] + " / Keyword = " + analyticDetails[2];
    }

}
