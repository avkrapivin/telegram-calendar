package krpaivin.telcal.telegram;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
import krpaivin.telcal.config.CalendarData;
import krpaivin.telcal.config.Constants;
import krpaivin.telcal.config.TelegramBotConfig;
import krpaivin.telcal.config.UserCalendar;
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
    private final Cache<String, UserCalendar> calendarSelectionCache;

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
        } else if (Constants.BUTTON_KEYWORDS.equals(callData)) {
            sendSetKeywordsRequest(chatId);
        } else if (Constants.BUTTON_DEFAULT_KEYWORD.equals(callData)) {
            sendSetDefaultKeywordsRequest(chatId);
        } else if (Constants.BUTTON_COMPOUND_KEYWORDS.equals(callData)) {
            sendSetCompoundKeywordsRequest(chatId);
        } else if (callData != null && callData.length() >= 9
                && "Calendar/".equals(callData.substring(0, 9))) {
            setUserCalendar(chatId, userId, callData);
        }
    }

    private void cancelEvent(String chatId) {
        sessionDataCache.invalidate(chatId);
        sessionDataCache.invalidate(chatId + Constants.STATE);
        sendResponseMessage(chatId, "Operation canceled");
    }

    private void confirmEvent(String chatId, String userId) {
        try {
            String gptResponse = sessionDataCache.getIfPresent(chatId);
            String[] eventDetails = TextHandler.extractEventDetails(gptResponse); // Extracting event details
            // Create a calendar event with a received data
            createCalendarEvent(eventDetails[0], eventDetails[1], eventDetails[2], eventDetails[3], chatId, userId);
            // Remove session data
        } catch (Exception e) {
            sendResponseMessage(chatId, "Error creating event in calendar.");
        }
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

        if (Constants.REQUEST_ANALYTICS.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {

            String[] analyticDetails = extractDetailsFromVoiceAndGPT(message, TypeGPTRequest.ANALYTICS, chatId, userId);
            getAnalyticsFromCalendar(analyticDetails[0], analyticDetails[1], analyticDetails[2], chatId, userId);

        } else if (Constants.REQUEST_SEARCH.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {

            String[] searchDetails = extractDetailsFromVoiceAndGPT(message, TypeGPTRequest.SEARCH, chatId, userId);
            getFoundEventFromCalendar(searchDetails[0], searchDetails[1], searchDetails[2], searchDetails[3], chatId,
                    userId);

        } else {
            String gptResponse = getResponseFromVoiceAndGPT(message, TypeGPTRequest.CREATING_EVENT, chatId, userId);
            sessionDataCache.put(chatId, gptResponse);
            // Send message with response and buttons for confirmation
            sendEventConfirmationMessage(chatId, gptResponse);
        }
    }

    private String[] extractDetailsFromVoiceAndGPT(Message message, TypeGPTRequest typeGPTRequest, String chatId,
            String userId) {
        String gptResponse = getResponseFromVoiceAndGPT(message, typeGPTRequest, chatId, userId);
        String[] details = null;
        if (typeGPTRequest == TypeGPTRequest.ANALYTICS) {
            details = TextHandler.extractAnalyticDetails(gptResponse);
        } else if (typeGPTRequest == TypeGPTRequest.SEARCH) {
            details = TextHandler.extractSearchDetails(gptResponse);
        }
        new Thread(() -> sendResponseMessage(chatId, "Your request: " + gptResponse)).start();
        return details;
    }

    private String getResponseFromVoiceAndGPT(Message message, TypeGPTRequest typeGPTRequest, String chatId,
            String userId) {
        String voiceText = getTextFromTelegramVoice(message, chatId);
        return chatGPTHadler.publicGetResponseFromChatGPT(voiceText, typeGPTRequest, userId);
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
        } else if (messageText.equals("/help")) {
            sendHelp(chatId);
        } else if (messageText.equals("/start")) {
            sendAuthorizationRequest(chatId);
        } else if (messageText.equals("/setting")) {
            sendSettingRequest(chatId);
        } else if (Constants.REQUEST_ANALYTICS.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
            processAnalyticsRequest(messageText, chatId, userId);
        } else if (Constants.REQUEST_SEARCH.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
            processSearchRequest(messageText, chatId, userId);
        } else if (Constants.REQUEST_AUTHORIZATION.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
            processAuthorizationRresponse(messageText, chatId, userId);
        } else if (Constants.REQUEST_SET_CALENDAR.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
            processSetCalendar(messageText, chatId, userId);
        } else if (Constants.REQUEST_KEYWORDS.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
            processSetKeywordsRequest(messageText, chatId, userId);
        } else if (Constants.REQUEST_DEFAULT_KEYWORDS.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
            processSetDefaultKeywordsRequest(messageText, chatId, userId);
        } else if (Constants.REQUEST_COMPOUND_KEYWORDS
                .equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
            processSetCompoundKeywordsRequest(messageText, chatId, userId);
        } else {
            processEventCreation(messageText, chatId, userId);
        }
    }

    private void sendChoiceOfSettingsMessage(String chatId) {
        // Create buttons
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        InlineKeyboardButton allSettingsButton = new InlineKeyboardButton();
        allSettingsButton.setText("Connection settings");
        allSettingsButton.setCallbackData(Constants.BUTTON_ALL_SETTINGS);

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("Cancel");
        cancelButton.setCallbackData(Constants.BUTTON_CANCEL_EVENT);

        InlineKeyboardButton keywordsButton = new InlineKeyboardButton();
        keywordsButton.setText("Keywords");
        keywordsButton.setCallbackData(Constants.BUTTON_KEYWORDS);

        InlineKeyboardButton defaultKeywordButton = new InlineKeyboardButton();
        defaultKeywordButton.setText("Default keyword");
        defaultKeywordButton.setCallbackData(Constants.BUTTON_DEFAULT_KEYWORD);

        InlineKeyboardButton compoundKeywordsButton = new InlineKeyboardButton();
        compoundKeywordsButton.setText("Compound keywords");
        compoundKeywordsButton.setCallbackData(Constants.BUTTON_COMPOUND_KEYWORDS);

        buttons.add(Arrays.asList(allSettingsButton, cancelButton));
        buttons.add(Arrays.asList(keywordsButton, defaultKeywordButton, compoundKeywordsButton));
        markup.setKeyboard(buttons);

        SendMessage message = new SendMessage(chatId, "What settings do you want to set?");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendChoiceOfCalendarsMessage(String chatId, String userId) {
        // Create buttons
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        UserCalendar userCalendar = calendarSelectionCache.getIfPresent(userId);

        if (userCalendar != null) {
            Map<Integer, CalendarData> mapOfCalendars = userCalendar.getObjects();
            for (Entry<Integer, CalendarData> entryHashMap : mapOfCalendars.entrySet()) {
                Map<String, String> innerMap = entryHashMap.getValue().getAttributes();
                List<InlineKeyboardButton> row = new ArrayList<>();

                for (Entry<String, String> entryInnerMap : innerMap.entrySet()) {
                    InlineKeyboardButton calendarButton = new InlineKeyboardButton();
                    calendarButton.setText(entryInnerMap.getValue());
                    calendarButton.setCallbackData("Calendar/" + userId + "/" + entryHashMap.getKey());
                    row.add(calendarButton);
                }

                buttons.add(row);
            }

            markup.setKeyboard(buttons);

            SendMessage message = new SendMessage(chatId, "Select a calendar:");
            message.setReplyMarkup(markup);

            try {
                execute(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            sendResponseMessage(chatId, "Error saving calendar data.");
        }
    }

    private void sendSettingRequest(String chatId) {
        sendChoiceOfSettingsMessage(chatId);
    }

    private void processSetCalendar(String messageText, String chatId, String userId) {
        sessionDataCache.invalidate(chatId + Constants.STATE);
        if (userAuthData.saveSelectedCalendar(userId, messageText)) {
            sendResponseMessage(chatId, "Calendar access has been successfully configured.");
        } else {
            sendResponseMessage(chatId, "Error saving calendar data.");
        }
    }

    private void setUserCalendar(String chatId, String userId, String callData) {
        String[] arrayData = callData.split("/");
        UserCalendar userCalendar = calendarSelectionCache.getIfPresent(userId);
        calendarSelectionCache.invalidate(userId);

        if (arrayData.length == 3 && arrayData[1].equals(userId) && userCalendar != null) {
            Map<Integer, CalendarData> calendarList = userCalendar.getObjects();
            CalendarData calendarData = calendarList.get(Integer.parseInt(arrayData[2]));
            Map<String, String> calendarMap = calendarData.getAttributes();
            String calendarId = "";
            for (Entry<String, String> entryHashMap : calendarMap.entrySet()) {
                calendarId = entryHashMap.getKey();
            }
            if (userAuthData.saveSelectedCalendar(userId, calendarId)) {
                sendResponseMessage(chatId, "Calendar access has been successfully configured.");
            } else {
                sendResponseMessage(chatId, "Error saving calendar data.");
            }
        } else {
            sendResponseMessage(chatId, "Error saving calendar data.");
        }
    }

    private void processAuthorizationRresponse(String messageText, String chatId, String userId) {
        String choiceCalendar = googleCalendarService.getAccessToCalendar(messageText, userId);
        sessionDataCache.invalidate(chatId + Constants.STATE);
        if ("".equals(choiceCalendar) || choiceCalendar.startsWith("Error")) {
            sendResponseMessage(chatId, choiceCalendar);
        } else {
            sendChoiceOfCalendarsMessage(chatId, userId);
        }
    }

    private void processSetCompoundKeywordsRequest(String messageText, String chatId, String userId) {
        sessionDataCache.invalidate(chatId + Constants.STATE);
        if (userAuthData.saveCompoundKeywords(userId, messageText)) {
            sendResponseMessage(chatId, "Compound keywords access has been successfully configured.");
        } else {
            sendResponseMessage(chatId, "Error saving compound keywords data.");
        }
    }

    private void processSetDefaultKeywordsRequest(String messageText, String chatId, String userId) {
        sessionDataCache.invalidate(chatId + Constants.STATE);
        if (userAuthData.saveDefaultKeywords(userId, messageText)) {
            sendResponseMessage(chatId, "Default keyword access has been successfully configured.");
        } else {
            sendResponseMessage(chatId, "Error saving default keyword data.");
        }
    }

    private void processSetKeywordsRequest(String messageText, String chatId, String userId) {
        sessionDataCache.invalidate(chatId + Constants.STATE);
        if (userAuthData.saveKeywords(userId, messageText)) {
            sendResponseMessage(chatId, "Keywords access has been successfully configured.");
        } else {
            sendResponseMessage(chatId, "Error saving keywords data.");
        }
    }

    private void sendAuthorizationRequest(String chatId) {
        String url = googleCalendarService.getUrlForAuthorization();
        if (!"".equals(url)) {
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
        boolean isTrueFormat = TextHandler.checkFormatSearchRequest(messageText);

        if (isTrueFormat) {
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
        boolean isTrueFormat = TextHandler.checkFormatEventCreation(messageText);

        if (isTrueFormat) {
            String[] parts = messageText.split(" ", 3);
            if (parts.length == 3) {
                String dateStr = parts[0];
                String timeStr = parts[1];
                String description = parts[2];
                createCalendarEvent(dateStr, timeStr, "1", description, chatId, userId);
            } else {
                sendResponseMessage(chatId, "Incorrect message format.");
            }
        } else {
            sendResponseMessage(chatId, "Incorrect message format.");
        }
    }

    private void processAnalyticsRequest(String messageText, String chatId, String userId) {
        // Format of the message "yyyy-MM-dd yyyy-MM-dd Keyword"
        boolean isTrueFormat = TextHandler.checkFormatAnalyticsRequest(messageText);

        if (isTrueFormat) {
            String[] parts = messageText.split(" ", 3);
            if (parts.length >= 2) {
                String startDate = parts[0] + " 00:00";
                String endDate = parts[1] + " 23:59";
                String keyword = (parts.length == 3) ? parts[2] : "";
                getAnalyticsFromCalendar(startDate, endDate, keyword, chatId, userId);
            } else {
                sendResponseMessage(chatId, "Incorrect message format.");
            }
        } else {
            sendResponseMessage(chatId, "Incorrect message format.");
        }
    }

    private void sendRequestForAnalytics(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_ANALYTICS);
        sendResponseMessage(chatId, "Send message with period and keyword (optional)");
    }

    private void sendRequestForSearch(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_SEARCH);
        sendResponseMessage(chatId, "Send message with period, keyword (optional) and type search (optional).");
    }

    private void sendSetCompoundKeywordsRequest(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_COMPOUND_KEYWORDS);
        sendResponseMessage(chatId, "Enter keywords to compound. Groups of words are separated by commas." +
                "For example: \"Partner1 Partner2, My family\" means that the words \"Partner1 Partner2\" will be counted "
                +
                "as one keyword and \"My family\" will be counted as one (other) keyword.");
    }

    private void sendSetDefaultKeywordsRequest(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_DEFAULT_KEYWORDS);
        sendResponseMessage(chatId, "Enter a default keyword that will be automatically set " +
                "in cases where the keyword is missing.");
    }

    private void sendSetKeywordsRequest(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_KEYWORDS);
        sendResponseMessage(chatId, "Enter, separated by commas, keywords that will be set at the beginning " +
                "of the description of your event. For example, you have a shared calendar and you set the " +
                "keywords: \"Mike, Teresa\". Then you send a request: \"Tomorrow at 11 go to the store, Mike\". " +
                "The request will be processed and an event will be created for the corresponding date with the " +
                "description \"Mike. Go to the store\".");
    }

    private void getAnalyticsFromCalendar(String startDate, String endDate, String keyword, String chatId,
            String userId) {
        LocalDateTime startDateTime = LocalDateTime.parse(startDate,
                DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));
        LocalDateTime endDateTime = LocalDateTime.parse(endDate,
                DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));

        sessionDataCache.invalidate(chatId + Constants.STATE);
        try {
            String response = googleCalendarService.analyticsEventsByKeyword(startDateTime, endDateTime, keyword,
                    userId);
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
            // if (e.getMessage().contains("bot was blocked by the user")) {
            //     System.out.println("Пользователь заблокировал бота. ID пользователя: " + userId);
            // } else {
                e.printStackTrace();
            //}
        }
    }

}
