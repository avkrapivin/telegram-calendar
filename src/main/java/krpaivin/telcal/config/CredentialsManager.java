package krpaivin.telcal.config;

import org.json.JSONObject;

import krpaivin.telcal.data.CredentialsLoader;

public class CredentialsManager {
    private static final String CREDENTIALS_PATH = "config/credentials.json";
    private static String clientId;
    private static String clientSecret;

    static {
        try {
            JSONObject jsonCredentials = CredentialsLoader.loadCredentials(CREDENTIALS_PATH);
            if (jsonCredentials != null) {
                JSONObject installed = jsonCredentials.getJSONObject("installed");
                clientId = installed.getString("client_id");
                clientSecret = installed.getString("client_secret");
            } else {
                throw new Exception("Error accessing calendar credentials");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load credentials from " + CREDENTIALS_PATH, e);
        }
    }

    private CredentialsManager() {}

    public static String getClientId() {
        return clientId;
    }

    public static String getClientSecret() {
        return clientSecret;
    }
}