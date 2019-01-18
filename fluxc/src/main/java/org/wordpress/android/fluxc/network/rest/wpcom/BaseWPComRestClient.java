package org.wordpress.android.fluxc.network.rest.wpcom;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;

import org.wordpress.android.fluxc.Dispatcher;
import org.wordpress.android.fluxc.generated.AuthenticationActionBuilder;
import org.wordpress.android.fluxc.network.BaseRequest;
import org.wordpress.android.fluxc.network.BaseRequest.OnAuthFailedListener;
import org.wordpress.android.fluxc.network.BaseRequest.OnParseErrorListener;
import org.wordpress.android.fluxc.network.UserAgent;
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest.OnJetpackTimeoutError;
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest.OnJetpackTunnelTimeoutListener;
import org.wordpress.android.fluxc.network.rest.wpcom.account.AccountSocialRequest;
import org.wordpress.android.fluxc.network.rest.wpcom.auth.AccessToken;
import org.wordpress.android.fluxc.store.AccountStore.AuthenticateErrorPayload;
import org.wordpress.android.fluxc.utils.ErrorUtils.OnUnexpectedError;
import org.wordpress.android.util.LanguageUtils;

public abstract class BaseWPComRestClient {
    private AccessToken mAccessToken;
    private final RequestQueue mRequestQueue;

    protected final Context mAppContext;
    protected final Dispatcher mDispatcher;
    protected UserAgent mUserAgent;

    private OnAuthFailedListener mOnAuthFailedListener;
    private OnParseErrorListener mOnParseErrorListener;
    private OnJetpackTunnelTimeoutListener mOnJetpackTunnelTimeoutListener;

    public BaseWPComRestClient(Context appContext, Dispatcher dispatcher, RequestQueue requestQueue,
                               AccessToken accessToken, UserAgent userAgent) {
        mRequestQueue = requestQueue;
        mDispatcher = dispatcher;
        mAccessToken = accessToken;
        mUserAgent = userAgent;
        mAppContext = appContext;
        mOnAuthFailedListener = new OnAuthFailedListener() {
            @Override
            public void onAuthFailed(AuthenticateErrorPayload authError) {
                mDispatcher.dispatch(AuthenticationActionBuilder.newAuthenticateErrorAction(authError));
            }
        };
        mOnParseErrorListener = new OnParseErrorListener() {
            @Override
            public void onParseError(OnUnexpectedError event) {
                mDispatcher.emitChange(event);
            }
        };
        mOnJetpackTunnelTimeoutListener = new OnJetpackTunnelTimeoutListener() {
            @Override
            public void onJetpackTunnelTimeout(OnJetpackTimeoutError onTimeoutError) {
                mDispatcher.emitChange(onTimeoutError);
            }
        };
    }

    protected Request add(WPComGsonRequest request) {
        // Add "locale=xx_XX" query parameter to all request by default
        return add(request, true);
    }

    protected Request add(WPComGsonRequest request, boolean addLocaleParameter) {
        // Adds "locale=xx_XX" query parameter if the `addLocaleParameter` is true.
        return add(request, LocaleParamName.LOCALE_PARAM_NAME_FOR_V1_ENDPOINT, addLocaleParameter);
    }

    protected Request add(WPComGsonRequest request, LocaleParamName localeParamName, boolean addLocaleParameter) {
        if (addLocaleParameter) {
            // Add either `locale=xx_XX` or `_locale=xx_XX` parameter depending on the endpoint
            request.addQueryParameter(localeParamName.name, getLocale());
        }
        // TODO: If !mAccountToken.exists() then trigger the mOnAuthFailedListener
        return addRequest(setRequestAuthParams(request, true));
    }

    protected Request addUnauthedRequest(AccountSocialRequest request) {
        // Add "locale=xx_XX" query parameter to all request by default
        return addUnauthedRequest(request, true);
    }

    protected Request addUnauthedRequest(AccountSocialRequest request, boolean addLocaleParameter) {
        if (addLocaleParameter) {
            request.addQueryParameter("locale", getLocale());
            request.setOnParseErrorListener(mOnParseErrorListener);
            request.setUserAgent(mUserAgent.getUserAgent());
        }
        return addRequest(request);
    }

    protected Request addUnauthedRequest(WPComGsonRequest request) {
        // Add "locale=xx_XX" query parameter to all request by default
        return addUnauthedRequest(request, true);
    }

    protected Request addUnauthedRequest(WPComGsonRequest request, boolean addLocaleParameter) {
        if (addLocaleParameter) {
            request.addQueryParameter("locale", getLocale());
        }
        return addRequest(setRequestAuthParams(request, false));
    }

    protected AccessToken getAccessToken() {
        return mAccessToken;
    }

    private WPComGsonRequest setRequestAuthParams(WPComGsonRequest request, boolean shouldAuth) {
        request.setOnAuthFailedListener(mOnAuthFailedListener);
        request.setOnParseErrorListener(mOnParseErrorListener);
        request.setOnJetpackTunnelTimeoutListener(mOnJetpackTunnelTimeoutListener);
        request.setUserAgent(mUserAgent.getUserAgent());
        request.setAccessToken(shouldAuth ? mAccessToken.get() : null);
        return request;
    }

    private Request addRequest(BaseRequest request) {
        if (request.shouldCache() && request.shouldForceUpdate()) {
            mRequestQueue.getCache().invalidate(request.mUri.toString(), true);
        }
        return mRequestQueue.add(request);
    }

    private String getLocale() {
        return LanguageUtils.getPatchedCurrentDeviceLanguage(mAppContext);
    }

    /**
     * Adding a `_locale` parameter to all endpoints is a part of v2 infrastructure. However, `locale` parameter is
     * more commonly used for v1 endpoints and have been added to all the requests up until the time of this
     * documentation.
     *
     * This enum adds a more structured way to decide which locale parameter should be added. Since `_locale` is more
     * of a v2 thing, the naming of the enums reflects that. It's definitely something we could improve upon as we
     * start dealing with more endpoints that utilizes this enum.
     */
    public enum LocaleParamName {
        LOCALE_PARAM_NAME_FOR_V1_ENDPOINT("locale"),
        LOCALE_PARAM_FOR_V2_ENDPOINT("_locale");

        public String name;

        LocaleParamName(String name) {
            this.name = name;
        }
    }
}
