package com.admin.config;

import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(ClientHttpRequestFactory factory){
        return new RestTemplate(factory);
    }

    // 10秒超时配置
    private static final int TIMEOUT_SECONDS = 10;
    private static final int TIMEOUT_MILLISECONDS = TIMEOUT_SECONDS * 1000;


    @Bean
    public ClientHttpRequestFactory simpleClientHttpRequestFactory(){
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MILLISECONDS);
        factory.setReadTimeout(TIMEOUT_MILLISECONDS);
        return factory;
    }

    // 禁用ssl证书校验
    public static HttpComponentsClientHttpRequestFactory generateHttpRequestFactory() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException{
        TrustStrategy acceptingTrustStrategy = (x509Certificates, authType) -> true;
        SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
        SSLConnectionSocketFactory connectionSocketFactory = new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
        HttpClientBuilder httpClientBuilder = HttpClients.custom();

        httpClientBuilder.setSSLSocketFactory(connectionSocketFactory);
        CloseableHttpClient httpClient = httpClientBuilder.build();
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setHttpClient(httpClient);
        // 添加超时配置
        factory.setConnectTimeout(TIMEOUT_MILLISECONDS);
        factory.setReadTimeout(TIMEOUT_MILLISECONDS);
        return factory;
    }

    // 禁用ssl证书校验  添加代理ip  post请求
    public static HttpComponentsClientHttpRequestFactory post(String url, Integer port) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException{
        TrustStrategy acceptingTrustStrategy = (x509Certificates, authType) -> true;
        SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
        SSLConnectionSocketFactory connectionSocketFactory = new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        httpClientBuilder.setProxy(new HttpHost(url, port, "http"));

        httpClientBuilder.setSSLSocketFactory(connectionSocketFactory);
        CloseableHttpClient httpClient = httpClientBuilder.build();
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setHttpClient(httpClient);
        // 添加超时配置
        factory.setConnectTimeout(TIMEOUT_MILLISECONDS);
        factory.setReadTimeout(TIMEOUT_MILLISECONDS);
        return factory;
    }

}
