package krpaivin.telcal.calendar;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.api.services.calendar.model.CalendarList;

import krpaivin.telcal.config.Constants;
import krpaivin.telcal.config.CredentialsManager;
import krpaivin.telcal.config.TelegramBotConfig;
import krpaivin.telcal.data.CredentialsLoader;
import krpaivin.telcal.data.UserAuthData;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONObject;

public class GoogleCalendarService {

        private GoogleCalendarService() {
                // Private constuctor to prevent instantiation
        }

        // public static Credential getCredentials(final NetHttpTransport httpTransport) throws Exception {
        //         GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(Constants.JSON_FACTORY,
        //                         new InputStreamReader(GoogleCalendarService.class
        //                                         .getResourceAsStream(Constants.CREDENTIALS_FILE_PATH)));
        //         GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        //                         httpTransport, Constants.JSON_FACTORY, clientSecrets, Constants.SCOPES)
        //                         .setDataStoreFactory(new FileDataStoreFactory(
        //                                         new java.io.File(Constants.TOKENS_DIRECTORY_PATH)))
        //                         .setAccessType("offline")
        //                         .build();
        //         LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        //         return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        // }

        public static void createGoogleCalendarEvent(String summary, String description,
                        LocalDateTime startDateTime, LocalDateTime endDateTime, String userId) throws Exception {

                final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                HashMap<String, String> mapCredentials = UserAuthData.getCredentialFromData(userId);

                if (mapCredentials == null) {
                        throw new Exception("Error accessing calendar");
                }

                String calendarId = mapCredentials.get(userId + "_calendar");
                Credential credential = getCredentialWithToken(userId, httpTransport, mapCredentials);

                Calendar service = new Calendar.Builder(httpTransport, Constants.JSON_FACTORY, credential)
                                .setApplicationName(Constants.APPLICATION_NAME)
                                .build();

                Event event = new Event().setSummary(summary).setDescription(description);

                com.google.api.services.calendar.model.Calendar calendar = service.calendars().get(calendarId)
                                .execute();
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

        private static Credential getCredentialWithToken(String userId, final NetHttpTransport httpTransport,
                        HashMap<String, String> mapCredentials)
                        throws Exception, IOException {

                String accessToken = mapCredentials.get(userId + "_accessToken");
                String refreshToken = mapCredentials.get(userId + "_refreshToken");
                long expirationTime = Long.parseLong(mapCredentials.get(userId + "_expirationTimeToken"));

                String clientId = CredentialsManager.getClientId();
                String clientSecret = CredentialsManager.getClientSecret();

                Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                                .setJsonFactory(Constants.JSON_FACTORY)
                                .setTransport(httpTransport)
                                .setClientAuthentication(new ClientParametersAuthentication(clientId, clientSecret))
                                .setTokenServerEncodedUrl("https://oauth2.googleapis.com/token")
                                .build()
                                .setAccessToken(accessToken)
                                .setRefreshToken(refreshToken)
                                .setExpirationTimeMilliseconds(expirationTime);

                if (credential.getAccessToken() == null || credential.getExpiresInSeconds() <= 0) {
                        credential.refreshToken();
                        UserAuthData.saveTokens(userId, credential);
                }
                return credential;
        }

        public static String analyticsEventsByKeyword(LocalDateTime startDateTime, LocalDateTime endDateTime,
                        String keyword, String userId) throws Exception {

                //String calendarId = TelegramBotConfig.getCalendarId();
                DateTime start = new DateTime(startDateTime.toString() + ":00Z");
                DateTime end = new DateTime(endDateTime.toString() + ":59Z");

                final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                HashMap<String, String> mapCredentials = UserAuthData.getCredentialFromData(userId);

                if (mapCredentials == null) {
                        throw new Exception("Error accessing calendar");
                }

                String calendarId = mapCredentials.get(userId + "_calendar");
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

                        if (startEvent != null && endEvent != null) {
                                long eventDuration = (endEvent.getValue() - startEvent.getValue()) / (1000 * 60 * 60);
                                totalDuration += eventDuration;
                        }
                }

                return "Amount events: " + eventCount + ".\n" + "All time (hours): " + totalDuration;
        }

        public static String searchEventInCalendar(LocalDateTime startDateTime, LocalDateTime endDateTime,
                        String keyword, SearchType searchType, String userId) throws Exception {
                //String calendarId = TelegramBotConfig.getCalendarId();
                DateTime start = new DateTime(startDateTime.toString() + ":00Z");
                DateTime end = new DateTime(endDateTime.toString() + ":59Z");
                String result = "Events found: \n";

                final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                HashMap<String, String> mapCredentials = UserAuthData.getCredentialFromData(userId);

                if (mapCredentials == null) {
                        throw new Exception("Error accessing calendar");
                }

                String calendarId = mapCredentials.get(userId + "_calendar");
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
                                                        .map(event -> formatEvent(event))
                                                        .collect(Collectors.joining("\n"));
                                        result = result + eventsString;
                                        break;
                                default:
                                        result = "Invalid search type";
                        }
                }

                return result;
        }

        private static String formatEvent(Event event) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

                DateTime startEvent = event.getStart().getDateTime();
                DateTime endEvent = event.getEnd().getDateTime();
                LocalDateTime localstartEvent = OffsetDateTime.parse(startEvent.toStringRfc3339()).toLocalDateTime();

                String start = localstartEvent.format(formatter);

                long eventDuration = 0L;

                if (startEvent != null && endEvent != null) {
                        eventDuration = (endEvent.getValue() - startEvent.getValue()) / (1000 * 60 * 60);
                }

                return String.format("Date = %s, Duration = %d, Description = %s", start, eventDuration,
                                event.getSummary());
        }

        public static String getUrlForAuthorization() {
                String url = "";
                GoogleAuthorizationCodeFlow flow = getGoogleFlow();
                if (flow != null) {
                        url = flow.newAuthorizationUrl()
                                        .setRedirectUri("urn:ietf:wg:oauth:2.0:oob") // Указываем oob-режим
                                        .build();
                }
                return url;
        }

        public static GoogleAuthorizationCodeFlow getGoogleFlow() {
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

        public static HashMap<String, String> getAllCalendar(Credential credential) {
                HashMap<String, String> calendars = new HashMap<>();
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
                } catch (GeneralSecurityException e) {
                        e.printStackTrace();
                } catch (IOException e) {
                        e.printStackTrace();
                }
                return calendars;
        }

}