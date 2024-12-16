package krpaivin.telcal.config;

import org.json.JSONObject;

import krpaivin.telcal.data.CredentialsLoader;

/**
 * Utility class for managing and providing access to application credentials.
 * 
 * This class loads the client ID and client secret from a credentials file during initialization
 * and provides static methods to retrieve them. The credentials are expected to be in a JSON format
 * with the "installed" object containing "client_id" and "client_secret" fields.
 */
public class CredentialsManager {
    private static String clientId;
    private static String clientSecret;

    // Static initializer block to load credentials at class loading time
    static {
        try {
            JSONObject jsonCredentials = CredentialsLoader.loadCredentials(Constants.CREDENTIALS_FILE_PATH);
            if (jsonCredentials != null) {
                JSONObject installed = jsonCredentials.getJSONObject("installed");
                clientId = installed.getString("client_id");
                clientSecret = installed.getString("client_secret");
            } else {
                throw new Exception(Messages.ERROR_ACCESS_CREDETIALS);
            }
        } catch (Exception e) {
            throw new RuntimeException(Messages.FAILD_LOAD_CREDENTIALS + Constants.CREDENTIALS_FILE_PATH, e);
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
