package krpaivin.telcal.data;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.api.client.auth.oauth2.Credential;

import krpaivin.telcal.config.Constants;
import krpaivin.telcal.entity.UserData;
import krpaivin.telcal.entity.UserDataService;
import lombok.RequiredArgsConstructor;

/**
 * Provides functionality for managing and persisting user authentication data, 
 * including tokens, selected calendars, and keywords.
 */
@Service
@RequiredArgsConstructor
public class UserAuthData {

    private final UserDataService userDataService;
    private final Cache<String, UserData> userCache;

    /**
     * Updates the user data for a specified user ID by applying a given update function.
     *
     * @param userId  the ID of the user whose data will be updated.
     * @param updater a {@link UnaryOperator} to modify the {@link UserData} object.
     * @return {@code true} if the update was successful, otherwise {@code false}.
     */
    private boolean updateUserData(String userId, UnaryOperator<UserData> updater) {
        boolean res = true;
        try {
            UserData userData = userDataService.getUserDataByUserId(userId).orElse(new UserData());
            userData.setUserId(userId);
            userData = updater.apply(userData);
            userDataService.saveUserData(userData);
            userCache.put(userId, userData);
        } catch (Exception e) {
            res = false;
        }
        return res;
    }

    /**
     * Saves the access and refresh tokens along with their expiration time for a specified user.
     *
     * @param userId     the ID of the user.
     * @param credential the {@link Credential} object containing token data.
     * @return {@code true} if the tokens were successfully saved, otherwise {@code false}.
     */
    public boolean saveTokens(String userId, Credential credential) {
        return updateUserData(userId, userData -> {
            userData.setAccessToken(credential.getAccessToken());
            userData.setRefreshToken(credential.getRefreshToken());
            userData.setExpirationTimeToken(String.valueOf(credential.getExpirationTimeMilliseconds()));
            return userData;
        });
    }

    /**
     * Saves the selected calendar for a specified user.
     *
     * @param userId      the ID of the user.
     * @param messageText the name or ID of the calendar.
     * @return {@code true} if the calendar was successfully saved, otherwise {@code false}.
     */
    public boolean saveSelectedCalendar(String userId, String messageText) {
        return updateUserData(userId, userData -> {
            userData.setCalendar(messageText.strip());
            return userData;
        });
    }

    /**
     * Saves the keywords associated with a specified user.
     *
     * @param userId      the ID of the user.
     * @param messageText the keywords to save.
     * @return {@code true} if the keywords were successfully saved, otherwise {@code false}.
     */
    public boolean saveKeywords(String userId, String messageText) {
        return updateUserData(userId, userData -> {
            userData.setKeywords(messageText.strip());
            return userData;
        });
    }

    /**
     * Saves the default keyword for a specified user.
     *
     * @param userId      the ID of the user.
     * @param messageText the default keyword to save.
     * @return {@code true} if the default keyword was successfully saved, otherwise {@code false}.
     */
    public boolean saveDefaultKeywords(String userId, String messageText) {
        return updateUserData(userId, userData -> {
            userData.setDefaultKeyword(messageText.strip());
            return userData;
        });
    }

    /**
     * Saves the compound keywords for a specified user.
     *
     * @param userId      the ID of the user.
     * @param messageText the compound keywords to save.
     * @return {@code true} if the compound keywords were successfully saved, otherwise {@code false}.
     */
    public boolean saveCompoundKeywords(String userId, String messageText) {
        return updateUserData(userId, userData -> {
            userData.setCompoundKeywords(messageText.strip());
            return userData;
        });
    }

    /**
     * Retrieves a map containing user credentials and related data from the database or cache.
     *
     * @param userId the ID of the user.
     * @return a {@link Map} containing the credentials and related data, or {@code null} if not found.
     */
    public Map<String, String> getCredentialFromData(String userId) {
        Map<String, String> hashMap = null;
        UserData userData;

        userData = getUserFromCache(userId);
        if (userData != null) {
            hashMap = putDataFromBdToMap(userId, userData);
        }

        return hashMap;
    }

    /**
     * Populates a {@link HashMap} with user credentials and related data.
     *
     * @param userId   the ID of the user.
     * @param userData the {@link UserData} object containing user data.
     * @return a {@link HashMap} with the user's credential data.
     */
    private HashMap<String, String> putDataFromBdToMap(String userId, UserData userData) {
        HashMap<String, String> hashMap = new HashMap<>();

        hashMap.put(userId + Constants.BD_FIELD_CALENDAR, userData.getCalendar());
        hashMap.put(userId + Constants.BD_FIELD_ACCESS_TOKEN, userData.getAccessToken());
        hashMap.put(userId + Constants.BD_FIELD_REFRESH_TOKEN, userData.getRefreshToken());
        hashMap.put(userId + Constants.BD_FIELD_EXP_TIME_TOKEN, userData.getExpirationTimeToken());

        return hashMap;
    }

    /**
     * Retrieves user data from the cache. If not found in the cache, retrieves it from the database.
     *
     * @param userId the ID of the user.
     * @return the {@link UserData} object for the user, or {@code null} if not found.
     */
    private UserData getUserFromCache(String userId) {
        return userCache.get(userId, this::getUserFromDataBase);
    }

    /**
     * Retrieves user data directly from the database.
     *
     * @param userId the ID of the user.
     * @return the {@link UserData} object for the user, or {@code null} if not found.
     */
    private UserData getUserFromDataBase(String userId) {
        return userDataService.getUserDataByUserId(userId).orElse(null);
    }

    /**
     * Retrieves a specific field from cached user data, or returns a default value if the data is not found.
     *
     * @param <T>          the type of the field.
     * @param userId       the ID of the user.
     * @param extractor    a function to extract the field from the {@link UserData}.
     * @param defaultValue the default value to return if the field is not found.
     * @return the extracted field value, or the default value if not found.
     */
    private <T> T getFieldFromCache(String userId, Function<UserData, T> extractor, T defaultValue) {
        UserData userData = getUserFromCache(userId);
        return userData != null ? extractor.apply(userData) : defaultValue;
    }

    /**
     * Retrieves the access token for a specified user.
     *
     * @param userId the ID of the user.
     * @return the access token, or an empty string if not found.
     */
    public String getAccessToken(String userId) {
        return getFieldFromCache(userId, UserData::getAccessToken, "");
    }

    /**
     * Retrieves the keywords associated with a specified user.
     *
     * @param userId the ID of the user.
     * @return the keywords, or an empty string if not found.
     */
    public String getKeywords(String userId) {
        return getFieldFromCache(userId, UserData::getKeywords, "");
    }

    /**
     * Retrieves the default keyword for a specified user.
     *
     * @param userId the ID of the user.
     * @return the default keyword, or an empty string if not found.
     */
    public String getDefaultKeywords(String userId) {
        return getFieldFromCache(userId, UserData::getDefaultKeyword, "");
    }

    /**
     * Retrieves the compound keywords for a specified user.
     *
     * @param userId the ID of the user.
     * @return the compound keywords, or an empty string if not found.
     */
    public String getCompoundKeywords(String userId) {
        return getFieldFromCache(userId, UserData::getCompoundKeywords, "");
    }

    /**
     * Clears all keywords (default, compound, and regular) for a specified user.
     *
     * @param userId the ID of the user.
     * @return {@code true} if the operation was successful, otherwise {@code false}.
     */
    public boolean clearAllKeywords(String userId) {
        return updateUserData(userId, userData -> {
            userData.setCompoundKeywords(null);
            userData.setDefaultKeyword(null);
            userData.setKeywords(null);
            return userData;
        });
    }

}
