package krpaivin.telcal.telegram;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.File;

import org.springframework.stereotype.Component;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;

import krpaivin.telcal.calendar.GoogleCalendarService;
import krpaivin.telcal.chatgpt.TypeGPTRequest;
import krpaivin.telcal.config.CalendarData;
import krpaivin.telcal.config.Constants;
import krpaivin.telcal.config.Messages;
import krpaivin.telcal.config.TelegramBotConfig;
import krpaivin.telcal.config.UserCalendar;
import krpaivin.telcal.data.UserAuthData;

@RequiredArgsConstructor
@Component
public class TelegramCalendar extends TelegramLongPollingBot {

    private final UserAuthData userAuthData;
    private final GoogleCalendarService googleCalendarService;
    private final Cache<String, String> sessionDataCache;
    private final Cache<String, UserCalendar> calendarSelectionCache;
    private final VoiceCommandHandler voiceCommandHandler;
    private final CalendarDataService calendarDataService;

    @Override
    public String getBotUsername() {
        return Messages.BOT_USERNAME;
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
        String userId = callbackQuery.getFrom().getUserName();

        if (isBotInMaintenanceMode(userId)) {
            sendResponseMessage(chatId, Messages.B0T_UNDERCONSTRUCTION);
            return;
        }

        switch (callbackQuery.getData()) {
            case Constants.BUTTON_CONFIRM_EVENT:
                confirmEvent(chatId, userId);
                break;
            case Constants.BUTTON_CANCEL_EVENT:
                cancelEvent(chatId);
                break;
            case Constants.BUTTON_ALL_SETTINGS:
                sendAuthorizationRequest(chatId);
                break;
            case Constants.BUTTON_KEYWORDS:
                sendSetKeywordsRequest(chatId);
                break;
            case Constants.BUTTON_DEFAULT_KEYWORD:
                sendSetDefaultKeywordsRequest(chatId);
                break;
            case Constants.BUTTON_COMPOUND_KEYWORDS:
                sendSetCompoundKeywordsRequest(chatId);
                break;
            case Constants.BUTTON_CLEAR_ALL_KEYWORDS:
                clearAllKeywordsRequest(chatId, userId);
                break;
            default:
                if (callbackQuery.getData().startsWith("Calendar/")) {
                    setUserCalendar(chatId, userId, callbackQuery.getData());
                }
        }
    }

    private void cancelEvent(String chatId) {
        sessionDataCache.invalidate(chatId);
        sessionDataCache.invalidate(chatId + Constants.STATE);
        sendResponseMessage(chatId, Messages.OPERATION_CANCEL);
    }

    private void confirmEvent(String chatId, String userId) {
        try {
            String gptResponse = sessionDataCache.getIfPresent(chatId);
            String[] eventDetails = TextHandler.extractEventDetails(gptResponse);
            calendarDataService.createCalendarEvent(eventDetails[0], eventDetails[1], eventDetails[2], eventDetails[3], userId);
            sendResponseMessage(chatId, Messages.EVENT_CREATED);
        } catch (Exception e) {
            sendResponseMessage(chatId, Messages.ERROR_CREATING_EVENT);
        }
        sessionDataCache.invalidate(chatId);
    }

    private void handleMessage(Message message) {
        String userId = message.getFrom().getUserName();
        String chatId = message.getChatId().toString();

        if (isBotInMaintenanceMode(userId)) {
            sendResponseMessage(chatId, Messages.B0T_UNDERCONSTRUCTION);
            return;
        }

        if (message.hasText()) {
            // Processing text message
            handleTextMessage(message, userId);
        } else if (message.hasVoice()) {
            // Processing voice message
            handleVoiceMessage(message, userId, chatId);
        }
    }

