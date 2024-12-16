package krpaivin.telcal.telegram;

import java.io.BufferedReader;
import java.io.IOException;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import com.assemblyai.api.AssemblyAI;
import com.assemblyai.api.resources.transcripts.types.*;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import krpaivin.telcal.chatgpt.ChatGPTHadler;
import krpaivin.telcal.chatgpt.TypeGPTRequest;
import krpaivin.telcal.config.TelegramBotConfig;

/**
 * Handles voice command processing, including converting voice input to text
 * and interacting with the ChatGPT API for responses based on voice commands.
 */
@RequiredArgsConstructor
@Component
public class VoiceCommandHandler {
    private final ChatGPTHadler chatGPTHadler;

    /**
     * Converts a voice audio file located at the specified URL to text using AssemblyAI.
     *
     * @param fileUrl the URL of the audio file to convert to text
     * @return the transcribed text from the audio file
     * @throws IOException if an I/O error occurs during the process
     * @throws IllegalArgumentException if the provided URL format is invalid
     */
    public String convertVoiceToText(String fileUrl) throws IOException {
        URL url;
        try {
            url = new URI(fileUrl).toURL();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL format: " + fileUrl, e);
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Create input stream
        try (InputStream inputStream = connection.getInputStream()) {

            // Download file to AssemblyAI
            String uploadUrl = uploadAudioFile(inputStream);

            var client = AssemblyAI.builder()
                    .apiKey(TelegramBotConfig.getAssemblyAI())
                    .build();

            var params = TranscriptOptionalParams.builder()
                    .languageDetection(true)
                    .speakerLabels(true)
                    .build();

            Transcript transcript = client.transcripts().transcribe(uploadUrl, params);

            // Checking the transcription status
            if (transcript.getStatus().equals(TranscriptStatus.ERROR)) {
                throw new IOException(transcript.getError().get());
            }

            // Return the recognize text
            return transcript.getText().get();
        }
    }

    /**
     * Uploads an audio file to AssemblyAI for transcription.
     *
     * @param audioInputStream the input stream of the audio file to upload
     * @return the upload URL of the audio file after a successful upload
     * @throws IOException if an I/O error occurs during the upload process
     * @throws IllegalArgumentException if the URL format for AssemblyAI is invalid
     */
    private String uploadAudioFile(InputStream audioInputStream) throws IOException {
        URL url;
        try {
            url = new URI(TelegramBotConfig.getAssemblyAIURL()).toURL();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL format: " + TelegramBotConfig.getAssemblyAIURL(), e);
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("authorization", TelegramBotConfig.getAssemblyAI());
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        connection.setDoOutput(true);

        // Sending an audio file
        try (OutputStream os = connection.getOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = audioInputStream.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }

        String response = readResponse(connection);
        // Checking answer
        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            JSONObject jsonObject = new JSONObject(response);
            return jsonObject.getString("upload_url"); // Extract URL of the audio file
        } else {
            throw new IOException("Error loading audio file: " + response);
        }
    }

    /**
     * Reads the response from the given HTTP connection.
     *
     * @param connection the HTTP connection to read the response from
     * @return the response as a string
     * @throws IOException if an I/O error occurs while reading the response
     */
    private String readResponse(HttpURLConnection connection) throws IOException {
        InputStream stream = connection.getResponseCode() == HttpURLConnection.HTTP_OK
                ? connection.getInputStream()
                : connection.getErrorStream();

        StringBuilder response = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    /**
     * Extracts specific details from the voice command and the response from ChatGPT
     * based on the request type.
     *
     * @param typeGPTRequest the type of request for GPT processing
     * @param userId the ID of the user making the request
     * @param fileUrl the URL of the audio file for voice command
     * @return an array of extracted details
     * @throws IOException if an I/O error occurs during processing
     */
    protected String[] extractDetailsFromVoiceAndGPT(TypeGPTRequest typeGPTRequest, String userId, String fileUrl) throws IOException {
        String gptResponse = getResponseFromVoiceAndGPT(typeGPTRequest, userId, fileUrl);
        String[] details = null;
        if (typeGPTRequest == TypeGPTRequest.ANALYTICS) {
            details = TextHandler.extractAnalyticDetails(gptResponse);
        } else if (typeGPTRequest == TypeGPTRequest.SEARCH) {
            details = TextHandler.extractSearchDetails(gptResponse);
        }
        return details;
    }

    /**
     * Gets a response from ChatGPT based on the voice command processed from the audio file.
     *
     * @param typeGPTRequest the type of request for GPT processing
     * @param userId the ID of the user making the request
     * @param fileUrl the URL of the audio file for voice command
     * @return the response from ChatGPT
     * @throws IOException if an I/O error occurs during processing
     */
    protected String getResponseFromVoiceAndGPT(TypeGPTRequest typeGPTRequest, String userId, String fileUrl) throws IOException {
        String voiceText = convertVoiceToText(fileUrl);
        return chatGPTHadler.publicGetResponseFromChatGPT(voiceText, typeGPTRequest, userId);
    }
    
}
