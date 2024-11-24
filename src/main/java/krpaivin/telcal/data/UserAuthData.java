package krpaivin.telcal.data;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Optional;
import java.util.Properties;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import com.google.api.client.auth.oauth2.Credential;

import krpaivin.telcal.config.TelegramBotConfig;
import krpaivin.telcal.entity.UserData;
import krpaivin.telcal.entity.UserDataService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAuthData {

    private final UserDataService userDataService;

    public boolean saveTokens(String userId, Credential credential) {
        boolean res = true;
        String accessToken = credential.getAccessToken();
        String refreshToken = credential.getRefreshToken();
        long expirationTimeToken = credential.getExpirationTimeMilliseconds();

        // Properties properties = new Properties();

        // try (InputStream in =
        // TelegramBotConfig.class.getClassLoader().getResourceAsStream("config.properties"))
        // {//new FileInputStream("config.properties")) {
        // properties.load(in);
        // } catch (IOException e) {
        // //
        // }

        // properties.setProperty("userId", userId);
        // properties.setProperty(userId + "_accessToken", accessToken);
        // properties.setProperty(userId + "_refreshToken", refreshToken);
        // properties.setProperty(userId + "_expirationTimeToken",
        // String.valueOf(expirationTimeToken));

        // try (FileOutputStream out = new FileOutputStream("config.properties")) {
        // properties.store(out, "Configuration Settings");
        // } catch (IOException e) {
        // res = false;
        // }

        try {
            UserData userData = userDataService.getUserDataById(userId).orElse(new UserData());
            userData.setUserId(userId);
            userData.setAccessToken(accessToken);
            userData.setRefreshToken(refreshToken);
            userData.setExpirationTimeToken(String.valueOf(expirationTimeToken));
            userDataService.saveUserData(userData);
        } catch (Exception e) {
            res = false;
        }

        return res;
    }

    public boolean saveSelectedCalendar(String userId, String messageText) {
        boolean res = true;
        // Properties properties = new Properties();

        // try (InputStream in =
        // TelegramBotConfig.class.getClassLoader().getResourceAsStream("config.properties"))
        // {
        // properties.load(in);
        // } catch (IOException e) {
        // //
        // }

        // properties.setProperty(userId + "_calendar", messageText.strip());

        // try (FileOutputStream out = new FileOutputStream("config.properties")) {
        // properties.store(out, "Configuration Settings");
        // } catch (IOException e) {
        // res = false;
        // }

        UserData userData = new UserData();
        userData.setUserId(userId);
        userData.setCalendar(messageText.strip());

        try {
            userDataService.saveUserData(userData);
        } catch (Exception e) {
            res = false;
        }

        return res;
    }

    public HashMap<String, String> getCredentialFromData(String userId) {
        HashMap<String, String> hashMap = new HashMap<>();

        // Properties properties = new Properties();

        // try (InputStream in =
        // TelegramBotConfig.class.getClassLoader().getResourceAsStream("config.properties"))
        // {
        // properties.load(in);
        // hashMap.put(userId + "_calendar", properties.getProperty(userId +
        // "_calendar"));
        // hashMap.put(userId + "_accessToken", properties.getProperty(userId +
        // "_accessToken"));
        // hashMap.put(userId + "_refreshToken", properties.getProperty(userId +
        // "_refreshToken"));
        // hashMap.put(userId + "_expirationTimeToken", properties.getProperty(userId +
        // "_expirationTimeToken"));
        // } catch (IOException e) {
        // hashMap = null;
        // }

        try {
            Optional<UserData> optionalUserData = userDataService.getUserDataById(userId);
            if (optionalUserData.isPresent()) {
                UserData userData = optionalUserData.get();
                hashMap.put(userId + "_calendar", userData.getCalendar());
                hashMap.put(userId + "_accessToken", userData.getAccessToken());
                hashMap.put(userId + "_refreshToken", userData.getRefreshToken());
                hashMap.put(userId + "_expirationTimeToken", userData.getExpirationTimeToken());
            } else {
                hashMap = null;
            }
        } catch (Exception e) {
            hashMap = null;
        }

        return hashMap;
    }

}