    private void handleVoiceMessage(Message message, String userId, String chatId) {
        try {
            String fileId = message.getVoice().getFileId();
            String fileUrl = getFileUrl(fileId);
            String response = "";

            if (Constants.REQUEST_ANALYTICS.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
                String[] analyticDetails = voiceCommandHandler.extractDetailsFromVoiceAndGPT(TypeGPTRequest.ANALYTICS, userId, fileUrl);
                sendResponseMessage(chatId, TextHandler.getAnalyticsMessageForResponse(analyticDetails));
                response = calendarDataService.getAnalyticsFromCalendar(analyticDetails[0], analyticDetails[1], analyticDetails[2],
                        chatId, userId);
                sendResponseMessage(chatId, response);

            } else if (Constants.REQUEST_SEARCH.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
                String[] searchDetails = voiceCommandHandler.extractDetailsFromVoiceAndGPT(TypeGPTRequest.SEARCH, userId, fileUrl);
                sendResponseMessage(chatId, TextHandler.getSearchMessageForResponse(searchDetails));
                response = calendarDataService.getFoundEventFromCalendar(searchDetails[0], searchDetails[1], searchDetails[2],
                        searchDetails[3], chatId, userId);
                sendResponseMessage(chatId, response);

            } else {
                response = voiceCommandHandler.getResponseFromVoiceAndGPT(TypeGPTRequest.CREATING_EVENT, userId, fileUrl);
                // Send message with response and buttons for confirmation
                if (response != null && !"".equals(response)) {
                    sessionDataCache.put(chatId, response);
                    sendEventConfirmationMessage(chatId, response);
                } else {
                    sendResponseMessage(chatId, Messages.ERROR_RECEIVING_AUDIO);
                }
            }
        } catch (IllegalArgumentException e) {
            sendResponseMessage(chatId, e.getMessage());
        } catch (GeneralSecurityException | TelegramApiException | IOException e) {
            sendResponseMessage(chatId, Messages.ERROR_RECEIVING_AUDIO);
        }
    }

    private boolean isBotInMaintenanceMode(String userId) {
        return TelegramBotConfig.getMaintenanceMode().equals("true")
                && !userId.equals(TelegramBotConfig.getUserOneId());
    }

    public void sendEventConfirmationMessage(String chatId, String eventDetails) {
        // Create buttons
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText(Messages.BUTTON_CONFIRM);
        confirmButton.setCallbackData(Constants.BUTTON_CONFIRM_EVENT);

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText(Messages.BUTTON_CANCEL);
        cancelButton.setCallbackData(Constants.BUTTON_CANCEL_EVENT);

        buttons.add(Arrays.asList(confirmButton, cancelButton));
        markup.setKeyboard(buttons);

        SendMessage message = new SendMessage(chatId, Messages.WILL_BE_CREATED + eventDetails);
        message.setReplyMarkup(markup);

        executeMessage(message);
    }

    private void handleTextMessage(Message message, String userId) {
        String messageText = message.getText();
        String chatId = message.getChatId().toString();

        if (messageText.equals(Messages.ANALYTICS)) {
            sendRequestForAnalytics(chatId);
        } else if (messageText.equals(Messages.SEARCH)) {
            sendRequestForSearch(chatId);
        } else if (messageText.equals(Messages.HELP)) {
            sendHelp(chatId);
        } else if (messageText.equals(Messages.START)) {
            sendAuthorizationRequest(chatId);
        } else if (messageText.equals(Messages.SETTING)) {
            sendSettingRequest(chatId);
        } else if (Constants.REQUEST_ANALYTICS.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
            sendAnalyticsRequest(messageText, chatId, userId);
        } else if (Constants.REQUEST_SEARCH.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
            sendSearchRequest(messageText, chatId, userId);
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
            requestEventCreation(messageText, chatId, userId);
        }
    }

    private void requestEventCreation(String messageText, String chatId, String userId) {
        try {
            calendarDataService.processEventCreation(messageText, userId);
            sendResponseMessage(chatId, Messages.EVENT_CREATED);
        } catch (IllegalArgumentException e) {
            sendResponseMessage(chatId, e.getMessage());
        } catch (GeneralSecurityException | IOException e) {
            sendResponseMessage(chatId, Messages.ERROR_CREATING);
        }
    }

