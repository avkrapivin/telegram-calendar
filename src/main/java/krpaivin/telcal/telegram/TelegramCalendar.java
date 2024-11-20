package krpaivin.telcal.telegram;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import krpaivin.telcal.calendar.GoogleCalendarService;
import krpaivin.telcal.calendar.SearchType;
import krpaivin.telcal.chatgpt.ChatGPTHadler;
import krpaivin.telcal.chatgpt.TypeGPTRequest;
import krpaivin.telcal.config.Constants;
import krpaivin.telcal.config.TelegramBotConfig;

import org.telegram.telegrambots.meta.api.objects.File;

public class TelegramCalendar extends TelegramLongPollingBot {

    private Map<String, String> sessionData = new HashMap<>();

    @SuppressWarnings("deprecation")
    public TelegramCalendar() {
        //
    }
    @Override
    public String getBotUsername() {
        return "AshkaYushkaCalendar_bot";
    }

    @Override
    public String getBotToken() {
        return TelegramBotConfig.getBotToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            // Processing message
            handleMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            // Processing a button press
            handleCallbackQuery(update);
        }
    }

    private void handleCallbackQuery(Update update) {
        String chatId = update.getCallbackQuery().getMessage().getChatId().toString();
        String callData = update.getCallbackQuery().getData();
        if ("confirm_event".equals(callData)) {
            confirmEvent(chatId);
        } else if ("cancel_event".equals(callData)) {
            cancelEvent(chatId);
        }
    }

    private void cancelEvent(String chatId) {
        sessionData.remove(chatId);
        sendResponseMessage(chatId, "Event canceled");
    }

    private void confirmEvent(String chatId) {
        try {
            String gptResponse = sessionData.get(chatId);
            String[] eventDetails = TextHandler.extractEventDetails(gptResponse); // Extracting event details
            // Create a calendar event with a received data
            createCalendarEvent(eventDetails[0], eventDetails[1], eventDetails[2], eventDetails[3], chatId);
            // Remove session data
            sessionData.remove(chatId);
        } catch (Exception e) {
            sendResponseMessage(chatId, "Error creating event in calendar.");
        }
    }

    private void handleMessage(Message message) {
        String userId = message.getFrom().getUserName();
        String chatId = message.getChatId().toString();

        if (!isAuthorizedUser(userId)) {
            sendResponseMessage(chatId, "You do not have permission to use the bot.");
            return;
        }

        if (message.hasText()) {
            // Processing text message
            handleTextMessage(message);
        } else if (message.hasVoice()) {
            // Processing voice message
            handleVoiceMessage(message, chatId);
        }
    }

    private void handleVoiceMessage(Message message, String chatId) {

        if (Constants.REQUEST_ANALYTICS.equals(sessionData.get(chatId + Constants.STATE))) {

            String[] analyticDetails = extractDetailsFromVoiceAndGPT(message, TypeGPTRequest.ANALYTICS, chatId);
            getAnalyticsFromCalendar(analyticDetails[0], analyticDetails[1], analyticDetails[2], chatId);

        } else if (Constants.REQUEST_SEARCH.equals(sessionData.get(chatId + Constants.STATE))) {

            String[] searchDetails = extractDetailsFromVoiceAndGPT(message, TypeGPTRequest.SEARCH, chatId);
            getFoundEventFromCalendar(searchDetails[0], searchDetails[1], searchDetails[2], searchDetails[3], chatId);

        } else {
            String gptResponse = getResponseFromVoiceAndGPT(message, TypeGPTRequest.CREATING_EVENT, chatId);
            sessionData.put(chatId, gptResponse);
            // Send message with response and buttons for confirmation
            sendEventConfirmationMessage(chatId, gptResponse);
        }
    }

    private String[] extractDetailsFromVoiceAndGPT(Message message, TypeGPTRequest typeGPTRequest, String chatId) {
        String gptResponse = getResponseFromVoiceAndGPT(message, typeGPTRequest, chatId);
        String[] details = null;
        if (typeGPTRequest == TypeGPTRequest.ANALYTICS) {
            details = TextHandler.extractAnalyticDetails(gptResponse);
        } else if (typeGPTRequest == TypeGPTRequest.SEARCH) {
            details = TextHandler.extractSearchDetails(gptResponse);
        }
        new Thread(() -> sendResponseMessage(chatId, "Your request: " + gptResponse)).start();
        return details;
    }

    private String getResponseFromVoiceAndGPT(Message message, TypeGPTRequest typeGPTRequest, String chatId) {
        ChatGPTHadler chatGPTHadler = new ChatGPTHadler();
        String voiceText = getTextFromTelegramVoice(message, chatId);
        return chatGPTHadler.publicGetResponseFromChatGPT(voiceText, typeGPTRequest);
    }

    private String getTextFromTelegramVoice(Message message, String chatId) {
        String result = "";
        try {
            VoiceCommandHandler voiceCommandHandler = new VoiceCommandHandler();
            String fileId = message.getVoice().getFileId();
            String fileUrl = getFileUrl(fileId); // Get the URL of the voice message file
            result = voiceCommandHandler.convertVoiceToText(fileUrl); // Convertation voice to text
        } catch (TelegramApiException e) {
            sendResponseMessage(chatId, "Error receiving audio file from telegram.");
        } catch (IOException e) {
            sendResponseMessage(chatId, "Error processing voice message.");
        }
        return result;
    }

    private boolean isAuthorizedUser(String userId) {
        return userId.equals(TelegramBotConfig.getUserOneId()) || userId.equals(TelegramBotConfig.getUserTwoId());
    }

    public void sendEventConfirmationMessage(String chatId, String eventDetails) {
        // Create buttons
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText("Confirm");
        confirmButton.setCallbackData("confirm_event");

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("Cancel");
        cancelButton.setCallbackData("cancel_event");

        buttons.add(Arrays.asList(confirmButton, cancelButton));
        markup.setKeyboard(buttons);

        SendMessage message = new SendMessage(chatId, "Will be created:\n" + eventDetails);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getFileUrl(String fileId) throws TelegramApiException {
        GetFile getFile = new GetFile();
        getFile.setFileId(fileId);
        File telegramFile = execute(getFile);
        return "https://api.telegram.org/file/bot" + TelegramBotConfig.getBotToken() + "/" + telegramFile.getFilePath();
    }

    private void handleTextMessage(Message message) {
        String messageText = message.getText();
        String chatId = message.getChatId().toString();

        if (messageText.equals("/analytics")) {
            sendRequestForAnalytics(chatId);
        } else if (messageText.equals("/search")) {
            sendRequestForSearch(chatId);
        } else if (Constants.REQUEST_ANALYTICS.equals(sessionData.get(chatId + Constants.STATE))) {
            processAnalyticsRequest(messageText, chatId);
        } else if (Constants.REQUEST_SEARCH.equals(sessionData.get(chatId + Constants.STATE))) {
            processSearchRequest(messageText, chatId);
        } else if (messageText.equals("/help")) {
            sendHelp(chatId);
        } else {
            processEventCreation(messageText, chatId);
        }
    }

    private void sendHelp(String chatId) {
        String str = TextHandler.getTextHepl();
        sendResponseMessage(chatId, str);
    }

    private void processSearchRequest(String messageText, String chatId) {
        // Format of the message "yyyy-MM-dd / yyyy-MM-dd / TypeSearch / Keyword"
        String[] parts = messageText.split(" / ", 4);
        if (parts.length >= 2) {
            String startDate = parts[0] + " 00:00";
            String endDate = parts[1] + " 23:59";
            String keyword = "";
            String searchType = "all";
            if (parts.length == 3) {
                String str = parts[2];
                if (!"first".equals(str) && !"last".equals(str) && !"all".equals(str)) {
                    searchType = "all";
                    keyword = str;
                }
            } else if (parts.length == 4) {
                keyword = parts[2];
                searchType = parts[3];
            }
            getFoundEventFromCalendar(startDate, endDate, searchType, keyword, chatId);
        } else {
            sendResponseMessage(chatId, "Incorrect message format.");
        }
    }

    private SearchType getSearchTypeFromStr(String str) {
        SearchType searchType;
        if ("first".equals(str)) {
            searchType = SearchType.FIRST;
        } else if ("last".equals(str)) {
            searchType = SearchType.LAST;
        } else if ("all".equals(str)) {
            searchType = SearchType.ALL;
        } else {
            searchType = null;
        }
        return searchType;
    }

    private void processEventCreation(String messageText, String chatId) {
        // Format of the message "Date Time Description"
        String[] parts = messageText.split(" ", 3);
        if (parts.length == 3) {
            String dateStr = parts[0];
            String timeStr = parts[1];
            String description = parts[2];
            createCalendarEvent(dateStr, timeStr, "1", description, chatId);
        } else {
            sendResponseMessage(chatId, "Incorrect message format.");
        }
    }

    private void processAnalyticsRequest(String messageText, String chatId) {
        // Format of the message "yyyy-MM-dd yyyy-MM-dd Keyword"
        String[] parts = messageText.split(" ", 3);
        if (parts.length >= 2) {
            String startDate = parts[0] + " 00:00";
            String endDate = parts[1] + " 23:59";
            String keyword = (parts.length == 3) ? parts[2] : "";
            getAnalyticsFromCalendar(startDate, endDate, keyword, chatId);
        } else {
            sendResponseMessage(chatId, "Incorrect message format.");
        }
    }

    private void sendRequestForAnalytics(String chatId) {
        sessionData.put(chatId + Constants.STATE, Constants.REQUEST_ANALYTICS);
        sendResponseMessage(chatId, "Enter period and keyword (optional)");
    }

    private void sendRequestForSearch(String chatId) {
        sessionData.put(chatId + Constants.STATE, Constants.REQUEST_SEARCH);
        sendResponseMessage(chatId, "Enter period, keyword (optional) and type search (optional).");
    }

    private void getAnalyticsFromCalendar(String startDate, String endDate, String keyword, String chatId) {
        LocalDateTime startDateTime = LocalDateTime.parse(startDate,
                DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));
        LocalDateTime endDateTime = LocalDateTime.parse(endDate,
                DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));

        try {
            String response = GoogleCalendarService.analyticsEventsByKeyword(startDateTime, endDateTime, keyword);
            sendResponseMessage(chatId, response);
            sessionData.remove(chatId + Constants.STATE);
        } catch (Exception e) {
            sessionData.remove(chatId + Constants.STATE);
            sendResponseMessage(chatId, "Error collecting analytics.");
        }
    }

    private void getFoundEventFromCalendar(String startDate, String endDate, String searchTypeString, String keyword,
            String chatId) {
        LocalDateTime startDateTime = LocalDateTime.parse(startDate,
                DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));
        LocalDateTime endDateTime = LocalDateTime.parse(endDate,
                DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));
        SearchType searchType = getSearchTypeFromStr(searchTypeString);

        try {
            String response = GoogleCalendarService.searchEventInCalendar(startDateTime, endDateTime, keyword,
                    searchType);
            sendResponseMessage(chatId, response);
            sessionData.remove(chatId + Constants.STATE);
        } catch (Exception e) {
            sessionData.remove(chatId + Constants.STATE);
            sendResponseMessage(chatId, "Error searching events.");
        }
    }

    public void createCalendarEvent(String dateStr, String timeStr, String duration, String description,
            String chatId) {
        LocalDateTime startDateTime = LocalDateTime.parse(dateStr + " " + timeStr,
                DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));
        LocalDateTime endDateTime = startDateTime.plusMinutes(Long.parseLong(duration));

        try {
            GoogleCalendarService.createGoogleCalendarEvent(description, description, startDateTime, endDateTime);
            sendResponseMessage(chatId, "Event created in Google Calendar.");
        } catch (Exception e) {
            sendResponseMessage(chatId, "Error creating event.");
        }
    }

    public void sendResponseMessage(String chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        try {
            execute(message); // Sending a message
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

}
