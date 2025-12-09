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
            String apiKey = telegramProperties.getOpenAIKey();
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalArgumentException("OpenAI API key is not configured. Check application.properties: openAIKey");
            }

            // Connect to ChatGPT API to get response
            URL url = new URI(telegramProperties.getOpenAIURL()).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);

            String prompt = "";
            if (typeGPTRequest == TypeGPTRequest.CREATING_EVENT) {
                prompt = getInstructionsForCreatingCalendar(voiceText, userId);
            } else if (typeGPTRequest == TypeGPTRequest.ANALYTICS) {
                prompt = getInstructionsForAnalytics(voiceText);
            } else if (typeGPTRequest == TypeGPTRequest.SEARCH) {
                prompt = getInstructionsForSearch(voiceText);
            } else if (typeGPTRequest == TypeGPTRequest.CREATING_EVENT_TEXT) {
                prompt = getInstructionsForCreatingCalendarFromText(voiceText);
            } else if (typeGPTRequest == TypeGPTRequest.ANALYTICS_TEXT) {
                prompt = getInstructionsForAnalyticsFromText(voiceText);
            } else if (typeGPTRequest == TypeGPTRequest.SEARCH_TEXT) {
                prompt = getInstructionsForSearchFromText(voiceText);
            } else {
                throw new IOException(Messages.UNKNOWN_REQUEST_GPT);
            }

            String modelName = Constants.GPT_MODEL;
            boolean isNewModel = modelName.startsWith("gpt-5");

            String jsonInputString = "{\"model\": \"" + modelName + "\", " +
                    "\"messages\": [{\"role\": \"user\", \"content\": \"" + prompt + "\"}], " +
                    (isNewModel ? "\"max_completion_tokens\": 150}" : "\"temperature\": 0.7, \"max_tokens\": 150, \"top_p\": 1.0}");

            // Get a response from ChatGPT
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check response code before reading
            int responseCode = connection.getResponseCode();
            
            // Read response (success or error)
            StringBuilder response = new StringBuilder();
            InputStream inputStream;
            
            try {
                // Try to get inputStream (for successful responses)
                inputStream = connection.getInputStream();
            } catch (IOException e) {
                // If it is not possible to get inputStream, then it is an error - read from errorStream
                inputStream = connection.getErrorStream();
            }
                
            // If errorStream is also null, then there is a problem with the connection
            if (inputStream == null) {
                throw new IOException("Failed to get response from OpenAI API. HTTP Code: " + responseCode);
            }
            
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
            }

            String responseBody = response.toString();
            
            // If error response, throw exception with error message
            if (responseCode != HttpURLConnection.HTTP_OK) {
                // Try to parse JSON error for a more understandable message
                String errorMessage;
                try {
                    JSONObject errorJson = new JSONObject(responseBody);
                    if (errorJson.has("error")) {
                        JSONObject error = errorJson.getJSONObject("error");
                        errorMessage = "HTTP " + responseCode + ": " + 
                                error.optString("message", responseBody) + 
                                " (type: " + error.optString("type", "unknown") + ")";
                    } else {
                        errorMessage = "HTTP " + responseCode + ": " + responseBody;
                    }
                } catch (JSONException e) {
                    errorMessage = "HTTP " + responseCode + ": " + responseBody;
                }
                throw new IOException(errorMessage);
            }

            // Process JSON response
            String jsonResponse = response.toString();
            JSONObject jsonObject = new JSONObject(jsonResponse);

            // Extract text from the field message.content
            return jsonObject.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

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
     * Constructs instructions for ChatGPT to process search-related requests.
     *
     * @param voiceText the input text containing search criteria.
     * @return a formatted instruction string for ChatGPT to perform the search.
     */
    private String getInstructionsForSearch(String voiceText) {
        LocalDateTime today = LocalDateTime.now();
        StringBuilder res = new StringBuilder();

        res.append("Analyze the text and find 'Start Date', 'End Date', 'Keyword' and selection type. ")
                .append("The period can be specified in free form. ")
                .append("For example: last month, during the last year, last week, etc. ")
                .append("The period specified in this way is counted from the current day equal to ")
                .append(today.format(DateTimeFormatter.ofPattern(Constants.DATE_PATTERN))).append(". ")
                .append("If the period is not specified or the all-time period is assumed, ")
                .append("then 'Start Date' must be set equal to 01.01.1900. ")
                .append("And set 'End Date' equal to 01.01.2100")
                .append("The keyword comes after the word 'keyword' (if the text is in English). ")
                .append("If the text is in any other language, the keyword will be after the keyword ")
                .append("written in translation into that language. ")
                .append("Keyword may be missing. ")
                .append("It may be specified that either the first element found, or the last one, ")
                .append("or all found elements should be selected.")
                .append("If need search first element Search type = first, if need search last element Search type = last, ")
                .append("in other cases Search type = all.")
                .append("If there is a keyword, then answer strictly in the format: ")
                .append("'Start date: yyyy-MM-dd HH:mm / End date: yyyy-MM-dd HH:mm / Search type = / Keyword = ', ")
                .append("where the first date is the beginning of the period and the second date is the end of the period. ")
                .append("If there is no keyword, then answer strictly in the format: ")
                .append("'Start date: yyyy-MM-dd HH:mm / End date: yyyy-MM-dd HH:mm / Search type = ', ")
                .append("where the first date is the beginning of the period and the second date is the end of the period. ")
                .append("Here is the original text: ").append(voiceText);
        return res.toString();
    }

    /**
     * Constructs instructions for ChatGPT to perform analytics based on the given
     * text.
     *
     * @param voiceText the input text containing analytics criteria.
     * @return a formatted instruction string for ChatGPT to perform analytics.
     */
    private String getInstructionsForAnalytics(String voiceText) {
        LocalDateTime today = LocalDateTime.now();
        StringBuilder res = new StringBuilder();

        res.append("Analyze the text and find 'Start Date', 'End Date' and the 'Keyword'. ")
                .append("The period can be specified in free form. ")
                .append("For example: last month, during the last year, last week, etc. ")
                .append("The period specified in this way is counted from the current day equal to ")
                .append(today.format(DateTimeFormatter.ofPattern(Constants.DATE_PATTERN))).append(". ")
                .append("If the period is not specified or the all-time period is assumed, ")
                .append("then 'Start Date' must be set equal to 01.01.1900. ")
                .append("And set 'End Date' equal to ")
                .append(today.with(LocalTime.MAX).format(DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN)))
                .append(". ")
                .append("The keyword comes after the word 'keyword' (if the text is in English). ")
                .append("If the text is in any other language, the keyword will be after the keyword ")
                .append("written in translation into that language. ")
                .append("Keyword may be missing. ")
                .append("If there is a keyword, then answer strictly in the format: ")
                .append("'Start date: yyyy-MM-dd HH:mm / End date: yyyy-MM-dd HH:mm / Keyword =', ")
                .append("where the first date is the beginning of the period and the second date is the end of the period. ")
                .append("If there is no keyword, then answer strictly in the format: ")
                .append("'Start date: yyyy-MM-dd HH:mm / End date: yyyy-MM-dd HH:mm', ")
                .append("where the first date is the beginning of the period and the second date is the end of the period. ")
                .append("Here is the original text: ").append(voiceText);
        return res.toString();
    }

    /**
     * Constructs instructions for ChatGPT to create a calendar event from the
     * provided text and user-specific data.
     *
     * @param voiceText the input text describing the event.
     * @param userId    the ID of the user for accessing personalized data.
     * @return a formatted instruction string for ChatGPT to create a calendar
     *         event.
     */
    private String getInstructionsForCreatingCalendar(String voiceText, String userId) {

        LocalDateTime today = LocalDateTime.now();
        String todayStr = today.format(DateTimeFormatter.ofPattern(Constants.DATE_PATTERN));
        String nextDayStr = today.plusDays(1).format(DateTimeFormatter.ofPattern(Constants.DATE_PATTERN));
        // String todayNextYearStr =
        // today.plusYears(1).format(DateTimeFormatter.ofPattern(Constants.DATE_PATTERN));
        String currentYear = today.format(DateTimeFormatter.ofPattern(Constants.YEAR_PATTERN));
        String nextYear = today.plusYears(1).format(DateTimeFormatter.ofPattern(Constants.YEAR_PATTERN));
        String keywords = userAuthData.getKeywords(userId);
        String defaultKeyword = userAuthData.getDefaultKeywords(userId);
        String compoundKeywords = userAuthData.getCompoundKeywords(userId);
        boolean keywordExists = false;
        StringBuilder res = new StringBuilder();

        res.append("Analyze the text and find 'date', 'start time', 'duration', ");
        if ((keywords != null && !"".equals(keywords))
                || (compoundKeywords != null && !"".equals(compoundKeywords))) {
            res.append(" 'keyword', ");
            keywordExists = true;
        }
        res.append(" 'description'. ")
                .append(". In the source text, the date can be specified in free form. ")
                .append("For example: tomorrow, tomorrow at eight, the day after tomorrow, some day this week and the next, etc. ")
                .append("The date specified in this way is counted from the current day equal to ")
                .append(todayStr).append(" and time from the source text. ")
                .append("If the year is not specified in the source text and the date you specify may be greater than ")
                .append(todayStr).append(" and less than 01.01.").append(nextYear).append(" , then the ")
                .append(currentYear).append(" is set. Otherwise, the year ").append(nextYear).append(" is set. ")
                .append("The month can be specified as a number or a word. ")
                .append("If the date is not explicitly set in the past, then the date you set must be greater then  ")
                .append(todayStr)
                .append("If the date is missing or could not be determined, then it is necessary to set the date equal to ")
                .append(nextDayStr).append(" and time from the source text. ")
                .append("In the source text, the time can be specified in a free form. ")
                .append("For example: at eight, at ten in the evening, at nineteen zero zero, 10, etc.")
                .append("If you could not determine the time in the source text, then set the time to 09:00. ")
                .append("Duration is the number of minutes that indicates how long the event will last. ")
                .append("If a start and end time are specified, the duration is equal to the difference between them. ")
                .append("If the duration is missing, it should be equal to 60 minutes. ");
        if (keywordExists) {
            res.append("The following keywords are possible: ");
        }
        if (keywords != null && !"".equals(keywords)) {
            res.append(keywords).append(", ");
        }
        if (compoundKeywords != null && !"".equals(compoundKeywords)) {
            res.append(compoundKeywords).append(". ");
        }
        if (compoundKeywords != null && !"".equals(compoundKeywords)) {
            String[] arrayString = compoundKeywords.split(",");
            for (String str : arrayString) {
                res.append("If the original text contains together the words ").append(str).append(", ")
                        .append("then consider it one keyword. ");
            }
        }
        if (defaultKeyword != null && !"".equals(defaultKeyword)) {
            res.append("If there is no keyword, then you need to install keyword equals ")
                    .append(defaultKeyword).append(". ");
        }
        res.append(". 'Description' is the remaining text without date and duration. ")
                .append("If there is a keyword in the source text, the answer you provide ")
                .append("should be in this format: 'yyyy-MM-dd HH:mm / Duration=mm / Keyword. Description'.")
                .append("If there is no keyword in the source text, the answer you provide ")
                .append("should be in this format: 'yyyy-MM-dd HH:mm / Duration=mm / Description'. ")
                .append("There is no need to display the word 'Description'. ")
                .append("Here is the source text: ").append(voiceText);
        return res.toString();
    }

    /**
     * Generates a string containing instructions for analyzing and formatting a
     * source text to create a calendar entry.
     *
     * @param text the source text to analyze
     * @return a formatted string with instructions for processing the text
     */
    private String getInstructionsForCreatingCalendarFromText(String text) {
        StringBuilder res = new StringBuilder();
        res.append("Analyze the source text. Find the date, time, and description. ")
                .append("The date and time can be specified in a free format. ")
                .append("The description is the rest of the text. Format the source text ")
                .append("and output it in the following format: yyyy-MM-dd HH:mm Description. ")
                .append("If the date is not specified in the source text, output: Error. Date is not specified. ")
                .append("If the time is not specified in the source text, output: Error. Time is not specified. ")
                .append("If the description is missing in the source text, output: Error. Description is not specified. ")
                .append("Here is the source text: " + text);
        return res.toString();
    }

    /**
     * Generates a string containing instructions for analyzing and formatting a
     * source text for analytics.
     *
     * @param text the source text to analyze
     * @return a formatted string with instructions for processing the text
     */
    private String getInstructionsForAnalyticsFromText(String text) {
        StringBuilder res = new StringBuilder();
        res.append("Analyze the source text. Find the start date, end date, and description. ")
                .append("Dates can be specified in a free format. The description is the rest of the text. ")
                .append("Format the source text and output it in the following format: yyyy-MM-dd yyyy-MM-dd Description. ")
                .append("The description may be missing. ")
                .append("Here is the source text: " + text);
        return res.toString();
    }

    /**
     * Generates a string containing instructions for analyzing and formatting a
     * source text for a search query.
     *
     * @param text the source text to analyze
     * @return a formatted string with instructions for processing the text
     */
    private String getInstructionsForSearchFromText(String text) {
        StringBuilder res = new StringBuilder();
        res.append("Analyze the source text. Find the start date, end date, search type, and description. ")
                .append("Dates can be specified in a free format. The search type can take ")
                .append("the following values: first/last/all. Search type may be missing. ")
                .append("Description - arbitrary text. Description may be missing. ")
                .append("Format the source text and output it in the following ")
                .append("format: yyyy-MM-dd / yyyy-MM-dd / Description / Search type.")
                .append("Here is the source text: " + text);
        return res.toString();
    }

}
