package me.ksyz.accountmanager.utils;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class SSLUtils {
    private static final SSLContext ctx;

    public static SSLContext getSSLContext() {
        return ctx;
    }

    static {
        try {
            // 密钥库是打包进 jar 的，从资源里读出来
            KeyStore jks = KeyStore.getInstance("JKS");
            InputStream stream = SSLUtils.class.getResourceAsStream("/ssl.jks");
            if (stream == null) {
                throw new RuntimeException("Couldn't find ssl.jks in resources");
            }
            jks.load(stream, "changeit".toCharArray());

            // 拿它初始化信任管理器
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(jks);

            // 用上面那套信任管理器建 TLS 上下文
            ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);

            // 设成全局默认，之后所有 https 连接都走它
            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize custom SSLContext", e);
        }
    }
}
