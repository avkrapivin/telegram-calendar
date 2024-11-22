package krpaivin.telcal.data;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

import com.google.api.client.auth.oauth2.Credential;

public class UserAuthData {

    public static boolean saveTokens(String userId, Credential credential) {
        boolean res = true;
        String accessToken = credential.getAccessToken();
        String refreshToken = credential.getRefreshToken();
        long expirationTimeToken = credential.getExpirationTimeMilliseconds();

        Properties properties = new Properties();

        try (FileInputStream in = new FileInputStream("config.properties")) {
            properties.load(in);
        } catch (IOException e) {
            //
        }

        properties.setProperty("userId", userId);
        properties.setProperty(userId + "_accessToken", accessToken);
        properties.setProperty(userId + "_refreshToken", refreshToken);
        properties.setProperty(userId + "_expirationTimeToken", String.valueOf(expirationTimeToken));

        try (FileOutputStream out = new FileOutputStream("config.properties")) {
            properties.store(out, "Configuration Settings");
        } catch (IOException e) {
            res = false;
        }
        return res;
    }

    public static boolean saveSelectedCalendar(String userId, String messageText) {
        boolean res = true;
        Properties properties = new Properties();

        try (FileInputStream in = new FileInputStream("config.properties")) {
            properties.load(in);
        } catch (IOException e) {
            //
        }

        properties.setProperty(userId + "_calendar", messageText.strip());

        try (FileOutputStream out = new FileOutputStream("config.properties")) {
            properties.store(out, "Configuration Settings");
        } catch (IOException e) {
            res = false;
        }
        return res;
    }

    public static HashMap<String, String> getCredentialFromData(String userId) {
        HashMap<String, String> hashMap = new HashMap<>();

        Properties properties = new Properties();

        try (FileInputStream in = new FileInputStream("config.properties")) {
            properties.load(in);
            hashMap.put(userId + "_calendar", properties.getProperty(userId + "_calendar"));
            hashMap.put(userId + "_accessToken", properties.getProperty(userId + "_accessToken"));
            hashMap.put(userId + "_refreshToken", properties.getProperty(userId + "_refreshToken"));
            hashMap.put(userId + "_expirationTimeToken", properties.getProperty(userId + "_expirationTimeToken"));
        } catch (IOException e) {
            hashMap = null;
        }

        return hashMap;
    }

}
