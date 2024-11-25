package krpaivin.telcal.telegram;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.github.benmanes.caffeine.cache.Cache;

import krpaivin.telcal.calendar.GoogleCalendarService;
import krpaivin.telcal.calendar.SearchType;
import krpaivin.telcal.chatgpt.ChatGPTHadler;
import krpaivin.telcal.chatgpt.TypeGPTRequest;
import krpaivin.telcal.config.Constants;
import krpaivin.telcal.config.TelegramBotConfig;
import krpaivin.telcal.data.UserAuthData;
import lombok.RequiredArgsConstructor;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.File;

@RequiredArgsConstructor
@Component
public class TelegramCalendar extends TelegramLongPollingBot {

    private final UserAuthData userAuthData;
    private final GoogleCalendarService googleCalendarService;
    private final ChatGPTHadler chatGPTHadler;
    private final Cache<String, String> sessionDataCache;

    //private Map<String, String> sessionData = new HashMap<>(); // переписать на кэш

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
        CallbackQuery callbackQuery = update.getCallbackQuery();
        String chatId = callbackQuery.getMessage().getChatId().toString();
        String callData = callbackQuery.getData();
        String userId = callbackQuery.getFrom().getUserName();
        if (Constants.BUTTON_CONFIRM_EVENT.equals(callData)) {
            confirmEvent(chatId, userId);
        } else if (Constants.BUTTON_CANCEL_EVENT.equals(callData)) {
            cancelEvent(chatId);
        } else if (Constants.BUTTON_ALL_SETTINGS.equals(callData)) {
            sendAuthorizationRequest(chatId);
        }
    }

    private void cancelEvent(String chatId) {
        //sessionData.remove(chatId);
        sessionDataCache.invalidate(chatId);
        sendResponseMessage(chatId, "Operation canceled");
    }

    private void confirmEvent(String chatId, String userId) {
        try {
            //String gptResponse = sessionData.get(chatId);
            String gptResponse = sessionDataCache.getIfPresent(chatId);
            String[] eventDetails = TextHandler.extractEventDetails(gptResponse); // Extracting event details
            // Create a calendar event with a received data
            createCalendarEvent(eventDetails[0], eventDetails[1], eventDetails[2], eventDetails[3], chatId, userId);
            // Remove session data
        } catch (Exception e) {
            sendResponseMessage(chatId, "Error creating event in calendar.");
        }
        //sessionData.remove(chatId);
        sessionDataCache.invalidate(chatId);
    }

    private void handleMessage(Message message) {
        String userId = message.getFrom().getUserName();
        String chatId = message.getChatId().toString();

        if (message.hasText()) {
            // Processing text message
            handleTextMessage(message, userId);
        } else if (message.hasVoice()) {
            // Processing voice message
            handleVoiceMessage(message, chatId, userId);
        }
    }

    private void handleVoiceMessage(Message message, String chatId, String userId) {

        //if (Constants.REQUEST_ANALYTICS.equals(sessionData.get(chatId + Constants.STATE))) {
        if (Constants.REQUEST_ANALYTICS.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {

            String[] analyticDetails = extractDetailsFromVoiceAndGPT(message, TypeGPTRequest.ANALYTICS, chatId);
            getAnalyticsFromCalendar(analyticDetails[0], analyticDetails[1], analyticDetails[2], chatId, userId);

        //} else if (Constants.REQUEST_SEARCH.equals(sessionData.get(chatId + Constants.STATE))) {
        } else if (Constants.REQUEST_SEARCH.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {

            String[] searchDetails = extractDetailsFromVoiceAndGPT(message, TypeGPTRequest.SEARCH, chatId);
            getFoundEventFromCalendar(searchDetails[0], searchDetails[1], searchDetails[2], searchDetails[3], chatId,
                    userId);

        } else {
            String gptResponse = getResponseFromVoiceAndGPT(message, TypeGPTRequest.CREATING_EVENT, chatId);
            //sessionData.put(chatId, gptResponse);
            sessionDataCache.put(chatId, gptResponse);
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

    public void sendEventConfirmationMessage(String chatId, String eventDetails) {
        // Create buttons
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText("Confirm");
        confirmButton.setCallbackData(Constants.BUTTON_CONFIRM_EVENT);

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("Cancel");
        cancelButton.setCallbackData(Constants.BUTTON_CANCEL_EVENT);

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

    private void handleTextMessage(Message message, String userId) {
        String messageText = message.getText();
        String chatId = message.getChatId().toString();

        if (messageText.equals("/analytics")) {
            sendRequestForAnalytics(chatId);
        } else if (messageText.equals("/search")) {
            sendRequestForSearch(chatId);
        //} else if (Constants.REQUEST_ANALYTICS.equals(sessionData.get(chatId + Constants.STATE))) {
        } else if (Constants.REQUEST_ANALYTICS.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
            processAnalyticsRequest(messageText, chatId, userId);
        //} else if (Constants.REQUEST_SEARCH.equals(sessionData.get(chatId + Constants.STATE))) {
        } else if (Constants.REQUEST_SEARCH.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
            processSearchRequest(messageText, chatId, userId);
        //} else if (Constants.REQUEST_AUTHORIZATION.equals(sessionData.get(chatId + Constants.STATE))) {
        } else if (Constants.REQUEST_AUTHORIZATION.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
            processAuthorizationRresponse(messageText, chatId, userId);
        //} else if (Constants.REQUEST_SET_CALENDAR.equals(sessionData.get(chatId + Constants.STATE))) {
        } else if (Constants.REQUEST_SET_CALENDAR.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
            processSetCalendar(messageText, chatId, userId);
        } else if (messageText.equals("/help")) {
            sendHelp(chatId);
        } else if (messageText.equals("/start")) {
            sendAuthorizationRequest(chatId);
        } else if (messageText.equals("/setting")) {
            sendSettingRequest(chatId);
        } else {
            processEventCreation(messageText, chatId, userId);
        }
    }

    private void sendChoiceOfSettingsMessage(String chatId) {
        // Create buttons
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        InlineKeyboardButton allSettingsButton = new InlineKeyboardButton();
        allSettingsButton.setText("Confirm");
        allSettingsButton.setCallbackData(Constants.BUTTON_ALL_SETTINGS);

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("Cancel");
        cancelButton.setCallbackData(Constants.BUTTON_CANCEL_EVENT);

        buttons.add(Arrays.asList(allSettingsButton, cancelButton));
        markup.setKeyboard(buttons);

        SendMessage message = new SendMessage(chatId, "Do you want to update all settings?");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendSettingRequest(String chatId) {
        sendChoiceOfSettingsMessage(chatId);
    }

    private void processSetCalendar(String messageText, String chatId, String userId) {
        //sessionData.remove(chatId + Constants.REQUEST_SET_CALENDAR);
        sessionDataCache.invalidate(chatId + Constants.REQUEST_SET_CALENDAR);
        if (userAuthData.saveSelectedCalendar(userId, messageText)) {
            sendResponseMessage(chatId, "Calendar access has been successfully configured.");
        } else {
            sendResponseMessage(chatId, "Error saving calendar data.");
        }
    }

    private void processAuthorizationRresponse(String messageText, String chatId, String userId) {
        String choiceCalendar = googleCalendarService.getAccessToCalendar(messageText, userId);
        //sessionData.remove(chatId + Constants.REQUEST_AUTHORIZATION);
        sessionDataCache.invalidate(chatId + Constants.REQUEST_AUTHORIZATION);
        if ("".equals(choiceCalendar) || choiceCalendar.startsWith("Error")) {
            sendResponseMessage(chatId, choiceCalendar);
        } else {
            //sessionData.put(chatId + Constants.STATE, Constants.REQUEST_SET_CALENDAR);
            sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_SET_CALENDAR);
            sendResponseMessage(chatId, choiceCalendar);
        }
    }

    private void sendAuthorizationRequest(String chatId) {
        String url = googleCalendarService.getUrlForAuthorization();
        if (!"".equals(url)) {
            //sessionData.put(chatId + Constants.STATE, Constants.REQUEST_AUTHORIZATION);
            sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_AUTHORIZATION);
            sendResponseMessage(chatId, "Follow the link, copy the code and send it to the bot");
            sendResponseMessage(chatId, url);
        } else {
            sendResponseMessage(chatId, "Error retrieving authorization data.");
        }
    }

    private void sendHelp(String chatId) {
        String str = TextHandler.getTextHepl();
        sendResponseMessage(chatId, str);
    }

    private void processSearchRequest(String messageText, String chatId, String userId) {
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
            getFoundEventFromCalendar(startDate, endDate, searchType, keyword, chatId, userId);
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

    private void processEventCreation(String messageText, String chatId, String userId) {
        // Format of the message "Date Time Description"
        String[] parts = messageText.split(" ", 3);
        if (parts.length == 3) {
            String dateStr = parts[0];
            String timeStr = parts[1];
            String description = parts[2];
            createCalendarEvent(dateStr, timeStr, "1", description, chatId, userId);
        } else {
            sendResponseMessage(chatId, "Incorrect message format.");
        }
    }

    private void processAnalyticsRequest(String messageText, String chatId, String userId) {
        // Format of the message "yyyy-MM-dd yyyy-MM-dd Keyword"
        String[] parts = messageText.split(" ", 3);
        if (parts.length >= 2) {
            String startDate = parts[0] + " 00:00";
            String endDate = parts[1] + " 23:59";
            String keyword = (parts.length == 3) ? parts[2] : "";
            getAnalyticsFromCalendar(startDate, endDate, keyword, chatId, userId);
        } else {
            sendResponseMessage(chatId, "Incorrect message format.");
        }
    }

    private void sendRequestForAnalytics(String chatId) {
        //sessionData.put(chatId + Constants.STATE, Constants.REQUEST_ANALYTICS);
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_ANALYTICS);
        sendResponseMessage(chatId, "Send message with period and keyword (optional)");
    }

    private void sendRequestForSearch(String chatId) {
        //sessionData.put(chatId + Constants.STATE, Constants.REQUEST_SEARCH);
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_SEARCH);
        sendResponseMessage(chatId, "Send message with period, keyword (optional) and type search (optional).");
    }

    private void getAnalyticsFromCalendar(String startDate, String endDate, String keyword, String chatId,
            String userId) {
        LocalDateTime startDateTime = LocalDateTime.parse(startDate,
                DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));
        LocalDateTime endDateTime = LocalDateTime.parse(endDate,
                DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));

        //sessionData.remove(chatId + Constants.STATE);
        sessionDataCache.invalidate(chatId + Constants.STATE);
        try {
            String response = googleCalendarService.analyticsEventsByKeyword(startDateTime, endDateTime, keyword, userId);
            sendResponseMessage(chatId, response);
        } catch (Exception e) {
            sendResponseMessage(chatId, "Error collecting analytics.");
        }
    }

    private void getFoundEventFromCalendar(String startDate, String endDate, String searchTypeString, String keyword,
            String chatId, String userId) {
        LocalDateTime startDateTime = LocalDateTime.parse(startDate,
                DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));
        LocalDateTime endDateTime = LocalDateTime.parse(endDate,
                DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));
        SearchType searchType = getSearchTypeFromStr(searchTypeString);

        //sessionData.remove(chatId + Constants.STATE);
        sessionDataCache.invalidate(chatId + Constants.STATE);
        try {
            String response = googleCalendarService.searchEventInCalendar(startDateTime, endDateTime, keyword,
                    searchType, userId);
            sendResponseMessage(chatId, response);
        } catch (Exception e) {
            sendResponseMessage(chatId, "Error searching events.");
        }
    }

    public void createCalendarEvent(String dateStr, String timeStr, String duration, String description,
            String chatId, String userId) {
        LocalDateTime startDateTime = LocalDateTime.parse(dateStr + " " + timeStr,
                DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));
        LocalDateTime endDateTime = startDateTime.plusMinutes(Long.parseLong(duration));

        try {
            googleCalendarService.createGoogleCalendarEvent(description, description, startDateTime, endDateTime,
                    userId);
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
