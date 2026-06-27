package krpaivin.telcal.chatgpt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import krpaivin.telcal.config.Constants;
import krpaivin.telcal.config.Messages;
import krpaivin.telcal.config.TelegramProperties;
import krpaivin.telcal.data.UserAuthData;

/**
 * Handles communication with the ChatGPT API for generating responses based on
 * user input and request type.
 */
@RequiredArgsConstructor
@Service
public class ChatGPTHadler {

    private final UserAuthData userAuthData;
    private final TelegramProperties telegramProperties;

    /**
     * Gets a response from ChatGPT based on the provided voice text, request type,
     * and user ID.
     * 
     * @param voiceText      the input text to be analyzed or processed by ChatGPT.
     * @param typeGPTRequest the type of request indicating the purpose of the
     *                       response, such as creating events,
     *                       performing analytics, or searching for information.
     * @param userId         the ID of the user making the request.
     * @return a response string from ChatGPT.
     * @throws IllegalArgumentException if there is an error with the API connection
     *                                  or the request type is unknown.
     * @throws JSONException            if there is an issue parsing the JSON
     *                                  response.
     */
    public String publicGetResponseFromChatGPT(String voiceText, TypeGPTRequest typeGPTRequest, String userId) {
        return getResponseFromChatGPT(voiceText, typeGPTRequest, userId);
    }

    /**
     * Builds the system prompt for the ChatGPT API based on the request type.
     *
     * @param type   the type of request to determine the instructions sent to
     *               ChatGPT.
     * @param userId the ID of the user making the request.
     * @return the system prompt for the ChatGPT API.
     * @throws IOException if there is an error with the API connection or the
     *                     request type is unknown.
     */
    private String buildSystemPrompt(TypeGPTRequest type, String userId) throws IOException {
        return switch (type) {
            case CREATING_EVENT -> getInstructionsForCreatingCalendar(userId);
            case ANALYTICS -> getInstructionsForAnalytics();
            case SEARCH -> getInstructionsForSearch();
            case CREATING_EVENT_TEXT -> getInstructionsForCreatingCalendarFromText();
            case ANALYTICS_TEXT -> getInstructionsForAnalyticsFromText();
            case SEARCH_TEXT -> getInstructionsForSearchFromText();
            default -> throw new IOException(Messages.UNKNOWN_REQUEST_GPT);
        };
    }

