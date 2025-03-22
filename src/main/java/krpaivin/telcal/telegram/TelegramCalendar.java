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
import com.google.api.client.auth.oauth2.TokenResponseException;

import lombok.RequiredArgsConstructor;

import krpaivin.telcal.calendar.GoogleCalendarService;
import krpaivin.telcal.chatgpt.TypeGPTRequest;
import krpaivin.telcal.config.CalendarData;
import krpaivin.telcal.config.Constants;
import krpaivin.telcal.config.Messages;
import krpaivin.telcal.config.TelegramProperties;
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
    private final TelegramProperties telegramProperties;

    @Override
    public String getBotUsername() {
        return Messages.BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return telegramProperties.getBotToken();
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

    /**
     * Handles a callback query from the user.
     * 
     * @param update the update containing the callback query.
     */
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
            case Constants.BUTTON_SUBMIT:
                sendSubmitRequest(chatId);
                break;
            default:
                if (callbackQuery.getData().startsWith("Calendar/")) {
                    setUserCalendar(chatId, userId, callbackQuery.getData());
                }
        }
    }

    /**
     * Sends a request for the user to submit their Gmail address.
     *
     * This method updates the session data cache for the specified user
     * by storing the current state as 'REQUEST_SUBMIT'. It then sends a
     * response message to the user, prompting them to provide their
     * Gmail address.
     *
     * @param chatId The unique identifier of the user (chat) to whom the
     *               request message will be sent.
     */
    private void sendSubmitRequest(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_SUBMIT);
        sendResponseMessage(chatId, Messages.SEND_YOUR_GMAIL_ADDRESS);
    }

    /**
     * Cancels the current event creation process for the user.
     * 
     * @param chatId the chat ID of the user.
     */
    private void cancelEvent(String chatId) {
        sessionDataCache.invalidate(chatId);
        sessionDataCache.invalidate(chatId + Constants.STATE);
        sendResponseMessage(chatId, Messages.OPERATION_CANCEL);
    }

    /**
     * Confirms the creation of an event using details stored in the session.
     * 
     * @param chatId the chat ID of the user.
     * @param userId the ID of the user.
     */
    private void confirmEvent(String chatId, String userId) {
        try {
            String gptResponse = sessionDataCache.getIfPresent(chatId);
            String[] eventDetails = TextHandler.extractEventDetails(gptResponse);
            calendarDataService.createCalendarEvent(eventDetails[0], eventDetails[1], eventDetails[2], eventDetails[3],
                    userId);
            sendResponseMessage(chatId, Messages.EVENT_CREATED);
        } catch (TokenResponseException e) {
            sendResponseMessage(chatId, Messages.ERROR_INVALID_TOKEN);
        } catch (Exception e) {
            sendResponseMessage(chatId, Messages.ERROR_CREATING_EVENT);
        }
        sessionDataCache.invalidate(chatId);
    }

    /**
     * Handles incoming text or voice messages from the user.
     * 
     * @param message the message to be processed.
     */
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

    /**
     * Handles incoming voice messages from the user.
     * 
     * @param message the voice message to be processed.
     * @param userId  the ID of the user.
     * @param chatId  the chat ID of the user.
     */
    private void handleVoiceMessage(Message message, String userId, String chatId) {
        try {
            String fileId = message.getVoice().getFileId();
            String fileUrl = getFileUrl(fileId);
            String response = "";

            if (Constants.REQUEST_ANALYTICS.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
                String[] analyticDetails = voiceCommandHandler.extractDetailsFromVoiceAndGPT(TypeGPTRequest.ANALYTICS,
                        userId, fileUrl);
                sendResponseMessage(chatId, TextHandler.getAnalyticsMessageForResponse(analyticDetails));
                response = calendarDataService.getAnalyticsFromCalendar(analyticDetails[0], analyticDetails[1],
                        analyticDetails[2],
                        chatId, userId);
                sendResponseMessage(chatId, response);

            } else if (Constants.REQUEST_SEARCH.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
                String[] searchDetails = voiceCommandHandler.extractDetailsFromVoiceAndGPT(TypeGPTRequest.SEARCH,
                        userId, fileUrl);
                sendResponseMessage(chatId, TextHandler.getSearchMessageForResponse(searchDetails));
                response = calendarDataService.getFoundEventFromCalendar(searchDetails[0], searchDetails[1],
                        searchDetails[2],
                        searchDetails[3], chatId, userId);
                sendResponseMessage(chatId, response);

            } else {
                response = voiceCommandHandler.getResponseFromVoiceAndGPT(TypeGPTRequest.CREATING_EVENT, userId,
                        fileUrl);
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

    /**
     * Checks if the bot is currently in maintenance mode for a specific user.
     * 
     * @param userId the ID of the user.
     * @return true if the bot is in maintenance mode and the user is not exempt;
     *         false otherwise.
     */
    private boolean isBotInMaintenanceMode(String userId) {
        return telegramProperties.getMaintenanceMode().equals("true")
                && !userId.equals(telegramProperties.getUserOneId());
    }

    /**
     * Sends a confirmation message to the user for the event creation.
     * 
     * @param chatId       the chat ID of the user.
     * @param eventDetails the details of the event to be created.
     */
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

    /**
     * Handles text messages received from the user.
     * 
     * @param message the text message to be processed.
     * @param userId  the ID of the user.
     */
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
        } else if (Constants.REQUEST_SUBMIT.equals(sessionDataCache.getIfPresent(chatId + Constants.STATE))) {
            sendSubmitRequest(messageText, chatId, userId);
        } else if (messageText.startsWith(Messages.SUMBIT_RESPONSE) && userId.equals(telegramProperties.getUserOneId())) {
            sendSubmitResponse(messageText);
        } else {
            requestEventCreation(messageText, chatId, userId);
        }
    }

    /**
     * Sends a response based on the user's reply to a previous request.
     *
     * This method takes a message containing the user's chat ID and their
     * response (either "ok" or "no"). Depending on the user's reply, it sends
     * an appropriate response message to the user. If the reply is "ok",
     * a success message is sent; if "no", a denial message is sent.
     * If the reply is anything else, that reply is sent as the response.
     *
     * @param messageText The text message received from the user, expected to
     *                    contain the chat ID and the user's response in the
     *                    format: "<command> <userChatId> <replyMessage>".
     */
    private void sendSubmitResponse(String messageText) {
        String[] parts = messageText.split(" ", 3);
        if (parts.length == 3) {
            String userChatId = (parts[1].strip());
            String replyMessage = parts[2].strip();
            if ("ok".equals(replyMessage)) {
                sendResponseMessage(userChatId, Messages.ACCESS_SUCCESSFUL);
            } else if ("no".equals(replyMessage)) {
                sendResponseMessage(userChatId, Messages.ACCESS_DENIED);
            } else {
                sendResponseMessage(userChatId, replyMessage);
            }
        }
    }

    /**
     * Sends a notification of a new request to the administrator.
     *
     * This method invalidates the current state for the specified chat ID
     * and sends a notification message to the administrator containing
     * details about the new request. The message includes the user's ID,
     * chat ID, and the content of the message submitted by the user.
     *
     * @param messageText The text message submitted by the user, which will
     *                    be included in the notification to the administrator.
     * @param chatId      The unique identifier of the user's chat, used for
     *                    tracking the session state.
     * @param userId      The unique identifier of the user making the request,
     *                    used to identify the requester in the notification.
     */
    private void sendSubmitRequest(String messageText, String chatId, String userId) {
        sessionDataCache.invalidate(chatId + Constants.STATE);

        sendResponseMessage(telegramProperties.getAdminChatid(),
                "New request. User id: " + userId + ". Chat id: " + chatId + ". Message: " + messageText);
    }

    /**
     * Processes a request to create an event based on the provided message text.
     * This method interacts with the calendar data service to handle event creation
     * and sends an appropriate response back to the user.
     *
     * @param messageText the text containing the details of the event to be created
     * @param chatId      the ID of the chat where the response message is sent
     * @param userId      the ID of the user requesting the event creation
     */
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

    /**
     * Sends a search request based on the provided message text, chat ID, and user
     * ID.
     * 
     * @param messageText the text message containing the search query
     * @param chatId      the ID of the chat where the message is sent
     * @param userId      the ID of the user initiating the search
     */
    private void sendSearchRequest(String messageText, String chatId, String userId) {
        try {
            calendarDataService.processSearchRequest(messageText, chatId, userId);
        } catch (IllegalArgumentException e) {
            sendResponseMessage(chatId, e.getMessage());
        } catch (GeneralSecurityException | IOException e) {
            sendResponseMessage(chatId, Messages.ERROR_SEARCHING);
        }
    }

    /**
     * Sends an analytics request based on the provided message text, chat ID, and
     * user ID.
     * 
     * @param messageText the text message containing the analytics query
     * @param chatId      the ID of the chat where the message is sent
     * @param userId      the ID of the user requesting analytics
     */
    private void sendAnalyticsRequest(String messageText, String chatId, String userId) {
        try {
            calendarDataService.processAnalyticsRequest(messageText, chatId, userId);
        } catch (IllegalArgumentException e) {
            sendResponseMessage(chatId, e.getMessage());
        }
    }

    /**
     * Sends a message to the user to choose various settings options.
     * 
     * @param chatId the ID of the chat where the message is sent
     */
    private void sendChoiceOfSettingsMessage(String chatId) {
        // Create buttons
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        InlineKeyboardButton submitButton = new InlineKeyboardButton();
        submitButton.setText(Messages.BUTTON_SUBMIT);
        submitButton.setCallbackData(Constants.BUTTON_SUBMIT);

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

        buttons.add(Arrays.asList(submitButton));
        buttons.add(Arrays.asList(allSettingsButton, cancelButton));
        buttons.add(Arrays.asList(keywordsButton, defaultKeywordButton, compoundKeywordsButton));
        buttons.add(Arrays.asList(clearAllKeywordsButton));
        markup.setKeyboard(buttons);

        SendMessage message = new SendMessage(chatId, Messages.WHAT_SETTINGS);
        message.setReplyMarkup(markup);

        executeMessage(message);
    }

    /**
     * Sends a message to the user to choose from available calendars.
     * 
     * @param chatId the ID of the chat where the message is sent
     * @param userId the ID of the user requesting calendar selection
     */
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

    /**
     * Sends a request to the user to choose settings.
     * 
     * @param chatId the ID of the chat where the message is sent
     */
    private void sendSettingRequest(String chatId) {
        sendChoiceOfSettingsMessage(chatId);
    }

    /**
     * Processes the request to set a calendar based on the provided message text,
     * chat ID, and user ID.
     * 
     * @param messageText the text message containing the calendar ID to set
     * @param chatId      the ID of the chat where the message is sent
     * @param userId      the ID of the user setting the calendar
     */
    private void processSetCalendar(String messageText, String chatId, String userId) {
        sessionDataCache.invalidate(chatId + Constants.STATE);
        if (userAuthData.saveSelectedCalendar(userId, messageText)) {
            sendResponseMessage(chatId, Messages.CALENDAR_SUCCESS);
        } else {
            sendResponseMessage(chatId, Messages.ERROR_SAVING_CALENDAR);
        }
    }

    /**
     * Sets the user's calendar based on the provided callback data, chat ID, and
     * user ID.
     * 
     * @param chatId   the ID of the chat where the message is sent
     * @param userId   the ID of the user setting the calendar
     * @param callData the callback data containing the calendar selection
     */
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

    /**
     * Processes the authorization response based on the provided message text, chat
     * ID, and user ID.
     * 
     * @param messageText the text message containing the authorization response
     * @param chatId      the ID of the chat where the message is sent
     * @param userId      the ID of the user authorizing access to their calendar
     */
    private void processAuthorizationRresponse(String messageText, String chatId, String userId) {
        String choiceCalendar = googleCalendarService.getAccessToCalendar(messageText, userId);
        sessionDataCache.invalidate(chatId + Constants.STATE);
        if ("".equals(choiceCalendar) || choiceCalendar.startsWith(Messages.ERROR)) {
            sendResponseMessage(chatId, choiceCalendar);
        } else {
            sendChoiceOfCalendarsMessage(chatId, userId);
        }
    }

    /**
     * Processes the request to set compound keywords based on the provided message
     * text, chat ID, and user ID.
     * 
     * @param messageText the text message containing the compound keywords
     * @param chatId      the ID of the chat where the message is sent
     * @param userId      the ID of the user setting the compound keywords
     */
    private void processSetCompoundKeywordsRequest(String messageText, String chatId, String userId) {
        sessionDataCache.invalidate(chatId + Constants.STATE);
        if (userAuthData.saveCompoundKeywords(userId, messageText)) {
            sendResponseMessage(chatId, Messages.COMP_KEYWORDS_SUCCESS);
        } else {
            sendResponseMessage(chatId, Messages.ERROR_COMP_KEYWORDS);
        }
    }

    /**
     * Processes the request to set default keywords based on the provided message
     * text, chat ID, and user ID.
     * 
     * @param messageText the text message containing the default keywords
     * @param chatId      the ID of the chat where the message is sent
     * @param userId      the ID of the user setting the default keywords
     */
    private void processSetDefaultKeywordsRequest(String messageText, String chatId, String userId) {
        sessionDataCache.invalidate(chatId + Constants.STATE);
        if (userAuthData.saveDefaultKeywords(userId, messageText)) {
            sendResponseMessage(chatId, Messages.DEFAULT_KEYWORD_SUCCESS);
        } else {
            sendResponseMessage(chatId, Messages.ERROR_DEFAULT_KEYWORD);
        }
    }

    /**
     * Processes the request to set keywords based on the provided message text,
     * chat ID, and user ID.
     * 
     * @param messageText the text message containing the keywords
     * @param chatId      the ID of the chat where the message is sent
     * @param userId      the ID of the user setting the keywords
     */
    private void processSetKeywordsRequest(String messageText, String chatId, String userId) {
        sessionDataCache.invalidate(chatId + Constants.STATE);
        if (userAuthData.saveKeywords(userId, messageText)) {
            sendResponseMessage(chatId, Messages.KEYWORDS_SUCCESS);
        } else {
            sendResponseMessage(chatId, Messages.ERROR_KEYWORDS);
        }
    }

    /**
     * Processes the request to clear all keywords for the user based on the
     * provided chat ID and user ID.
     * 
     * @param chatId the ID of the chat where the message is sent
     * @param userId the ID of the user requesting to clear all keywords
     */
    private void clearAllKeywordsRequest(String chatId, String userId) {
        if (userAuthData.clearAllKeywords(userId)) {
            sendResponseMessage(chatId, Messages.KEYWORDS_CLEANED);
        } else {
            sendResponseMessage(chatId, Messages.ERROR_CLEANING_KEYWORDS);
        }
    }

    /**
     * Sends a request for authorization to the user by providing a URL for
     * authorization.
     * 
     * @param chatId the ID of the chat where the authorization request is sent
     */
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

    /**
     * Sends a help message to the user, providing information or instructions on
     * how to use the bot.
     * 
     * @param chatId the ID of the chat where the help message is sent
     */
    private void sendHelp(String chatId) {
        String str = TextHandler.getTextHepl();
        sendResponseMessage(chatId, str);
    }

    /**
     * Sends a request for analytics to the user, indicating that analytics
     * information is being requested.
     * 
     * @param chatId the ID of the chat where the analytics request is sent
     */
    private void sendRequestForAnalytics(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_ANALYTICS);
        sendResponseMessage(chatId, Messages.REQUEST_ANALYTICST);
    }

    /**
     * Sends a request for search to the user, indicating that a search operation is
     * being initiated.
     * 
     * @param chatId the ID of the chat where the search request is sent
     */
    private void sendRequestForSearch(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_SEARCH);
        sendResponseMessage(chatId, Messages.REQUEST_SEARCH);
    }

    /**
     * Sends a request to set compound keywords to the user.
     * 
     * @param chatId the ID of the chat where the request for compound keywords is
     *               sent
     */
    private void sendSetCompoundKeywordsRequest(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_COMPOUND_KEYWORDS);
        sendResponseMessage(chatId, Messages.REQUEST_COMP_KEYWORDS);
    }

    /**
     * Sends a request to set default keywords to the user.
     * 
     * @param chatId the ID of the chat where the request for default keywords is
     *               sent
     */
    private void sendSetDefaultKeywordsRequest(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_DEFAULT_KEYWORDS);
        sendResponseMessage(chatId, Messages.REQUEST_DEFAULT_KEYWORD);
    }

    /**
     * Sends a request to set keywords to the user.
     * 
     * @param chatId the ID of the chat where the request for keywords is sent
     */
    private void sendSetKeywordsRequest(String chatId) {
        sessionDataCache.put(chatId + Constants.STATE, Constants.REQUEST_KEYWORDS);
        sendResponseMessage(chatId, Messages.REQUEST_KEYWORDS);
    }

    /**
     * Sends a response message to the user in the specified chat.
     * 
     * @param chatId the ID of the chat where the message is sent
     * @param text   the text content of the message to be sent
     */
    public void sendResponseMessage(String chatId, String text) {
        SendMessage message = new SendMessage(chatId, text);
        executeMessage(message);
    }

    /**
     * Executes the provided SendMessage command, sending it to the Telegram API.
     * 
     * @param message the SendMessage object containing the message to be sent
     */
    public void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the file URL for a given file ID from the Telegram API.
     * 
     * @param fileId the ID of the file to retrieve
     * @return the URL of the file on the Telegram API
     * @throws TelegramApiException if an error occurs while accessing the Telegram
     *                              API
     */
    private String getFileUrl(String fileId) throws TelegramApiException {
        GetFile getFile = new GetFile();
        getFile.setFileId(fileId);
        File telegramFile = execute(getFile);
        return Messages.PATH_TG_API + telegramProperties.getBotToken() + "/" + telegramFile.getFilePath();
    }

}
