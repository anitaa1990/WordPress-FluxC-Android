package org.wordpress.android.fluxc.network.rest.wpcom.stats

import com.android.volley.RequestQueue
import com.android.volley.VolleyError
import com.nhaarman.mockito_kotlin.KArgumentCaptor
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.network.BaseRequest.BaseNetworkError
import org.wordpress.android.fluxc.network.BaseRequest.GenericErrorType.NETWORK_ERROR
import org.wordpress.android.fluxc.network.UserAgent
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest.WPComGsonNetworkError
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequestBuilder
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequestBuilder.Response
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequestBuilder.Response.Success
import org.wordpress.android.fluxc.network.rest.wpcom.auth.AccessToken
import org.wordpress.android.fluxc.network.rest.wpcom.stats.InsightsRestClient.AllTimeResponse
import org.wordpress.android.fluxc.network.rest.wpcom.stats.InsightsRestClient.MostPopularResponse
import org.wordpress.android.fluxc.network.rest.wpcom.stats.InsightsRestClient.PostStatsResponse
import org.wordpress.android.fluxc.network.rest.wpcom.stats.InsightsRestClient.PostsResponse
import org.wordpress.android.fluxc.network.rest.wpcom.stats.InsightsRestClient.VisitResponse
import org.wordpress.android.fluxc.network.utils.StatsGranularity.DAYS
import org.wordpress.android.fluxc.store.InsightsStore.StatsErrorType.API_ERROR
import org.wordpress.android.fluxc.store.POST_STATS_RESPONSE
import org.wordpress.android.fluxc.store.VISITS_RESPONSE
import org.wordpress.android.fluxc.test
import java.text.SimpleDateFormat
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class InsightsRestClientTest {
    @Mock private lateinit var dispatcher: Dispatcher
    @Mock private lateinit var wpComGsonRequestBuilder: WPComGsonRequestBuilder
    @Mock private lateinit var site: SiteModel
    @Mock private lateinit var requestQueue: RequestQueue
    @Mock private lateinit var accessToken: AccessToken
    @Mock private lateinit var userAgent: UserAgent
    private lateinit var urlCaptor: KArgumentCaptor<String>
    private lateinit var paramsCaptor: KArgumentCaptor<Map<String, String>>
    private lateinit var insightsRestClient: InsightsRestClient
    private val siteId: Long = 12
    private val postId: Long = 1

    @Before
    fun setUp() {
        urlCaptor = argumentCaptor()
        paramsCaptor = argumentCaptor()
        insightsRestClient = InsightsRestClient(
                dispatcher,
                wpComGsonRequestBuilder,
                null,
                requestQueue,
                accessToken,
                userAgent
        )
    }

    @Test
    fun `returns all time success response`() = test {
        val response = mock<AllTimeResponse>()
        initAllTimeResponse(response)

        val responseModel = insightsRestClient.fetchAllTimeInsights(site, false)

        assertThat(responseModel.response).isNotNull()
        assertThat(responseModel.response).isEqualTo(response)
        assertThat(urlCaptor.lastValue).isEqualTo("https://public-api.wordpress.com/rest/v1.1/sites/12/stats/")
        assertThat(paramsCaptor.lastValue).isEmpty()
    }

    @Test
    fun `returns all time error response`() = test {
        val errorMessage = "message"
        initAllTimeResponse(
                error = WPComGsonNetworkError(
                        BaseNetworkError(
                                NETWORK_ERROR,
                                errorMessage,
                                VolleyError(errorMessage)
                        )
                )
        )

        val responseModel = insightsRestClient.fetchAllTimeInsights(site, false)

        assertThat(responseModel.error).isNotNull()
        assertThat(responseModel.error.type).isEqualTo(API_ERROR)
        assertThat(responseModel.error.message).isEqualTo(errorMessage)
    }

    @Test
    fun `returns most popular success response`() = test {
        val response = mock<MostPopularResponse>()
        initMostPopularResponse(response)

        val responseModel = insightsRestClient.fetchMostPopularInsights(site, false)

        assertThat(responseModel.response).isNotNull()
        assertThat(responseModel.response).isEqualTo(response)
        assertThat(urlCaptor.lastValue).isEqualTo("https://public-api.wordpress.com/rest/v1.1/sites/12/stats/insights/")
        assertThat(paramsCaptor.lastValue).isEmpty()
    }

    @Test
    fun `returns most popular error response`() = test {
        val errorMessage = "message"
        initMostPopularResponse(
                error = WPComGsonNetworkError(
                        BaseNetworkError(
                                NETWORK_ERROR,
                                errorMessage,
                                VolleyError(errorMessage)
                        )
                )
        )

        val responseModel = insightsRestClient.fetchMostPopularInsights(site, false)

        assertThat(responseModel.error).isNotNull()
        assertThat(responseModel.error.type).isEqualTo(API_ERROR)
        assertThat(responseModel.error.message).isEqualTo(errorMessage)
    }

    @Test
    fun `returns latest post success response`() = test {
        val response = mock<PostsResponse>()
        initLatestPostResponse(response)

        val responseModel = insightsRestClient.fetchLatestPostForInsights(site, false)

        assertThat(responseModel.response).isNotNull()
        assertThat(responseModel.response).isEqualTo(response)
        assertThat(urlCaptor.lastValue).isEqualTo("https://public-api.wordpress.com/rest/v1.1/sites/12/posts/")
        assertThat(paramsCaptor.lastValue).isEqualTo(
                mapOf(
                        "fields" to "ID,title,URL,discussion,like_count,date",
                        "number" to "1",
                        "order_by" to "date",
                        "type" to "post"
                )
        )
    }

    @Test
    fun `returns latest post error response`() = test {
        val errorMessage = "message"
        initLatestPostResponse(
                error = WPComGsonNetworkError(
                        BaseNetworkError(
                                NETWORK_ERROR,
                                errorMessage,
                                VolleyError(errorMessage)
                        )
                )
        )

        val responseModel = insightsRestClient.fetchLatestPostForInsights(site, false)

        assertThat(responseModel.error).isNotNull()
        assertThat(responseModel.error.type).isEqualTo(API_ERROR)
        assertThat(responseModel.error.message).isEqualTo(errorMessage)
    }

    @Test
    fun `returns posts view success response`() = test {
        initPostsViewResponse(POST_STATS_RESPONSE)

        val responseModel = insightsRestClient.fetchPostStats(site, postId, false)

        assertThat(responseModel.response).isNotNull()
        assertThat(responseModel.response).isEqualTo(POST_STATS_RESPONSE)
        assertThat(urlCaptor.lastValue).isEqualTo("https://public-api.wordpress.com/rest/v1.1/sites/12/stats/post/1/")
        assertThat(paramsCaptor.lastValue).isEmpty()
    }

    @Test
    fun `returns posts view error response`() = test {
        val errorMessage = "message"
        initPostsViewResponse(
                error = WPComGsonNetworkError(
                        BaseNetworkError(
                                NETWORK_ERROR,
                                errorMessage,
                                VolleyError(errorMessage)
                        )
                )
        )

        val responseModel = insightsRestClient.fetchPostStats(site, postId, false)

        assertThat(responseModel.error).isNotNull()
        assertThat(responseModel.error.type).isEqualTo(API_ERROR)
        assertThat(responseModel.error.message).isEqualTo(errorMessage)
    }

    @Test
    fun `returns visits per time period`() = test {
        initVisitResponse(VISITS_RESPONSE)

        val date = Date()
        val format = SimpleDateFormat("yyyy-MM-dd")
        val responseModel = insightsRestClient.fetchTimePeriodStats(site, DAYS, date, false)

        assertThat(responseModel.response).isNotNull()
        assertThat(responseModel.response).isEqualTo(VISITS_RESPONSE)
        assertThat(urlCaptor.lastValue).isEqualTo("https://public-api.wordpress.com/rest/v1.1/sites/12/stats/visits/")
        assertThat(paramsCaptor.lastValue).isEqualTo(
                mapOf(
                        "date" to format.format(date),
                        "quantity" to "1",
                        "unit" to "day"
                )
        )
    }

    @Test
    fun `returns visits per time period error response`() = test {
        val errorMessage = "message"
        initVisitResponse(
                error = WPComGsonNetworkError(
                        BaseNetworkError(
                                NETWORK_ERROR,
                                errorMessage,
                                VolleyError(errorMessage)
                        )
                )
        )

        val date = Date()
        val responseModel = insightsRestClient.fetchTimePeriodStats(site, DAYS, date, false)

        assertThat(responseModel.error).isNotNull()
        assertThat(responseModel.error.type).isEqualTo(API_ERROR)
        assertThat(responseModel.error.message).isEqualTo(errorMessage)
    }

    private suspend fun initAllTimeResponse(
        data: AllTimeResponse? = null,
        error: WPComGsonNetworkError? = null
    ): Response<AllTimeResponse> {
        return initResponse(AllTimeResponse::class.java, data ?: mock(), error)
    }

    private suspend fun initMostPopularResponse(
        data: MostPopularResponse? = null,
        error: WPComGsonNetworkError? = null
    ): Response<MostPopularResponse> {
        return initResponse(MostPopularResponse::class.java, data ?: mock(), error)
    }

    private suspend fun initLatestPostResponse(
        data: PostsResponse? = null,
        error: WPComGsonNetworkError? = null
    ): Response<PostsResponse> {
        return initResponse(PostsResponse::class.java, data ?: mock(), error)
    }

    private suspend fun initPostsViewResponse(
        data: PostStatsResponse? = null,
        error: WPComGsonNetworkError? = null
    ): Response<PostStatsResponse> {
        return initResponse(PostStatsResponse::class.java, data ?: mock(), error)
    }

    private suspend fun initVisitResponse(
        data: VisitResponse? = null,
        error: WPComGsonNetworkError? = null
    ): Response<VisitResponse> {
        return initResponse(VisitResponse::class.java, data ?: mock(), error)
    }

    private suspend fun <T> initResponse(
        kclass: Class<T>,
        data: T,
        error: WPComGsonNetworkError? = null
    ): Response<T> {
        val response = if (error != null) Response.Error<T>(error) else Success(data)
        whenever(
                wpComGsonRequestBuilder.syncGetRequest(
                        eq(insightsRestClient),
                        urlCaptor.capture(),
                        paramsCaptor.capture(),
                        eq(kclass),
                        eq(true),
                        any(),
                        eq(false)
                )
        ).thenReturn(response)
        whenever(site.siteId).thenReturn(siteId)
        return response
    }
}