    private void sendSearchRequest(String messageText, String chatId, String userId) {
        try {
            calendarDataService.processSearchRequest(messageText, chatId, userId);
        } catch (IllegalArgumentException e) {
            sendResponseMessage(chatId, e.getMessage());
        } catch (GeneralSecurityException | IOException e) {
            sendResponseMessage(chatId, Messages.ERROR_SEARCHING);
        }
    }

    private void sendAnalyticsRequest(String messageText, String chatId, String userId) {
        try {
            calendarDataService.processAnalyticsRequest(messageText, chatId, userId);
        } catch (IllegalArgumentException e) {
            sendResponseMessage(chatId, e.getMessage());
        }
    }

    private void sendChoiceOfSettingsMessage(String chatId) {
        // Create buttons
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        InlineKeyboardButton allSettingsButton = new InlineKeyboardButton();
        allSettingsButton.setText(Messages.BUTTON_SETTINGS);
        allSettingsButton.setCallbackData(Constants.BUTTON_ALL_SETTINGS);

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText(Messages.BUTTON_CANCEL);
        cancelButton.setCallbackData(Constants.BUTTON_CANCEL_EVENT);

        InlineKeyboardButton keywordsButton = new InlineKeyboardButton();
        keywordsButton.setText(Messages.BUTTON_KEYWORDS);
        keywordsButton.setCallbackData(Constants.BUTTON_KEYWORDS);

        InlineKeyboardButton defaultKeywordButton = new InlineKeyboardButton();
        defaultKeywordButton.setText(Messages.BUTTON_DEFAULT_KEYWORD);
        defaultKeywordButton.setCallbackData(Constants.BUTTON_DEFAULT_KEYWORD);

        InlineKeyboardButton compoundKeywordsButton = new InlineKeyboardButton();
        compoundKeywordsButton.setText(Messages.BUTTON_COMP_KEYWORDS);
        compoundKeywordsButton.setCallbackData(Constants.BUTTON_COMPOUND_KEYWORDS);

        InlineKeyboardButton clearAllKeywordsButton = new InlineKeyboardButton();
        clearAllKeywordsButton.setText(Messages.BUTTON_CLEAR_KEYWORDS);
        clearAllKeywordsButton.setCallbackData(Constants.BUTTON_CLEAR_ALL_KEYWORDS);

        buttons.add(Arrays.asList(allSettingsButton, cancelButton));
        buttons.add(Arrays.asList(keywordsButton, defaultKeywordButton, compoundKeywordsButton));
        buttons.add(Arrays.asList(clearAllKeywordsButton));
        markup.setKeyboard(buttons);

        SendMessage message = new SendMessage(chatId, Messages.WHAT_SETTINGS);
        message.setReplyMarkup(markup);

        executeMessage(message);
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
                    calendarButton.setCallbackData(Messages.CALENDAR + userId + "/" + entryHashMap.getKey());
                    row.add(calendarButton);
                }

