package org.wordpress.android.fluxc.store

import com.wellsql.generated.SiteModelTable
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.Payload
import org.wordpress.android.fluxc.action.WCCoreAction
import org.wordpress.android.fluxc.annotations.action.Action
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.network.rest.wpcom.wc.WooCommerceRestClient
import org.wordpress.android.fluxc.persistence.SiteSqlUtils
import org.wordpress.android.fluxc.store.WooCommerceStore.ApiVersionErrorType.GENERIC_ERROR
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WooCommerceStore @Inject constructor(dispatcher: Dispatcher, private val wcCoreRestClient: WooCommerceRestClient)
    : Store(dispatcher) {
    companion object {
        const val WOO_API_NAMESPACE_V1 = "wc/v1"
        const val WOO_API_NAMESPACE_V2 = "wc/v2"
        const val WOO_API_NAMESPACE_V3 = "wc/v3"
    }

    class FetchApiVersionResponsePayload(
        var site: SiteModel,
        var version: String
    ) : Payload<ApiVersionError>() {
        constructor(error: ApiVersionError, site: SiteModel) : this(site, "") { this.error = error }
    }

    class ApiVersionError(val type: ApiVersionErrorType = GENERIC_ERROR, val message: String = "") : OnChangedError

    enum class ApiVersionErrorType {
        GENERIC_ERROR,
        NO_WOO_API;

        companion object {
            private val reverseMap = ApiVersionErrorType.values().associateBy(ApiVersionErrorType::name)
            fun fromString(type: String) = reverseMap[type.toUpperCase(Locale.US)] ?: ApiVersionErrorType.GENERIC_ERROR
        }
    }

    // OnChanged events
    class OnApiVersionFetched(val site: SiteModel, val apiVersion: String) : OnChanged<ApiVersionError>()

    override fun onRegister() = AppLog.d(T.API, "WooCommerceStore onRegister")

    @Subscribe(threadMode = ThreadMode.ASYNC)
    override fun onAction(action: Action<*>) {
        val actionType = action.type as? WCCoreAction ?: return
        when (actionType) {
            // Remote actions
            WCCoreAction.FETCH_SITE_API_VERSION -> getApiVersion(action.payload as SiteModel)
            // Remote responses
            WCCoreAction.FETCHED_SITE_API_VERSION ->
                handleGetApiVersionCompleted(action.payload as FetchApiVersionResponsePayload)
        }
    }

    fun getWooCommerceSites(): MutableList<SiteModel> =
            SiteSqlUtils.getSitesWith(SiteModelTable.HAS_WOO_COMMERCE, true).asModel

    private fun getApiVersion(site: SiteModel) = wcCoreRestClient.getSupportedWooApiVersion(site)

    private fun handleGetApiVersionCompleted(payload: FetchApiVersionResponsePayload) {
        val onApiVersionFetched: OnApiVersionFetched

        if (payload.isError) {
            onApiVersionFetched = OnApiVersionFetched(payload.site, "").also { it.error = payload.error }
        } else {
            onApiVersionFetched = OnApiVersionFetched(payload.site, payload.version)
        }

        emitChange(onApiVersionFetched)
    }
}
