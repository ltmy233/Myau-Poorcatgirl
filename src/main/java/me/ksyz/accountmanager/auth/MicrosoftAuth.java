package me.ksyz.accountmanager.auth;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import me.ksyz.accountmanager.utils.SSLUtils;
import net.minecraft.util.Session;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
// Based on Auth Me (https://github.com/axieum/authme)
public final class MicrosoftAuth {
    private static CloseableHttpClient createTrustedHttpClient() {
        try {
            SSLConnectionSocketFactory sf = new SSLConnectionSocketFactory(
                    SSLUtils.getSSLContext().getSocketFactory(),
                    new String[]{"TLSv1.2"},
                    null,
                    new BrowserCompatHostnameVerifier()
            );
            return HttpClientBuilder.create()
                    .setSSLSocketFactory(sf)
                    .build();
        } catch (Exception ignored) {
            // 证书那套没弄成，下面用默认客户端顶上
        }

        return HttpClients.createDefault();
    }

    // 复用一份请求配置，省得每个请求都重设一遍超时
    private static final RequestConfig REQUEST_CONFIG = RequestConfig
            .custom()
            .setConnectionRequestTimeout(30_000)
            .setConnectTimeout(30_000)
            .setSocketTimeout(30_000)
            .build();

    // Account Manager 那边登记的应用
    public static String CLIENT_ID = "42a60a84-599d-44b2-a7c6-b00cdef1d6a2";
    public static String SCOPE = "XboxLive.signin XboxLive.offline_access";

    // 25565 默认端口 + 10
    private static final int PORT = 25575;

    public static URI getMSAuthLink(String state) {
        try {
            // 拼一个微软登录页的地址出来
            URIBuilder uriBuilder = new URIBuilder("https://login.live.com/oauth20_authorize.srf")
                    .addParameter("client_id", CLIENT_ID)
                    .addParameter("response_type", "code")
                    .addParameter("redirect_uri", String.format("http://localhost:%d/callback", PORT))
                    .addParameter("scope", SCOPE)
                    .addParameter("state", state)
                    .addParameter("prompt", "select_account");
            return uriBuilder.build();
        } catch (Exception e) {
            return null;
        }
    }

    public static CompletableFuture<String> acquireMSAuthCode(String state, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 起个临时 HTTP 服务，专门等 OAuth 那边回回调
                HttpServer server = HttpServer.create(
                        new InetSocketAddress(PORT), 0
                );

                // 数着请求处理完没有
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> authCode = new AtomicReference<>(null),
                        errorMsg = new AtomicReference<>(null);

                server.createContext("/callback", exchange -> {
                    // 把查询串拆成键值对
                    Map<String, String> query = URLEncodedUtils
                            .parse(
                                    exchange.getRequestURI().toString().replaceAll("/callback\\?", ""),
                                    StandardCharsets.UTF_8
                            )
                            .stream()
                            .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));

                    // 核对回来的参数
                    if (!state.equals(query.get("state"))) {
                        // state 对不上，说明不是我们发出去的那一次
                        errorMsg.set(
                                String.format("State mismatch! Expected '%s' but got '%s'.", state, query.get("state"))
                        );
                    } else if (query.containsKey("code")) {
                        // 验证码对上了
                        authCode.set(query.get("code"));
                    } else if (query.containsKey("error")) {
                        // 没给码，那就看看人家怎么说的
                        errorMsg.set(String.format("%s: %s", query.get("error"), query.get("error_description")));
                    }

                    // 回个页面，告诉用户浏览器可以关了
                    InputStream stream = MicrosoftAuth.class.getResourceAsStream("/callback.html");
                    byte[] response = stream != null ? IOUtils.toByteArray(stream) : new byte[0];
                    exchange.getResponseHeaders().add("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.getResponseBody().close();

                    // 通知外层：这事办完了
                    latch.countDown();
                });