                buttons.add(row);
            }

            markup.setKeyboard(buttons);

            SendMessage message = new SendMessage(chatId, Messages.SELECT_CALENDAR);
            message.setReplyMarkup(markup);

            executeMessage(message);
        } else {
            sendResponseMessage(chatId, Messages.ERROR_SAVING_CALENDAR);
        }
    }

    private void sendSettingRequest(String chatId) {
        sendChoiceOfSettingsMessage(chatId);
    }

    private void processSetCalendar(String messageText, String chatId, String userId) {
        sessionDataCache.invalidate(chatId + Constants.STATE);
        if (userAuthData.saveSelectedCalendar(userId, messageText)) {
            sendResponseMessage(chatId, Messages.CALENDAR_SUCCESS);
        } else {
            sendResponseMessage(chatId, Messages.ERROR_SAVING_CALENDAR);
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
                sendResponseMessage(chatId, Messages.CALENDAR_SUCCESS);
            } else {
                sendResponseMessage(chatId, Messages.ERROR_SAVING_CALENDAR);
            }
        } else {
            sendResponseMessage(chatId, Messages.ERROR_SAVING_CALENDAR);
        }
    }

    private void processAuthorizationRresponse(String messageText, String chatId, String userId) {
        String choiceCalendar = googleCalendarService.getAccessToCalendar(messageText, userId);
        sessionDataCache.invalidate(chatId + Constants.STATE);
        if ("".equals(choiceCalendar) || choiceCalendar.startsWith(Messages.ERROR)) {
            sendResponseMessage(chatId, choiceCalendar);
        } else {
            sendChoiceOfCalendarsMessage(chatId, userId);
        }
    }

    private void processSetCompoundKeywordsRequest(String messageText, String chatId, String userId) {
        sessionDataCache.invalidate(chatId + Constants.STATE);
        if (userAuthData.saveCompoundKeywords(userId, messageText)) {
            sendResponseMessage(chatId, Messages.COMP_KEYWORDS_SUCCESS);
        } else {
            sendResponseMessage(chatId, Messages.ERROR_COMP_KEYWORDS);
        }
    }

    private void processSetDefaultKeywordsRequest(String messageText, String chatId, String userId) {
        sessionDataCache.invalidate(chatId + Constants.STATE);
        if (userAuthData.saveDefaultKeywords(userId, messageText)) {
            sendResponseMessage(chatId, Messages.DEFAULT_KEYWORD_SUCCESS);
        } else {
            sendResponseMessage(chatId, Messages.ERROR_DEFAULT_KEYWORD);
        }
    }

    private void processSetKeywordsRequest(String messageText, String chatId, String userId) {
        sessionDataCache.invalidate(chatId + Constants.STATE);
        if (userAuthData.saveKeywords(userId, messageText)) {
            sendResponseMessage(chatId, Messages.KEYWORDS_SUCCESS);
        } else {
            sendResponseMessage(chatId, Messages.ERROR_KEYWORDS);
        }
    }

    private void clearAllKeywordsRequest(String chatId, String userId) {
        if (userAuthData.clearAllKeywords(userId)) {
            sendResponseMessage(chatId, Messages.KEYWORDS_CLEANED);
        } else {
            sendResponseMessage(chatId, Messages.ERROR_CLEANING_KEYWORDS);
        }
    }

    private void sendAuthorizationRequest(String chatId) {
        String url = googleCalendarService.getUrlForAuthorization();
        if (!"".equals(url)) {
            sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_AUTHORIZATION);
            sendResponseMessage(chatId, Messages.FOLLOW_LINK);
            sendResponseMessage(chatId, url);
        } else {
            sendResponseMessage(chatId, Messages.ERROR_AUTHORIZATION);
        }
    }

    private void sendHelp(String chatId) {
        String str = TextHandler.getTextHepl();
        sendResponseMessage(chatId, str);
    }

    private void sendRequestForAnalytics(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_ANALYTICS);
        sendResponseMessage(chatId, Messages.REQUEST_ANALYTICST);
    }

    private void sendRequestForSearch(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_SEARCH);
        sendResponseMessage(chatId, Messages.REQUEST_SEARCH);
    }

    private void sendSetCompoundKeywordsRequest(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_COMPOUND_KEYWORDS);
        sendResponseMessage(chatId, Messages.REQUEST_COMP_KEYWORDS);
    }

    private void sendSetDefaultKeywordsRequest(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_DEFAULT_KEYWORDS);
        sendResponseMessage(chatId, Messages.REQUEST_DEFAULT_KEYWORD);
    }

    private void sendSetKeywordsRequest(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_KEYWORDS);
        sendResponseMessage(chatId, Messages.REQUEST_KEYWORDS);
    }

    public void sendResponseMessage(String chatId, String text) {
        SendMessage message = new SendMessage(chatId, text);
        executeMessage(message);
    }

    public void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private String getFileUrl(String fileId) throws TelegramApiException {
        GetFile getFile = new GetFile();
        getFile.setFileId(fileId);
        File telegramFile = execute(getFile);
        return Messages.PATH_TG_API + TelegramBotConfig.getBotToken() + "/" + telegramFile.getFilePath();
    }

}
