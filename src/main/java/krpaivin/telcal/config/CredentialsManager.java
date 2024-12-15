package krpaivin.telcal.config;

import org.json.JSONObject;

import krpaivin.telcal.data.CredentialsLoader;

public class CredentialsManager {
    private static String clientId;
    private static String clientSecret;

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