                try {
                    // 起服务（http://localhost:25575/callback）
                    server.start();

                    // 等回调过来，然后拿码返回
                    latch.await();

                    // 有码就拿走
                    return Optional.ofNullable(authCode.get())
                            .filter(code -> !StringUtils.isBlank(code))
                            // 没码的话，把错误原样抛出去（有就带，没有就一句话）
                            .orElseThrow(() -> new Exception(
                                    Optional.ofNullable(errorMsg.get())
                                            .orElse("There was no auth code or error description present.")
                            ));
                } finally {
                    // 不管成没成，服务都得关
                    server.stop(2);
                }
            } catch (InterruptedException e) {
                throw new CancellationException("Microsoft auth code acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Microsoft auth code!", e);
            }
        }, executor);
    }

    public static CompletableFuture<Map<String, String>> acquireMSAccessTokens(String authCode, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = createTrustedHttpClient()) {
                // 发个新请求
                HttpPost request = new HttpPost(URI.create("https://login.live.com/oauth20_token.srf"));
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/x-www-form-urlencoded");
                request.setEntity(new UrlEncodedFormEntity(
                        Arrays.asList(
                                new BasicNameValuePair("client_id", CLIENT_ID),
                                new BasicNameValuePair("grant_type", "authorization_code"),
                                new BasicNameValuePair("code", authCode),
                                // redirect_uri 必须跟拿码时那个一模一样，差一个字符人家都不认
                                new BasicNameValuePair(
                                        "redirect_uri", String.format("http://localhost:%d/callback", PORT)
                                )
                        ),
                        "UTF-8"
                ));

                // 把请求发出去
                HttpResponse res = client.execute(request);

                // 拿回来的按 JSON 解，抽出 access 和 refresh 两个 token
                JsonObject json = new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject();
                String accessToken = Optional.ofNullable(json.get("access_token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        .orElseThrow(() -> new Exception(json.has("error") ?
                                String.format("%s: %s", json.get("error").getAsString(), json.get("error_description").getAsString()) :
                                "There was no Microsoft access token or error description present."
                        ));
                String refreshToken = Optional.ofNullable(json.get("refresh_token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        .orElseThrow(() -> new Exception(json.has("error") ?
                                String.format("%s: %s", json.get("error").getAsString(), json.get("error_description").getAsString()) :
                                "There was no Microsoft refresh token or error description present."
                        ));

                // 把两个 token 装起来返回，外面按不可变的方式用
                Map<String, String> result = new HashMap<>();
                result.put("access_token", accessToken);
                result.put("refresh_token", refreshToken);
                return result;
            } catch (InterruptedException e) {
                throw new CancellationException("Microsoft access tokens acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Microsoft access tokens!", e);
            }
        }, executor);
    }

    public static CompletableFuture<Map<String, String>> refreshMSAccessTokens(String msToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = createTrustedHttpClient()) {
                // 发个新请求
                HttpPost request = new HttpPost(URI.create("https://login.live.com/oauth20_token.srf"));
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/x-www-form-urlencoded");
                request.setEntity(new UrlEncodedFormEntity(
                        Arrays.asList(
                                new BasicNameValuePair("client_id", CLIENT_ID),
                                new BasicNameValuePair("grant_type", "refresh_token"),
                                new BasicNameValuePair("refresh_token", msToken),
                                // 拿码时用的 redirect_uri 必须原样带回，刷新这步也一样
                                CLIENT_ID.equals("00000000402b5328") ? new BasicNameValuePair(
                                        "scope", SCOPE
                                ) : new BasicNameValuePair(
                                        "redirect_uri", String.format("http://localhost:%d/callback", PORT)
                                )
                        ),
                        "UTF-8"
                ));

                // 发出去
                HttpResponse res = client.execute(request);

                // 回来按 JSON 解，抽出 access 和 refresh 两个 token
                JsonObject json = new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject();
                String accessToken = Optional.ofNullable(json.get("access_token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        .orElseThrow(() -> new Exception(json.has("error") ?
                                String.format("%s: %s", json.get("error").getAsString(), json.get("error_description").getAsString()) :
                                "There was no Microsoft access token or error description present."
                        ));
                String refreshToken = Optional.ofNullable(json.get("refresh_token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        .orElseThrow(() -> new Exception(json.has("error") ?
                                String.format("%s: %s", json.get("error").getAsString(), json.get("error_description").getAsString()) :
                                "There was no Microsoft refresh token or error description present."
                        ));

                // 把两个 token 装起来返回，外面按不可变的方式用
                Map<String, String> result = new HashMap<>();
                result.put("access_token", accessToken);
                result.put("refresh_token", refreshToken);
                return result;
            } catch (InterruptedException e) {
                throw new CancellationException("Microsoft access tokens acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Microsoft access tokens!", e);
            }
        }, executor);
    }

    public static CompletableFuture<String> acquireXboxAccessToken(String accessToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = createTrustedHttpClient()) {
                // 发个新请求
                HttpPost request = new HttpPost(URI.create("https://user.auth.xboxlive.com/user/authenticate"));
                JsonObject entity = new JsonObject();
                JsonObject properties = new JsonObject();
                properties.addProperty("AuthMethod", "RPS");
                properties.addProperty("SiteName", "user.auth.xboxlive.com");
                properties.addProperty("RpsTicket", CLIENT_ID.equals("00000000402b5328") ? accessToken : String.format("d=%s", accessToken));
                entity.add("Properties", properties);
                entity.addProperty("RelyingParty", "http://auth.xboxlive.com");
                entity.addProperty("TokenType", "JWT");
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/json");
                request.setEntity(new StringEntity(entity.toString()));

                // 发出去
                HttpResponse res = client.execute(request);

                // 回来按 JSON 解，抽出 access token
                JsonObject json = res.getStatusLine().getStatusCode() == 200
                        ? new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject()
                        : new JsonObject();
                // 有就直接返回
                return Optional.ofNullable(json.get("Token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        // 拿不到就带着错误描述抛出去（有就带）
                        .orElseThrow(() -> new Exception(json.has("XErr") ?
                                String.format("%s: %s", json.get("XErr").getAsString(), json.get("Message").getAsString()) :
                                "There was no access token or error description present."
                        ));
            } catch (InterruptedException e) {
                throw new CancellationException("Xbox Live access token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Xbox Live access token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<Map<String, String>> acquireXboxXstsToken(String accessToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = createTrustedHttpClient()) {
                // 发个新请求
                HttpPost request = new HttpPost("https://xsts.auth.xboxlive.com/xsts/authorize");
                JsonObject entity = new JsonObject();
                JsonObject properties = new JsonObject();
                JsonArray userTokens = new JsonArray();
                userTokens.add(new JsonPrimitive(accessToken));
                properties.addProperty("SandboxId", "RETAIL");
                properties.add("UserTokens", userTokens);
                entity.add("Properties", properties);
                entity.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
                entity.addProperty("TokenType", "JWT");
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/json");
                request.setEntity(new StringEntity(entity.toString()));

                // 发出去
                HttpResponse res = client.execute(request);

                // 回来按 JSON 解，拿到 token 和 user hash
                JsonObject json = res.getStatusLine().getStatusCode() == 200
                        ? new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject()
                        : new JsonObject();
                return Optional.ofNullable(json.get("Token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        // 拿到了就顺手把 user hash 抠出来一起返回
                        .map(token -> {
                            // user hash 在 DisplayClaims.xui[0].uhs 里
                            String uhs = json.get("DisplayClaims").getAsJsonObject()
                                    .get("xui").getAsJsonArray()
                                    .get(0).getAsJsonObject()
                                    .get("uhs").getAsString();

                            // token 和 user hash 一起打包返回
                            Map<String, String> result = new HashMap<>();
                            result.put("Token", token);
                            result.put("uhs", uhs);
                            return result;
                        })
                        // 拿不到就带着错误描述抛出去（有就带）
                        .orElseThrow(() -> new Exception(json.has("XErr") ?
                                String.format("%s: %s", json.get("XErr").getAsString(), json.get("Message").getAsString()) :
                                "There was no access token or error description present."
                        ));
            } catch (InterruptedException e) {
                throw new CancellationException("Xbox Live XSTS token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Xbox Live XSTS token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<String> acquireMCAccessToken(String xstsToken, String userHash, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = createTrustedHttpClient()) {
                // 发个新请求
                HttpPost request = new HttpPost(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"));
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/json");
                request.setEntity(new StringEntity(
                        String.format("{\"identityToken\": \"XBL3.0 x=%s;%s\"}", userHash, xstsToken)
                ));

                // 发出去
                HttpResponse res = client.execute(request);

                // 回来按 JSON 解，抽出 access token
                JsonObject json = new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject();

                // 有就直接返回
                return Optional.ofNullable(json.get("access_token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        // 拿不到就带着错误描述抛出去（有就带）
                        .orElseThrow(() -> new Exception(json.has("error") ?
                                String.format("%s: %s", json.get("error").getAsString(), json.get("errorMessage").getAsString()) :
                                "There was no access token or error description present."
                        ));
            } catch (InterruptedException e) {
                throw new CancellationException("Minecraft access token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Minecraft access token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<Session> login(String mcToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = createTrustedHttpClient()) {
                // 发个请求查 profile
                HttpGet request = new HttpGet(URI.create("https://api.minecraftservices.com/minecraft/profile"));
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Authorization", "Bearer " + mcToken);

                // 发出去
                HttpResponse res = client.execute(request);

                // 回来按 JSON 解，抽出 profile
                JsonObject json = new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject();
                return Optional.ofNullable(json.get("id"))
                        .map(JsonElement::getAsString)
                        .filter(uuid -> !StringUtils.isBlank(uuid))
                        // profile 拿到了就用它拼个会话
                        .map(uuid -> new Session(
                                json.get("name").getAsString(),
                                uuid,
                                mcToken,
                                Session.Type.MOJANG.toString()
                        ))
                        // 拿不到就带着错误描述抛出去（有就带）
                        .orElseThrow(() -> new Exception(json.has("error") ?
                                String.format("%s: %s", json.get("error").getAsString(), json.get("errorMessage").getAsString()) :
                                "There was no profile or error description present."
                        ));
            } catch (InterruptedException e) {
                throw new CancellationException("Minecraft profile fetching was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to fetch Minecraft profile!", e);
            }
        }, executor);
    }
}
