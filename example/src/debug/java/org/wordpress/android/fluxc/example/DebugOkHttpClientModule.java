package org.wordpress.android.fluxc.example;

import com.facebook.stetho.okhttp3.StethoInterceptor;

import org.wordpress.android.fluxc.network.BaseRequest;
import org.wordpress.android.fluxc.network.MemorizingTrustManager;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import dagger.Module;
import dagger.Provides;
import okhttp3.OkHttpClient;

@Module
public class DebugOkHttpClientModule {
    @Provides
    @Named("regular")
    public OkHttpClient.Builder provideOkHttpClientBuilder() {
        return new OkHttpClient.Builder().addNetworkInterceptor(new StethoInterceptor());
    }

    @Provides
    @Named("custom-ssl")
    public OkHttpClient.Builder provideOkHttpClientBuilderCustomSSL(MemorizingTrustManager memorizingTrustManager) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        try {
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{memorizingTrustManager}, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            builder.sslSocketFactory(sslSocketFactory);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            AppLog.e(T.API, e);
        }
        builder.addNetworkInterceptor(new StethoInterceptor());
        return builder;
    }

    @Singleton
    @Provides
    @Named("custom-ssl")
    public OkHttpClient provideMediaOkHttpClientInstanceCustomSSL(@Named("custom-ssl")OkHttpClient.Builder builder) {
        return builder
                .connectTimeout(BaseRequest.DEFAULT_REQUEST_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(BaseRequest.UPLOAD_REQUEST_READ_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(BaseRequest.DEFAULT_REQUEST_TIMEOUT, TimeUnit.MILLISECONDS)
                .build();
    }

    @Singleton
    @Provides
    @Named("regular")
    public OkHttpClient provideMediaOkHttpClientInstance(@Named("regular")OkHttpClient.Builder builder) {
        return builder
                .connectTimeout(BaseRequest.DEFAULT_REQUEST_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(BaseRequest.UPLOAD_REQUEST_READ_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(BaseRequest.DEFAULT_REQUEST_TIMEOUT, TimeUnit.MILLISECONDS)
                .build();
    }
}
