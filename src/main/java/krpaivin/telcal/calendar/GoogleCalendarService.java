package krpaivin.telcal.calendar;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.api.services.calendar.model.CalendarList;

import krpaivin.telcal.config.CalendarData;
import krpaivin.telcal.config.Constants;
import krpaivin.telcal.config.CredentialsManager;
import krpaivin.telcal.config.Messages;
import krpaivin.telcal.config.UserCalendar;
import krpaivin.telcal.data.UserAuthData;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * Provides services for interacting with Google Calendar, including creating
 * events,
 * retrieving events based on keywords or time ranges, and managing user
 * credentials.
 */
@RequiredArgsConstructor
@Service
public class GoogleCalendarService {

    private final UserAuthData userAuthData;
    private final Cache<String, UserCalendar> calendarSelectionCache;

    /**
     * Creates a new event in the user's Google Calendar.
     *
     * @param summary       the event summary (title).
     * @param description   the event description.
     * @param startDateTime the start date and time of the event.
     * @param endDateTime   the end date and time of the event.
     * @param userId        the ID of the user for whom the event is created.
     * @throws GeneralSecurityException if there is a security issue accessing
     *                                  Google APIs.
     * @throws IOException              if there is an issue communicating with
     *                                  Google APIs.
     * @throws IllegalStateException    if user credentials or calendar information
     *                                  are not available.
     */
    public void createGoogleCalendarEvent(String summary, String description,
            LocalDateTime startDateTime, LocalDateTime endDateTime, String userId)
            throws GeneralSecurityException, IOException {

        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Map<String, String> mapCredentials = userAuthData.getCredentialFromData(userId);

        if (mapCredentials == null) {
            throw new IllegalStateException(Messages.ERROR_ACCESSING_CALENDAR);
        }

        String calendarId = mapCredentials.get(userId + Constants.BD_FIELD_CALENDAR);
        Credential credential = getCredentialWithToken(userId, httpTransport, mapCredentials);

        Calendar service = new Calendar.Builder(httpTransport, Constants.JSON_FACTORY, credential)
                .setApplicationName(Constants.APPLICATION_NAME)
                .build();

        Event event = new Event().setSummary(summary).setDescription(description);

        com.google.api.services.calendar.model.Calendar calendar = service.calendars().get(calendarId).execute();
        String timeZone = calendar.getTimeZone();

        ZonedDateTime zonedStart = startDateTime.atZone(ZoneId.of(timeZone));
        ZonedDateTime zonedEnd = endDateTime.atZone(ZoneId.of(timeZone));

        EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(zonedStart.toInstant().toString()))
                .setTimeZone(timeZone);
        event.setStart(start);

        EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(zonedEnd.toInstant().toString()))
                .setTimeZone(timeZone);
        event.setEnd(end);

        service.events().insert(calendarId, event).execute();
    }

    /**
     * Retrieves a {@link Credential} object for a user, initializing it with an
     * access token, refresh token, and expiration time. If the access token is expired 
     * or unavailable, the method refreshes the token.
     *
     * @param userId         the unique identifier of the user.
     * @param httpTransport  the {@link NetHttpTransport} instance used for network
     *                       communication.
     * @param mapCredentials a map containing user credentials, including access
     *                       token, refresh token,
     *                       and token expiration time.
     * @return a {@link Credential} object initialized with the user's credentials.
     * @throws IOException if an error occurs while refreshing the token or
     *                     accessing credentials.
     */
    private Credential getCredentialWithToken(String userId, final NetHttpTransport httpTransport,
            Map<String, String> mapCredentials) throws IOException {

        String accessToken = mapCredentials.get(userId + Constants.BD_FIELD_ACCESS_TOKEN);
        String refreshToken = mapCredentials.get(userId + Constants.BD_FIELD_REFRESH_TOKEN);
        long expirationTime = Long.parseLong(mapCredentials.get(userId + Constants.BD_FIELD_EXP_TIME_TOKEN));

        String clientId = CredentialsManager.getClientId();
        String clientSecret = CredentialsManager.getClientSecret();

        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setJsonFactory(Constants.JSON_FACTORY)
                .setTransport(httpTransport)
                .setClientAuthentication(new ClientParametersAuthentication(clientId, clientSecret))
                .setTokenServerEncodedUrl(Constants.OAUTH_PATH_TOKEN)
                .build()
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .setExpirationTimeMilliseconds(expirationTime);

        if (credential.getAccessToken() == null || credential.getExpiresInSeconds() <= 0) {
            credential.refreshToken();
            userAuthData.saveTokens(userId, credential);
        }
        return credential;
    }

    /**
     * Retrieves analytics of events based on a keyword within a specified date-time
     * range.
     *
     * @param startDateTime the start of the time range.
     * @param endDateTime   the end of the time range.
     * @param keyword       the keyword to search for in events.
     * @param userId        the ID of the user whose calendar is analyzed.
     * @return a string containing the number of events and the total duration (in
     *         hours).
     * @throws GeneralSecurityException if there is a security issue accessing
     *                                  Google APIs.
     * @throws IOException              if there is an issue communicating with
     *                                  Google APIs.
     */
    public String analyticsEventsByKeyword(LocalDateTime startDateTime, LocalDateTime endDateTime,
            String keyword, String userId) throws GeneralSecurityException, IOException {

        DateTime start = new DateTime(startDateTime.toString() + ":00Z");
        DateTime end = new DateTime(endDateTime.toString() + ":59Z");

        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Map<String, String> mapCredentials = userAuthData.getCredentialFromData(userId);

        if (mapCredentials == null) {
            throw new IllegalStateException(Messages.ERROR_ACCESSING_CALENDAR);
        }

        String calendarId = mapCredentials.get(userId + Constants.BD_FIELD_CALENDAR);
        Credential credential = getCredentialWithToken(userId, httpTransport, mapCredentials);

        Calendar service = new Calendar.Builder(httpTransport, Constants.JSON_FACTORY, credential)
                .setApplicationName(Constants.APPLICATION_NAME)
                .build();

        // Get eventst list
        Calendar.Events.List request = service.events().list(calendarId)
                .setTimeMin(start)
                .setTimeMax(end)
                .setSingleEvents(true)
                .setOrderBy("startTime");

        // Add filtr keyword if it exist
        if (keyword != null && !keyword.trim().isEmpty()) {
            request.setQ(keyword);
        }

        Events events = request.execute();
        List<Event> items = events.getItems();
        int eventCount = items.size();
        long totalDuration = 0;

        // Calculate the duration of all events
        for (Event event : items) {
            DateTime startEvent = event.getStart().getDateTime();
            DateTime endEvent = event.getEnd().getDateTime();

            if (startEvent == null) {
                startEvent = event.getStart().getDate();
            }
            if (endEvent == null) {
                endEvent = event.getEnd().getDate();
            }

            if (startEvent != null && endEvent != null) {
                long eventDuration = (endEvent.getValue() - startEvent.getValue()) / (1000 * 60 * 60);
                totalDuration += eventDuration;
            }
        }

        return "Amount events: " + eventCount + ".\n" + "All time (hours): " + totalDuration;
    }

    /**
     * Searches for events in the user's Google Calendar based on a keyword and time
     * range.
     *
     * @param startDateTime the start of the time range.
     * @param endDateTime   the end of the time range.
     * @param keyword       the keyword to search for in events.
     * @param searchType    the type of search: first event, last event, or all
     *                      events.
     * @param userId        the ID of the user whose calendar is searched.
     * @return a formatted string containing details of the found events or a
     *         message if no events are found.
     * @throws GeneralSecurityException if there is a security issue accessing
     *                                  Google APIs.
     * @throws IOException              if there is an issue communicating with
     *                                  Google APIs.
     */
    public String searchEventInCalendar(LocalDateTime startDateTime, LocalDateTime endDateTime,
            String keyword, SearchType searchType, String userId) throws GeneralSecurityException, IOException {
        DateTime start = new DateTime(startDateTime.toString() + ":00Z");
        DateTime end = new DateTime(endDateTime.toString() + ":59Z");
        String result = "Events found: \n";

        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Map<String, String> mapCredentials = userAuthData.getCredentialFromData(userId);

        if (mapCredentials == null) {
            throw new IllegalStateException(Messages.ERROR_ACCESSING_CALENDAR);
        }

        String calendarId = mapCredentials.get(userId + Constants.BD_FIELD_CALENDAR);
        Credential credential = getCredentialWithToken(userId, httpTransport, mapCredentials);

        Calendar service = new Calendar.Builder(httpTransport, Constants.JSON_FACTORY, credential)
                .setApplicationName(Constants.APPLICATION_NAME)
                .build();

        // Get eventst list
        Calendar.Events.List request = service.events().list(calendarId)
                .setTimeMin(start)
                .setTimeMax(end)
                .setOrderBy("startTime")
                .setSingleEvents(true);

        // Add filtr keyword if it exist
        if (keyword != null && !keyword.trim().isEmpty()) {
            request.setQ(keyword);
        }

        Events events = request.execute();
        List<Event> items = events.getItems();

        if (items.isEmpty()) {
            result = "Events not found";
        } else {
            switch (searchType) {
                case FIRST:
                    Event firstEvent = items.get(0);
                    result = result + formatEvent(firstEvent);
                    break;
                case LAST:
                    Event lastEvent = items.get(items.size() - 1);
                    result = result + formatEvent(lastEvent);
                    break;
                case ALL:
                    String eventsString = items.stream()
                            .map(this::formatEvent)
                            .collect(Collectors.joining("\n"));
                    result = result + eventsString;
                    break;
                default:
                    result = "Invalid search type";
            }
        }

        return result;
    }

    /**
     * Formats an event object into a string containing its start date, duration,
     * and description.
     *
     * @param event the Google Calendar {@link Event} to format.
     * @return a formatted string with the event's date, duration in hours, and
     *         description.
     *         If the event is all-day, the date will reflect that. If no start date
     *         is available,
     *         a default date of "0001-01-01" is used.
     */
    private String formatEvent(Event event) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        DateTime startEvent = event.getStart().getDateTime();
        DateTime endEvent = event.getEnd().getDateTime();
        DateTime startEventAllDay = null;
        DateTime endEventAllDay = null;

        if (startEvent == null) {
            startEventAllDay = event.getStart().getDate();
        }

        if (endEvent == null) {
            endEventAllDay = event.getEnd().getDate();
        }

        String start = "0001.01.01";
        long eventDuration = 0L;

        if (startEvent != null && endEvent != null) {
            LocalDateTime localstartEvent = OffsetDateTime.parse(startEvent.toStringRfc3339()).toLocalDateTime();
            start = localstartEvent.format(formatter);

            if (startEvent != null && endEvent != null) {
                eventDuration = (endEvent.getValue() - startEvent.getValue()) / (1000 * 60 * 60);
            }
        } else if (startEventAllDay != null && endEventAllDay != null) {
            start = startEventAllDay.toString();
        }

        return String.format("Date = %s, Duration = %d, Description = %s", start, eventDuration,
                event.getSummary());
    }

    /**
     * Generates a URL for Google OAuth authorization.
     *
     * @return the authorization URL.
     */
    public String getUrlForAuthorization() {
        String url = "";
        GoogleAuthorizationCodeFlow flow = getGoogleFlow();
        if (flow != null) {
            url = flow.newAuthorizationUrl()
                    .setRedirectUri("urn:ietf:wg:oauth:2.0:oob")
                    .build();
        }
        return url;
    }

    /**
     * Retrieves a pre-configured GoogleAuthorizationCodeFlow instance for managing
     * user authorization.
     *
     * @return the GoogleAuthorizationCodeFlow instance.
     */
    public GoogleAuthorizationCodeFlow getGoogleFlow() {
        GoogleAuthorizationCodeFlow flow = null;

        String clientId = CredentialsManager.getClientId();
        String clientSecret = CredentialsManager.getClientSecret();

        flow = new GoogleAuthorizationCodeFlow.Builder(
                new NetHttpTransport(),
                JacksonFactory.getDefaultInstance(),
                clientId,
                clientSecret,
                Collections.singletonList(CalendarScopes.CALENDAR))
                .setAccessType("offline")
                .build();
        return flow;
    }

    /**
     * Retrieves all calendars associated with the user's Google account.
     *
     * @param credential the user's Google API credentials.
     * @return a map where keys are calendar IDs and values are calendar names.
     */
    public Map<String, String> getAllCalendar(Credential credential) {
        Map<String, String> calendars = new HashMap<>();
        NetHttpTransport httpTransport;
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            Calendar service = new Calendar.Builder(httpTransport, Constants.JSON_FACTORY, credential)
                    .setApplicationName(Constants.APPLICATION_NAME)
                    .build();

            CalendarList calendarList = service.calendarList().list().execute();
            for (CalendarListEntry entry : calendarList.getItems()) {
                calendars.put(entry.getId(), entry.getSummary());
            }
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
        return calendars;
    }

    /**
     * Exchanges an authorization code for access tokens and retrieves the user's
     * calendars.
     *
     * @param messageText the authorization code received from the user.
     * @param userId      the ID of the user for whom access is being granted.
     * @return a message prompting the user to select a calendar or an error
     *         message.
     */
    public String getAccessToCalendar(String messageText, String userId) {
        String res = "";

        try {
            GoogleAuthorizationCodeFlow flow = getGoogleFlow();
            TokenResponse tokenResponse = flow.newTokenRequest(messageText)
                    .setRedirectUri("urn:ietf:wg:oauth:2.0:oob")
                    .execute();
            Credential credential = flow.createAndStoreCredential(tokenResponse, userId);

            if (userAuthData.saveTokens(userId, credential)) {
                UserCalendar userCalendar = new UserCalendar();
                int count = 1;
                res = "Select a calendar:";
                Map<String, String> innerListOfCalendar = getAllCalendar(credential);
                for (Entry<String, String> entryHashMap : innerListOfCalendar.entrySet()) {
                    userCalendar.getObjects().put(count++,
                            new CalendarData(Map.of(entryHashMap.getKey(), entryHashMap.getValue())));
                }
                calendarSelectionCache.put(userId, userCalendar);
            } else {
                res = "Error saving authentication data.";
            }

        } catch (IOException e) {
            res = "Error processing authorization response.";
        }

        return res;
    }

}