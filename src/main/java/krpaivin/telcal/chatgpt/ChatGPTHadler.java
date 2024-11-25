package krpaivin.telcal.chatgpt;

import java.io.BufferedReader;
import java.io.IOException;
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

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import krpaivin.telcal.config.Constants;
import krpaivin.telcal.config.TelegramBotConfig;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class ChatGPTHadler {

    public String publicGetResponseFromChatGPT(String voiceText, TypeGPTRequest typeGPTRequest) {
        return getResponseFromChatGPT(voiceText, typeGPTRequest);
    }

    protected String getResponseFromChatGPT(String voiceText, TypeGPTRequest typeGPTRequest) {
        try {
            // Connect to ChatGPT API to get response
            URL url = new URI(TelegramBotConfig.getOpenAIURL()).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + TelegramBotConfig.getOpenAIKey());
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);

            String prompt = "";
            if (typeGPTRequest == TypeGPTRequest.CREATING_EVENT) {
                prompt = getInstructionsForCreatingCalendar(voiceText);
            } else if (typeGPTRequest == TypeGPTRequest.ANALYTICS) {
                prompt = getInstructionsForAnalytics(voiceText);
            } else if (typeGPTRequest == TypeGPTRequest.SEARCH) {
                prompt = getInstructionsForSearch(voiceText);
            } else {
                throw new IOException("Unknown request type to ChatGPT.");
            }

            String jsonInputString = "{\"model\": \"" + Constants.GPT_MODEL + "\", " +
                    "\"messages\": [{\"role\": \"user\", \"content\": \"" + prompt + "\"}], " +
                    "\"temperature\": 0.7, " +
                    "\"max_tokens\": 150, " +
                    "\"top_p\": 1.0}";

            // Get a response from ChatGPT
            OutputStream os = connection.getOutputStream();
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);

            // Read a response from ChatGPT
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null) {
                response.append(line);
            }

            os.close();
            in.close();

            // Process JSON response
            String jsonResponse = response.toString();
            JSONObject jsonObject = new JSONObject(jsonResponse);

            // Extract text from the field message.content
            return jsonObject.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

        } catch (JSONException e) {
            throw new JSONException("Error processing JSON response from ChatGPT.");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL format: " + TelegramBotConfig.getOpenAIURL(), e);
        } catch (IOException e) {
            throw new RuntimeException("Error receiving response from ChatGPT." + e.getMessage());
        }
    }

    private String getInstructionsForSearch(String voiceText) {
        LocalDateTime today = LocalDateTime.now();

        return "Analyze the text and find 'Start Date', 'End Date', 'Keyword' and selection type. " +
                "The period can be specified in free form. " +
                "For example: last month, during the last year, last week, etc. " +
                "The period specified in this way is counted from the current day equal to " +
                today.format(DateTimeFormatter.ofPattern(Constants.DATE_PATTERN)) + ". " +
                "If the period is not specified or the all-time period is assumed, " +
                "then 'Start Date' must be set equal to 01.01.1900. " +
                "And set 'End Date' equal to 01.01.2100" +
                "The keyword comes after the word 'keyword' (if the text is in English). " +
                "If the text is in any other language, the keyword will be after the keyword " +
                "written in translation into that language. " +
                "Keyword may be missing. " +
                "It may be specified that either the first element found, or the last one, " +
                "or all found elements should be selected." +
                "If need search first element Search type = first, if need search last element Search type = last, " +
                "in other cases Search type = all." +
                "If there is a keyword, then answer strictly in the format: " +
                "'Start date: yyyy-MM-dd HH:mm / End date: yyyy-MM-dd HH:mm / Search type = / Keyword = ', " +
                "where the first date is the beginning of the period and the second date is the end of the period. " +
                "If there is no keyword, then answer strictly in the format: " +
                "'Start date: yyyy-MM-dd HH:mm / End date: yyyy-MM-dd HH:mm / Search type = ', " +
                "where the first date is the beginning of the period and the second date is the end of the period. " +
                "Here is the original text: " + voiceText;
    }

    private String getInstructionsForAnalytics(String voiceText) {
        LocalDateTime today = LocalDateTime.now();

        return "Analyze the text and find 'Start Date', 'End Date' and the 'Keyword'. " +
                "The period can be specified in free form. " +
                "For example: last month, during the last year, last week, etc. " +
                "The period specified in this way is counted from the current day equal to " +
                today.format(DateTimeFormatter.ofPattern(Constants.DATE_PATTERN)) + ". " +
                "If the period is not specified or the all-time period is assumed, " +
                "then 'Start Date' must be set equal to 01.01.1900. " +
                "And set 'End Date' equal to " +
                today.with(LocalTime.MAX).format(DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN)) + ". " +
                "The keyword comes after the word 'keyword' (if the text is in English). " +
                "If the text is in any other language, the keyword will be after the keyword " +
                "written in translation into that language. " +
                "Keyword may be missing. " +
                "If there is a keyword, then answer strictly in the format: " +
                "'Start date: yyyy-MM-dd HH:mm / End date: yyyy-MM-dd HH:mm / Keyword =', " +
                "where the first date is the beginning of the period and the second date is the end of the period. " +
                "If there is no keyword, then answer strictly in the format: " +
                "'Start date: yyyy-MM-dd HH:mm / End date: yyyy-MM-dd HH:mm', " +
                "where the first date is the beginning of the period and the second date is the end of the period. " +
                "Here is the original text: " + voiceText;
    }

    private String getInstructionsForCreatingCalendar(String voiceText) {

        LocalDateTime today = LocalDateTime.now();

        return "Analyze the text and find 'date', 'time', 'duration' and a keyword with a description. " +
                "If the year is not specified in the source text, then set the current year. " +
                "The month can be specified as a number or a word. " +
                "The date can be specified in free form. " +
                "For example: tomorrow, the day after tomorrow, some day this week and the next, etc. " +
                "The date specified in this way is counted from the current day equal to " +
                today.format(DateTimeFormatter.ofPattern(Constants.DATE_PATTERN)) + ". " +
                "If the date is missing or could not be determined, then it is necessary to set the date equal to " +
                today.plusDays(1).format(DateTimeFormatter.ofPattern(Constants.DATE_PATTERN)) + ". " +
                "If you could not determine the time in the source text, then set the time to 09:00. " +
                "Duration is the number of minutes that indicates how long the event will last. " +
                "If the duration is missing, it should be equal to 60 minutes. " +
                "The following keywords are possible: Ашка, Юшка или АшкаЮшка. " +
                "If the original text contains both the word Ашка and the word Юшка, then they should be combined into one АшкаЮшка. "
                +
                "If there is no keyword, then you need to install АшкаЮшка. " +
                "Description - what comes immediately after the keyword. " +
                "Please answer strictly in the format: 'yyyy-MM-dd HH:mm / Duration=mm / Keyword. Description'. " +
                "Here is the original text: " + voiceText;
    }

}
