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

@Service
@RequiredArgsConstructor
public class UserAuthData {

    private final UserDataService userDataService;
    private final Cache<String, UserData> userCache;

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

    public boolean saveTokens(String userId, Credential credential) {
        return updateUserData(userId, userData -> {
            userData.setAccessToken(credential.getAccessToken());
            userData.setRefreshToken(credential.getRefreshToken());
            userData.setExpirationTimeToken(String.valueOf(credential.getExpirationTimeMilliseconds()));
            return userData;
        });
    }

    public boolean saveSelectedCalendar(String userId, String messageText) {
        return updateUserData(userId, userData -> {
            userData.setCalendar(messageText.strip());
            return userData;
        });
    }

    public boolean saveKeywords(String userId, String messageText) {
        return updateUserData(userId, userData -> {
            userData.setKeywords(messageText.strip());
            return userData;
        });
    }

    public boolean saveDefaultKeywords(String userId, String messageText) {
        return updateUserData(userId, userData -> {
            userData.setDefaultKeyword(messageText.strip());
            return userData;
        });
    }

    public boolean saveCompoundKeywords(String userId, String messageText) {
        return updateUserData(userId, userData -> {
            userData.setCompoundKeywords(messageText.strip());
            return userData;
        });
    }

    public Map<String, String> getCredentialFromData(String userId) {
        Map<String, String> hashMap = null;
        UserData userData;

        userData = getUserFromCache(userId);
        if (userData != null) {
            hashMap = putDataFromBdToMap(userId, userData);
        }

        return hashMap;
    }

    private HashMap<String, String> putDataFromBdToMap(String userId, UserData userData) {
        HashMap<String, String> hashMap = new HashMap<>();

        hashMap.put(userId + Constants.BD_FIELD_CALENDAR, userData.getCalendar());
        hashMap.put(userId + Constants.BD_FIELD_ACCESS_TOKEN, userData.getAccessToken());
        hashMap.put(userId + Constants.BD_FIELD_REFRESH_TOKEN, userData.getRefreshToken());
        hashMap.put(userId + Constants.BD_FIELD_EXP_TIME_TOKEN, userData.getExpirationTimeToken());

        return hashMap;
    }

    private UserData getUserFromCache(String userId) {
        return userCache.get(userId, this::getUserFromDataBase);
    }

    private UserData getUserFromDataBase(String userId) {
        return userDataService.getUserDataByUserId(userId).orElse(null);
    }

    private <T> T getFieldFromCache(String userId, Function<UserData, T> extractor, T defaultValue) {
        UserData userData = getUserFromCache(userId);
        return userData != null ? extractor.apply(userData) : defaultValue;
    }

    public String getAccessToken(String userId) {
        return getFieldFromCache(userId, UserData::getAccessToken, "");
    }

    public String getKeywords(String userId) {
        return getFieldFromCache(userId, UserData::getKeywords, "");
    }

    public String getDefaultKeywords(String userId) {
        return getFieldFromCache(userId, UserData::getDefaultKeyword, "");
    }

    public String getCompoundKeywords(String userId) {
        return getFieldFromCache(userId, UserData::getCompoundKeywords, "");
    }

    public boolean clearAllKeywords(String userId) {
        return updateUserData(userId, userData -> {
            userData.setCompoundKeywords(null);
            userData.setDefaultKeyword(null);
            userData.setKeywords(null);
            return userData;
        });
    }

}
