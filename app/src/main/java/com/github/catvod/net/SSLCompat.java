package com.github.catvod.net;

import android.annotation.SuppressLint;

import java.net.InetAddress;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * SSL兼容工具类，提供TLS协议升级和信任所有证书的SSL套接字工厂
 */
public class SSLCompat extends SSLSocketFactory {

    @SuppressLint({"TrustAllX509TrustManager", "CustomX509TrustManager"})
    public static final X509TrustManager TM = new TrustAllManager();

    private String[] cipherSuites;
    private SSLSocketFactory factory;
    private String[] protocols;

    public SSLCompat() {
        try {
            LinkedList<String> protocolList = new LinkedList<>();
            SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
            String[] supportedProtocols = socket.getSupportedProtocols();
            for (String protocol : supportedProtocols) {
                if (!protocol.toUpperCase().contains("SSL")) {
                    protocolList.add(protocol);
                }
            }
            this.protocols = protocolList.toArray(new String[0]);

            String[] preferredCipherSuites = {
                "TLS_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECHDE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_RSA_WITH_3DES_EDE_CBC_SHA",
                "TLS_RSA_WITH_AES_128_CBC_SHA",
                "TLS_RSA_WITH_AES_256_CBC_SHA",
                "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"
            };

            HashSet<String> cipherSet = new HashSet<>(Arrays.asList(preferredCipherSuites));
            cipherSet.retainAll(Arrays.asList(socket.getSupportedCipherSuites()));
            cipherSet.addAll(Arrays.asList(socket.getEnabledCipherSuites()));
            this.cipherSuites = cipherSet.toArray(new String[0]);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new X509TrustManager[]{TM}, null);
            this.factory = context.getSocketFactory();
            HttpsURLConnection.setDefaultSSLSocketFactory(this.factory);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void upgradeTLS(SSLSocket socket) {
        if (protocols != null) socket.setEnabledProtocols(protocols);
        if (cipherSuites != null) socket.setEnabledCipherSuites(cipherSuites);
    }

    @Override
    public Socket createSocket(String host, int port) {
        Socket socket = factory.createSocket(host, port);
        if (socket instanceof SSLSocket) upgradeTLS((SSLSocket) socket);
        return socket;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) {
        Socket socket = factory.createSocket(host, port, localAddress, localPort);
        if (socket instanceof SSLSocket) upgradeTLS((SSLSocket) socket);
        return socket;
    }

    @Override
    public Socket createSocket(InetAddress host, int port) {
        Socket socket = factory.createSocket(host, port);
        if (socket instanceof SSLSocket) upgradeTLS((SSLSocket) socket);
        return socket;
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) {
        Socket socket = factory.createSocket(address, port, localAddress, localPort);
        if (socket instanceof SSLSocket) upgradeTLS((SSLSocket) socket);
        return socket;
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) {
        Socket sslSocket = factory.createSocket(socket, host, port, autoClose);
        if (sslSocket instanceof SSLSocket) upgradeTLS((SSLSocket) sslSocket);
        return sslSocket;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return cipherSuites;
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return cipherSuites;
    }

    private static class TrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}