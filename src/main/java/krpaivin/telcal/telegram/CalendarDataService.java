package krpaivin.telcal.telegram;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;

import krpaivin.telcal.calendar.GoogleCalendarService;
import krpaivin.telcal.calendar.SearchType;
import krpaivin.telcal.chatgpt.ChatGPTHadler;
import krpaivin.telcal.chatgpt.TypeGPTRequest;
import krpaivin.telcal.config.Constants;
import krpaivin.telcal.config.Messages;

@RequiredArgsConstructor
@Component
public class CalendarDataService {
    private final Cache<String, String> sessionDataCache;
    private final GoogleCalendarService googleCalendarService;
    private final ChatGPTHadler chatGPTHadler;

    protected String getAnalyticsFromCalendar(String startDate, String endDate, String keyword, String chatId,
            String userId) {
        String res = "";
        LocalDateTime startDateTime = LocalDateTime.parse(startDate, DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));
        LocalDateTime endDateTime = LocalDateTime.parse(endDate, DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));

        sessionDataCache.invalidate(chatId + Constants.STATE);
        try {
            res = googleCalendarService.analyticsEventsByKeyword(startDateTime, endDateTime, keyword, userId);
        } catch (Exception e) {
            res = Messages.ERROR_COLL_ANALYTICS;
        }
        return res;
    }

    protected String getFoundEventFromCalendar(String startDate, String endDate, String searchTypeString, String keyword,
            String chatId, String userId) throws GeneralSecurityException, IOException {
        LocalDateTime startDateTime = LocalDateTime.parse(startDate, DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));
        LocalDateTime endDateTime = LocalDateTime.parse(endDate, DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));
        SearchType searchType = getSearchTypeFromStr(searchTypeString);

        sessionDataCache.invalidate(chatId + Constants.STATE);
        return googleCalendarService.searchEventInCalendar(startDateTime, endDateTime, keyword, searchType, userId);
    }

    private SearchType getSearchTypeFromStr(String str) {
        SearchType searchType;
        switch (str) {
            case "first":
                searchType =  SearchType.FIRST;
                break;
            case "last":
                searchType = SearchType.LAST;
                break;
            case "all":
                searchType = SearchType.ALL;
                break;
            default:
                searchType = null;
        }
        return searchType;
    }

    protected String processSearchRequest(String messageText, String chatId, String userId) throws GeneralSecurityException, IllegalArgumentException, IOException {
        // Format of the message "yyyy-MM-dd / yyyy-MM-dd / TypeSearch / Keyword"
        String res = "";
        messageText = chatGPTHadler.publicGetResponseFromChatGPT(messageText, TypeGPTRequest.SEARCH_TEXT, userId);
        
        boolean isTrueFormat = TextHandler.checkFormatSearchRequest(messageText);

        if (isTrueFormat) {
            String[] parts = messageText.split(" / ", 4);
            if (parts.length >= 2) {
                String startDate = parts[0] + " 00:00";
                String endDate = parts[1] + " 23:59";
                String keyword = parts.length >= 3 ? parts[2].trim() : "";
                String searchType = parts.length == 4 ? parts[3].trim() : "all";
                searchType = searchType.isEmpty() ? "all" : searchType;

                res = getFoundEventFromCalendar(startDate, endDate, searchType, keyword, chatId, userId);
            } else {
                throw new IllegalArgumentException(Messages.INCORRECT_MESSAGE_FORMAT);
            }
        } else {
            throw new IllegalArgumentException(Messages.INCORRECT_MESSAGE_FORMAT);
        }
        return res;
    }

    protected void processEventCreation(String messageText, String userId) throws GeneralSecurityException, IllegalArgumentException, IOException {
        // Format of the message "Date Time Description"
        messageText = chatGPTHadler.publicGetResponseFromChatGPT(messageText, TypeGPTRequest.CREATING_EVENT_TEXT, userId);
        boolean isTrueFormat = TextHandler.checkFormatEventCreation(messageText);

        if (isTrueFormat) {
            String[] parts = messageText.split(" ", 3);
            if (parts.length == 3) {
                String dateStr = parts[0];
                String timeStr = parts[1];
                String description = parts[2];
                createCalendarEvent(dateStr, timeStr, "1", description, userId);
            } else {
                throw new IllegalArgumentException(Messages.INCORRECT_MESSAGE_FORMAT);
            }
        } else {
            throw new IllegalArgumentException(Messages.INCORRECT_MESSAGE_FORMAT);
        }
    }

    protected String processAnalyticsRequest(String messageText, String chatId, String userId) throws IllegalArgumentException{
        // Format of the message "yyyy-MM-dd yyyy-MM-dd Keyword"
        String res = "";
        messageText = chatGPTHadler.publicGetResponseFromChatGPT(messageText, TypeGPTRequest.ANALYTICS_TEXT, userId);
        boolean isTrueFormat = TextHandler.checkFormatAnalyticsRequest(messageText.trim());

        if (isTrueFormat) {
            String[] parts = messageText.split(" ", 3);
            if (parts.length >= 2) {
                String startDate = parts[0] + " 00:00";
                String endDate = parts[1] + " 23:59";
                String keyword = (parts.length == 3 && !parts[2].trim().equals("")) ? parts[2] : "";
                res = getAnalyticsFromCalendar(startDate, endDate, keyword, chatId, userId);
            } else {
                throw new IllegalArgumentException(Messages.INCORRECT_MESSAGE_FORMAT);
            }
        } else {
            throw new IllegalArgumentException(Messages.INCORRECT_MESSAGE_FORMAT);
        }
        return res;
    }

    public void createCalendarEvent(String dateStr, String timeStr, String duration, String description, String userId) throws GeneralSecurityException, IOException {
        LocalDateTime startDateTime = LocalDateTime.parse(dateStr + " " + timeStr,
                DateTimeFormatter.ofPattern(Constants.DATE_TIME_PATTERN));
        LocalDateTime endDateTime = startDateTime.plusMinutes(Long.parseLong(duration));

        googleCalendarService.createGoogleCalendarEvent(description, description, startDateTime, endDateTime, userId);
    }

}
