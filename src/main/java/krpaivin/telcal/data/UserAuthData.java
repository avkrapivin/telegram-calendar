package krpaivin.telcal.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

    public boolean saveTokens(String userId, Credential credential) {
        boolean res = true;
        String accessToken = credential.getAccessToken();
        String refreshToken = credential.getRefreshToken();
        long expirationTimeToken = credential.getExpirationTimeMilliseconds();

        try {
            UserData userData = userDataService.getUserDataById(userId).orElse(new UserData());
            userData.setUserId(userId);
            userData.setAccessToken(accessToken);
            userData.setRefreshToken(refreshToken);
            userData.setExpirationTimeToken(String.valueOf(expirationTimeToken));
            userDataService.saveUserData(userData);
            userCache.put(userId, userData);
        } catch (Exception e) {
            res = false;
        }

        return res;
    }

    public boolean saveSelectedCalendar(String userId, String messageText) {
        boolean res = true;

        try {
            UserData userData = userDataService.getUserDataById(userId).orElse(new UserData());
            userData.setUserId(userId);
            userData.setCalendar(messageText.strip());
            userDataService.saveUserData(userData);
            userCache.put(userId, userData);
        } catch (Exception e) {
            res = false;
        }

        return res;
    }

    public boolean saveKeywords(String userId, String messageText) {
        boolean res = true;

        try {
            UserData userData = userDataService.getUserDataById(userId).orElse(new UserData());
            userData.setUserId(userId);
            userData.setKeywords(messageText.strip());
            userDataService.saveUserData(userData);
            userCache.put(userId, userData);
        } catch (Exception e) {
            res = false;
        }

        return res;
    }

    public boolean saveDefaultKeywords(String userId, String messageText) {
        boolean res = true;

        try {
            UserData userData = userDataService.getUserDataById(userId).orElse(new UserData());
            userData.setUserId(userId);
            userData.setDefaultKeyword(messageText.strip());
            userDataService.saveUserData(userData);
            userCache.put(userId, userData);
        } catch (Exception e) {
            res = false;
        }

        return res;
    }

    public boolean saveCompoundKeywords(String userId, String messageText) {
        boolean res = true;

        try {
            UserData userData = userDataService.getUserDataById(userId).orElse(new UserData());
            userData.setUserId(userId);
            userData.setCompoundKeywords(messageText.strip());
            userDataService.saveUserData(userData);
            userCache.put(userId, userData);
        } catch (Exception e) {
            res = false;
        }

        return res;
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
        UserData userData = null;
        try {
            Optional<UserData> optionalUserData = userDataService.getUserDataById(userId);
            if (optionalUserData.isPresent()) {
                userData = optionalUserData.get();
            }
        } catch (Exception e) {
            //
        }
        return userData;
    }

    public String getAccessToken(String userId) {
        String accessToken = "";
        UserData userData = null;

        userData = getUserFromCache(userId);
        if (userData != null) {
            accessToken = userData.getAccessToken();
        }

        return accessToken;
    }

    public String getKeywords(String userId) {
        String keywords = "";
        UserData userData = null;

        userData = getUserFromCache(userId);
        if (userData != null) {
            keywords = userData.getKeywords();
        }

        return keywords;
    }

    public String getDefaultKeywords(String userId) {
        String keywords = "";
        UserData userData = null;

        userData = getUserFromCache(userId);
        if (userData != null) {
            keywords = userData.getDefaultKeyword();
        }

        return keywords;
    }

    public String getCompoundKeywords(String userId) {
        String keywords = "";
        UserData userData = null;

        userData = getUserFromCache(userId);
        if (userData != null) {
            keywords = userData.getCompoundKeywords();
        }

        return keywords;
    }

}
