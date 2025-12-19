package com.launcher.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class MicrosoftAuthService {

    private static final String CLIENT_ID = "00000000402b5328-3049-477e-b468-6f7076080505";

    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    public static class DeviceCodeResponse {
        public String device_code;
        public String user_code;
        public String verification_uri;
        public int interval;
        public int expires_in;
        public String message;
    }

    public static class AuthResult {
        public String accessToken;
        public String username;
        public String uuid;
        public String error;
    }

    public CompletableFuture<DeviceCodeResponse> requestDeviceCode() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = "client_id=" + CLIENT_ID +
                        "&scope=XboxLive.signin offline_access";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(DEVICE_CODE_URL))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
                DeviceCodeResponse response = gson.fromJson(res.body(), DeviceCodeResponse.class);

                if (response == null || response.device_code == null) {
                    System.err.println("Device Code Error: " + res.body());
                    return null;
                }
                return response;

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    public CompletableFuture<AuthResult> pollForToken(DeviceCodeResponse dc) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long start = System.currentTimeMillis();
                long timeout = dc.expires_in * 1000L;

                while (System.currentTimeMillis() - start < timeout) {
                    Thread.sleep(dc.interval * 1000L);

                    String body = "grant_type=urn:ietf:params:oauth:grant-type:device_code" +
                            "&client_id=" + CLIENT_ID +
                            "&device_code=" + dc.device_code;

                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(TOKEN_URL))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();

                    HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                    JsonObject json = gson.fromJson(res.body(), JsonObject.class);

                    if (json.has("error")) {
                        if ("authorization_pending".equals(json.get("error").getAsString())) {
                            continue;
                        }
                        AuthResult r = new AuthResult();
                        r.error = json.get("error").getAsString();
                        return r;
                    }

                    return performMinecraftLogin(json.get("access_token").getAsString());
                }

                AuthResult r = new AuthResult();
                r.error = "Login timeout";
                return r;

            } catch (Exception e) {
                AuthResult r = new AuthResult();
                r.error = e.getMessage();
                return r;
            }
        });
    }

    private AuthResult performMinecraftLogin(String msToken) throws Exception {
        // XBL
        JsonObject xbl = new JsonObject();
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        props.addProperty("RpsTicket", "d=" + msToken);
        xbl.add("Properties", props);
        xbl.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xbl.addProperty("TokenType", "JWT");

        HttpResponse<String> xblRes = postJson(XBL_AUTH_URL, xbl);
        JsonObject xblJson = gson.fromJson(xblRes.body(), JsonObject.class);

        String xblToken = xblJson.get("Token").getAsString();
        String uhs = xblJson.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0)
                .getAsJsonObject().get("uhs").getAsString();

        // XSTS
        JsonObject xsts = new JsonObject();
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        JsonArray arr = new JsonArray();
        arr.add(xblToken);
        xstsProps.add("UserTokens", arr);
        xsts.add("Properties", xstsProps);
        xsts.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xsts.addProperty("TokenType", "JWT");

        HttpResponse<String> xstsRes = postJson(XSTS_AUTH_URL, xsts);
        JsonObject xstsJson = gson.fromJson(xstsRes.body(), JsonObject.class);

        String xstsToken = xstsJson.get("Token").getAsString();

        // Minecraft
        JsonObject mc = new JsonObject();
        mc.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);

        HttpResponse<String> mcRes = postJson(MC_LOGIN_URL, mc);
        String mcToken = gson.fromJson(mcRes.body(), JsonObject.class)
                .get("access_token").getAsString();

        // Profile
        HttpRequest profileReq = HttpRequest.newBuilder()
                .uri(URI.create(MC_PROFILE_URL))
                .header("Authorization", "Bearer " + mcToken)
                .GET().build();

        JsonObject profile = gson.fromJson(
                client.send(profileReq, HttpResponse.BodyHandlers.ofString()).body(),
                JsonObject.class);

        AuthResult r = new AuthResult();
        r.accessToken = mcToken;
        r.username = profile.get("name").getAsString();
        r.uuid = profile.get("id").getAsString();
        return r;
    }

    private HttpResponse<String> postJson(String url, JsonObject json) throws Exception {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(json)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