    private String today() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(Constants.DATE_PATTERN_DASH));
    }

    private String tomorrow() {
        return LocalDateTime.now()
                .plusDays(1)
                .format(DateTimeFormatter.ofPattern(Constants.DATE_PATTERN_DASH));
    }

    private JSONObject buildRequestJson(String systemPrompt, String userPrompt) {

        String modelName = Constants.GPT_MODEL;
        boolean isNewModel = modelName.startsWith("gpt-5");

        JSONObject jsonInput = new JSONObject();
        jsonInput.put("model", modelName);
        JSONArray messages = new JSONArray();

        messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        messages.put(new JSONObject().put("role", "user").put("content", userPrompt));
        jsonInput.put("messages", messages);

        if (isNewModel) {
            jsonInput.put("max_completion_tokens", 150);
        } else {
            jsonInput.put("temperature", 0.7);
            jsonInput.put("max_tokens", 150);
            jsonInput.put("top_p", 1.0);
        }

        return jsonInput;
    }

    private HttpURLConnection createConnection() throws IOException, URISyntaxException {
        URL url = new URI(telegramProperties.getOpenAIURL()).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + telegramProperties.getOpenAIKey().trim());
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setDoOutput(true);

        return connection;
    }

    private String readResponse(HttpURLConnection connection, int responseCode) throws IOException {

        InputStream inputStream;

        try {
            // Try to get inputStream (for successful responses)
            inputStream = connection.getInputStream();
        } catch (IOException e) {
            // If it is not possible to get inputStream, then it is an error - read from
            // errorStream
            inputStream = connection.getErrorStream();
        }

        // If errorStream is also null, then there is a problem with the connection
        if (inputStream == null) {
            throw new IOException("Failed to get response from OpenAI API. HTTP Code: " + responseCode);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining());
        }
    }

    private String parseError(String responseBody, int responseCode) {

        try {
            JSONObject errorJson = new JSONObject(responseBody);

            if (errorJson.has("error")) {
                JSONObject error = errorJson.getJSONObject("error");

                return "HTTP "
                        + responseCode
                        + ": "
                        + error.optString("message", responseBody)
                        + " (type: "
                        + error.optString("type", "unknown")
                        + ")";
            }

        } catch (JSONException e) {
            // Response is not valid JSON. Return raw response below.
        }

        return "HTTP " + responseCode + ": " + responseBody;
    }

    private String sendRequest(HttpURLConnection connection, JSONObject request) throws IOException {

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = request.toString().getBytes(StandardCharsets.UTF_8);
            os.write( input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        String responseBody = readResponse(connection, responseCode);

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException(parseError(responseBody, responseCode));
        }

        return responseBody;
    }

    private String extractContent(String responseBody) {

        JSONObject jsonObject = new JSONObject(responseBody);

        return jsonObject.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
    }

    private void checkApiKey() {
        String apiKey = telegramProperties.getOpenAIKey();

            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "OpenAI API key is not configured. Check application.properties: openAIKey");
            }
    }

    /**
     * Gets a response from ChatGPT for internal use, with specific processing based
     * on the request type.
     *
     * @param voiceText      the input text for ChatGPT to analyze or process.
     * @param typeGPTRequest the type of request to determine the instructions sent
     *                       to ChatGPT.
     * @param userId         the ID of the user making the request.
     * @return the processed response from ChatGPT.
     * @throws IllegalArgumentException if there is an error with the API connection
     *                                  or the request type is unknown.
     * @throws JSONException            if there is an issue parsing the JSON
     *                                  response.
     */
    protected String getResponseFromChatGPT(String voiceText, TypeGPTRequest typeGPTRequest, String userId) {
        try {
            // Check if OpenAI API key is configured
            checkApiKey();

            String systemPrompt = buildSystemPrompt(typeGPTRequest, userId);
            String userPrompt = voiceText;

            // Connect to ChatGPT API to get response
            HttpURLConnection connection = createConnection();

            JSONObject jsonInput = buildRequestJson(systemPrompt, userPrompt);

            // Get a response from ChatGPT
            String response = sendRequest(connection, jsonInput);

            return extractContent(response);

        } catch (JSONException e) {
            throw new JSONException(Messages.ERROR_JSON_GPT);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(Messages.INVALID_URL + telegramProperties.getOpenAIURL(), e);
        } catch (IOException e) {
            String errorMsg = Messages.ERROR_RECEIVING_GPT + " " + e.getMessage();
            throw new IllegalArgumentException(errorMsg, e);
        }
    }

    /**
     * Appends the role to the system prompt.
     *
     * @param sb   the StringBuilder to append the role to.
     * @param role the role to append.
     */
    private void appendRole(StringBuilder sb, String role) {
        sb.append("""
                ROLE:

                """)
                .append(role)
                .append("""

                            Do not answer the user.
                            Do not explain your reasoning.
                            Return only the requested output format.

                        """);
    }

    private void appendKeywordRules(StringBuilder sb) {
        sb.append(
                """
                        KEYWORD RULES:

                        Keyword may be missing.

                        If the request explicitly contains the word 'keyword' (or the translated equivalent of word 'keyword' in the user's language),
                        extract the text after word 'keyword'.

                        """);
    }

    /**
     * Constructs instructions for ChatGPT to process search-related requests.
     *
     * @return a formatted instruction string for ChatGPT to perform the search.
     */
    private String getInstructionsForSearch() {
        String currentDate = today();
        StringBuilder res = new StringBuilder();

        appendRole(res, "You are an information extraction engine.");

        res.append("""
                    TASK:
                    Extract these fields:
                    - Start Date
                    - End Date
                    - Search Type
                    - Keyword (optional)

                """);

        res.append("""
                DATE RULES:

                Dates can be specified in natural language.

                Examples:
                - today
                - yesterday
                - last week
                - last month
                - last year
                - during the last 3 months

                Calculate all relative dates from:
                """)
                .append(currentDate)
                .append("""

                        If the period is not specified, use:

                        Start date: 1900-01-01 00:00
                        End date: 2100-01-01 00:00

                        """)
                .append("""
                        """);

        appendKeywordRules(res);

        res.append("""
                    SEARCH TYPE RULES:

                    If user wants the first matching item: Search type = first
                    If user wants the last matching item: Search type = last
                    Otherwise: Search type = all

                """);

        res.append("""
                    OUTPUT RULES:

                    Output only the result.
                    Do not add explanations.
                    Do not use markdown.


                    If Keyword exists:

                    Start date: yyyy-MM-dd HH:mm /
                    End date: yyyy-MM-dd HH:mm /
                    Search type = first|last|all /
                    Keyword = text


                    If Keyword does not exist:

                    Start date: yyyy-MM-dd HH:mm /
                    End date: yyyy-MM-dd HH:mm /
                    Search type = first|last|all

                """);

        res.append("""
                    EXAMPLES:

                    Input:
                    Find my last dentist appointment

                    Output:
                    Start date: 1900-01-01 00:00 /
                    End date: 2100-01-01 00:00 /
                    Search type = last /
                    Keyword = dentist


                    Input:
                    Show meetings from last month

                    Output:
                    Start date: calculated date /
                    End date: calculated date /
                    Search type = all


                    Input:
                    Find first event keyword Java

                    Output:
                    Start date: 1900-01-01 00:00 /
                    End date: 2100-01-01 00:00 /
                    Search type = first /
                    Keyword = Java

                """);

        return res.toString();
    }

    /**
     * Constructs instructions for ChatGPT to perform analytics based on the given
     * text.
     *
     * @return a formatted instruction string for ChatGPT to perform analytics.
     */
    private String getInstructionsForAnalytics() {
        LocalDateTime today = LocalDateTime.now();
        String currentDate = today();
        String endDate = today.with(LocalTime.MAX).format(DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));
        StringBuilder res = new StringBuilder();

        appendRole(res,
                "You are an information extraction engine. Your task is to extract analytics parameters from a user request.");

        res.append("""
                    TASK:
                    Extract:

                    - Start Date
                    - End Date
                    - Keyword (optional)

                """);

        res.append("""
                DATE RULES:

                Dates can be specified in natural language.

                Examples:
                today
                yesterday
                last week
                last month
                last year

                Calculate relative dates from:
                """)
                .append(currentDate)
                .append("""
                        If no period is specified, assume all available history.

                        Use:
                        Start date: 1900-01-01 00:00
                        End date:
                        """)
                .append(endDate)
                .append("""

                        """);

        appendKeywordRules(res);

        res.append("""
                    OUTPUT FORMAT:

                    Return only result.
                    No explanations.
                    No markdown.


                    If keyword exists:
                    Start date: yyyy-MM-dd HH:mm / End date: yyyy-MM-dd HH:mm / Keyword = text

                    If keyword does not exist:
                    Start date: yyyy-MM-dd HH:mm / End date: yyyy-MM-dd HH:mm

                """);

        res.append("""
                    EXAMPLES:

                    Input:
                    Analytics for last month keyword work

                    Output:
                    Start date: calculated date / End date: calculated date / Keyword = work


                    Input:
                    Show statistics for this year

                    Output:
                    Start date: calculated date / End date: calculated date

                """);

        return res.toString();
    }

    private void appendAllowedKeywords(StringBuilder sb, String keywords, String compoundKeywords) {

        if ((keywords != null && !keywords.isEmpty()) || (compoundKeywords != null && !compoundKeywords.isEmpty())) {

            sb.append("""
                        KEYWORD RULES:

                        Allowed keywords:

                    """);

            if (keywords != null && !keywords.isEmpty()) {
                sb.append(keywords).append("\n");
            }

            if (compoundKeywords != null && !compoundKeywords.isEmpty()) {
                sb.append(compoundKeywords).append("\n");
            }

            if (compoundKeywords != null && !compoundKeywords.isEmpty()) {
                String[] compounds = compoundKeywords.split(",");

                for (String compound : compounds) {
                    sb.append("Words \"").append(compound).append("\" together mean one keyword.\n");
                }
            }

            sb.append("\n");
        }

    }

    /**
     * Constructs instructions for ChatGPT to create a calendar event from the
     * provided text and user-specific data.
     *
     * @param userId the ID of the user for accessing personalized data.
     * @return a formatted instruction string for ChatGPT to create a calendar
     *         event.
     */
    private String getInstructionsForCreatingCalendar(String userId) {

        LocalDateTime today = LocalDateTime.now();
        String todayStr = today();
        String tomorrowStr = tomorrow();
        String currentYear = today.format(DateTimeFormatter.ofPattern(Constants.YEAR_PATTERN));
        String nextYear = today.plusYears(1).format(DateTimeFormatter.ofPattern(Constants.YEAR_PATTERN));
        String keywords = userAuthData.getKeywords(userId);
        String defaultKeyword = userAuthData.getDefaultKeywords(userId);
        String compoundKeywords = userAuthData.getCompoundKeywords(userId);
        StringBuilder res = new StringBuilder();

        appendRole(res, "You are a calendar event extraction engine. Extract structured event data from user text.");

        res.append("""
                    TASK:

                    Extract:

                    - Date
                    - Start time
                    - Duration
                    - Keyword (optional)
                    - Description

                """);

        res.append("""
                DATE RULES:

                Date can be written in natural language.

                Examples:
                tomorrow
                tomorrow at eight
                next week
                next Monday

                Calculate relative dates from:
                """)
                .append(todayStr)
                .append("""
                        If year is missing:

                        Use current year:
                        """)
                .append(currentYear)
                .append("""
                            If date is already passed, use next year:
                        """)
                .append(nextYear)
                .append("""
                        If date is missing: use tomorrow:
                        """)
                .append(tomorrowStr)
                .append("""

                        """);

        res.append("""
                    TIME RULES:

                    Time can be written in natural language.

                    Examples:
                    at eight
                    at 19:00
                    at ten in the evening

                    If time cannot be determined: use 09:00

                """);

        res.append("""
                    DURATION RULES:

                    Duration is measured in minutes.

                    If start and end time exist, calculate duration.
                    If duration is missing: use 60 minutes.

                """);

        appendAllowedKeywords(res, keywords, compoundKeywords);

        if (defaultKeyword != null && !defaultKeyword.isEmpty()) {
            res.append("""
                    If keyword is missing, use default keyword:
                    """)
                    .append(defaultKeyword)
                    .append("\n\n");
        }

        res.append("""
                    DESCRIPTION RULES:

                    Description is remaining text after removing date, time and duration.

                """);

        res.append("""
                    OUTPUT FORMAT:

                    If keyword exists:
                    yyyy-MM-dd HH:mm / Duration=minutes / Keyword. Description


                    If keyword does not exist:
                    yyyy-MM-dd HH:mm / Duration=minutes / Description

                    Do not output the word "Description".

                """);

        res.append("""
                EXAMPLE:

                Input:
                Tomorrow at 10 meeting keyword work

                Output:

                """)
                .append(tomorrowStr)
                .append("""
                        10:00 /  Duration=60 / work. meeting

                        """);

        return res.toString();
    }

    /**
     * Generates a string containing instructions for analyzing and formatting a
     * source text to create a calendar entry.
     *
     * @return a formatted string with instructions for processing the text
     */
    private String getInstructionsForCreatingCalendarFromText() {
        String tomorrowStr = tomorrow();
        StringBuilder res = new StringBuilder();

        appendRole(res, "You are a calendar text formatter. Convert user text into a calendar entry.");

        res.append("""
                    TASK:

                    Extract:

                    - Date
                    - Time
                    - Description

                """);

        res.append("""
                    RULES:

                    Date, time and description must exist in source text.

                    If date is missing:
                    Output: Error. Date is not specified.

                    If time is missing:
                    Output: Error. Time is not specified.

                    If description is missing:
                    Output: Error. Description is not specified.

                """);

        res.append("""
                    OUTPUT FORMAT:

                    yyyy-MM-dd HH:mm Description

                """);

        res.append("""
                EXAMPLE:

                Input:
                Meeting tomorrow at 10 discuss project

                Output:""")
                .append(tomorrowStr)
                .append("""
                        10:00 discuss project

                        """);

        return res.toString();
    }

    /**
     * Generates a string containing instructions for analyzing and formatting a
     * source text for analytics.
     *
     * @return a formatted string with instructions for processing the text
     */
    private String getInstructionsForAnalyticsFromText() {
        StringBuilder res = new StringBuilder();

        appendRole(res,
                "You are an analytics query formatting engine. Convert user text into structured analytics parameters.");

        res.append("""
                    TASK:

                    Extract:

                    - Start Date
                    - End Date
                    - Description

                """);

        res.append("""
                DATE RULES:

                Dates may be specified in natural language.

                Examples:

                - today
                - yesterday
                - last week
                - last month
                - next Monday

                Convert all dates to:

                yyyy-MM-dd

                """);

        res.append("""
                    DESCRIPTION RULES:

                    Description is the remaining text after removing all detected dates.
                    Description may be empty.

                """);

        res.append("""
                    OUTPUT FORMAT:

                    Output only:

                    yyyy-MM-dd yyyy-MM-dd Description

                    Do not output any explanations.
                    Do not use markdown.

                """);

        res.append("""
                    EXAMPLE:

                    Input:
                    Sales statistics for last month

                    Output:
                    yyyy-MM-dd yyyy-MM-dd Sales statistics

                """);

        return res.toString();
    }

    /**
     * Generates a string containing instructions for analyzing and formatting a
     * source text for a search query.
     *
     * @param text the source text to analyze
     * @return a formatted string with instructions for processing the text
     */
    private String getInstructionsForSearchFromText() {
        StringBuilder res = new StringBuilder();

        appendRole(res, "You are a text formatting engine. Convert user text into structured search parameters.");

        res.append("""
                    TASK:

                    Extract:

                    - Start Date
                    - End Date
                    - Search Type
                    - Description

                """);

        res.append("""
                    SEARCH TYPE RULES:

                    first:
                    if user wants first item

                    last:
                    if user wants last item

                    all:
                    otherwise

                    If search type is missing, use all.

                """);

        res.append("""
                    DESCRIPTION RULES:

                    Description is the remaining text after removing dates and search commands.

                    Description may be empty.

                """);

        res.append("""
                    OUTPUT FORMAT:

                    Output only:

                    yyyy-MM-dd HH:mm / yyyy-MM-dd HH:mm / Description / Search type

                """);

        res.append("""
                    EXAMPLE:

                    Input:
                    Find last meeting with John

                    Output:

                    1900-01-01 00:00 / 2100-01-01 00:00 / John meeting / last

                """);

        return res.toString();
    }

}
