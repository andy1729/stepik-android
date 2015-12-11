package org.stepic.droid.configuration;

import android.content.Context;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yandex.metrica.YandexMetrica;

import org.stepic.droid.web.IApi;

import java.io.InputStream;
import java.io.InputStreamReader;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ConfigRelease implements IConfig {

    private JsonObject mProperties;

    private static final String API_HOST_URL = "API_HOST_URL";
    private static final String OAUTH_CLIENT_ID = "OAUTH_CLIENT_ID";
    private static final String OAUTH_CLIENT_SECRET = "OAUTH_CLIENT_SECRET";
    private static final String GRANT_TYPE = "GRANT_TYPE";
    private static final String OAUTH_CLIENT_ID_SOCIAL = "OAUTH_CLIENT_ID_SOCIAL";
    private static final String OAUTH_CLIENT_SECRET_SOCIAL = "OAUTH_CLIENT_SECRET_SOCIAL";
    private static final String GRANT_TYPE_SOCIAL = "GRANT_TYPE_SOCIAL";
    private static final String REFRESH_GRANT_TYPE = "REFRESH_GRANT_TYPE";
    private static final String DATE_PATTERN = "DATE_PATTERN";
    private static final String DATE_PATTERN_FOR_VIEW = "DATE_PATTERN_FOR_VIEW";
    private static final String ARRAY_IDS_PARAM = "ARRAY_IDS_PARAM";
    private static final String REDIRECT_URI = "REDIRECT_URI";


    @Inject
    public ConfigRelease(Context context) {
        try {
            InputStream in = context.getAssets().open("configs/config.json");
            JsonParser parser = new JsonParser();
            JsonElement config = parser.parse(new InputStreamReader(in));
            mProperties = config.getAsJsonObject();
        } catch (Exception e) {
            YandexMetrica.reportError("configRelease, config.json problem", e);
            mProperties = new JsonObject();
        }
    }

    @Override
    public String getOAuthClientId(IApi.TokenType type) {
        switch (type) {
            case social:
                return getString(OAUTH_CLIENT_ID_SOCIAL);
            case loginPassword:
                return getString(OAUTH_CLIENT_ID);
            default:
                return null;
        }
    }

    @Override
    public String getBaseUrl() {
        return getString(API_HOST_URL);
    }

    @Override
    public String getOAuthClientSecret(IApi.TokenType type) {
        switch (type) {
            case social:
                return getString(OAUTH_CLIENT_SECRET_SOCIAL);
            case loginPassword:
                return getString(OAUTH_CLIENT_SECRET);
            default:
                return null;
        }
    }

    @Override
    public String getGrantType(IApi.TokenType type) {
        switch (type) {
            case social:
                return getString(GRANT_TYPE_SOCIAL);
            case loginPassword:
                return getString(GRANT_TYPE);
            default:
                return null;
        }
    }

    @Override
    public String getRefreshGrantType() {
        return getString(REFRESH_GRANT_TYPE);
    }

    @Override
    public String getDatePattern() {
        return getString(DATE_PATTERN);
    }

    @Override
    public String getDatePatternForView() {
        return getString(DATE_PATTERN_FOR_VIEW);
    }

    @Override
    public String getIDSParam() {
        return getString(ARRAY_IDS_PARAM);
    }

    @Override
    public String getRedirectUri() {
        return getString(REDIRECT_URI);
    }


    private String getString(String key) {
        return getString(key, null);
    }

    private String getString(String key, String defaultValue) {
        JsonElement element = getObject(key);
        if (element != null) {
            return element.getAsString();
        } else {
            return defaultValue;
        }
    }

    private JsonElement getObject(String key) {
        return mProperties.get(key);
    }
}
