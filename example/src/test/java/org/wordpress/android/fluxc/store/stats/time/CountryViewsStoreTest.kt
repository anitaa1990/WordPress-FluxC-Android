package org.wordpress.android.fluxc.store.stats.time

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.Dispatchers.Unconfined
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.stats.time.CountryViewsModel
import org.wordpress.android.fluxc.model.stats.time.TimeStatsMapper
import org.wordpress.android.fluxc.network.rest.wpcom.stats.time.CountryViewsRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.stats.time.CountryViewsRestClient.CountryViewsResponse
import org.wordpress.android.fluxc.network.utils.StatsGranularity.DAYS
import org.wordpress.android.fluxc.persistence.TimeStatsSqlUtils
import org.wordpress.android.fluxc.store.StatsStore.FetchStatsPayload
import org.wordpress.android.fluxc.store.StatsStore.StatsError
import org.wordpress.android.fluxc.store.StatsStore.StatsErrorType.API_ERROR
import org.wordpress.android.fluxc.test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val PAGE_SIZE = 8
private val DATE = Date(0)

@RunWith(MockitoJUnitRunner::class)
class CountryViewsStoreTest {
    @Mock lateinit var site: SiteModel
    @Mock lateinit var restClient: CountryViewsRestClient
    @Mock lateinit var sqlUtils: TimeStatsSqlUtils
    @Mock lateinit var mapper: TimeStatsMapper
    private lateinit var store: CountryViewsStore
    @Before
    fun setUp() {
        store = CountryViewsStore(
                restClient,
                sqlUtils,
                mapper,
                Unconfined
        )
    }

    @Test
    fun `returns country views per site`() = test {
        val fetchInsightsPayload = FetchStatsPayload(
                COUNTRY_VIEWS_RESPONSE
        )
        val forced = true
        whenever(restClient.fetchCountryViews(site, DAYS, DATE, PAGE_SIZE + 1, forced)).thenReturn(
                fetchInsightsPayload
        )
        val model = mock<CountryViewsModel>()
        whenever(mapper.map(COUNTRY_VIEWS_RESPONSE, PAGE_SIZE)).thenReturn(model)

        val responseModel = store.fetchCountryViews(site, PAGE_SIZE, DAYS, DATE, forced)

        assertThat(responseModel.model).isEqualTo(model)
        verify(sqlUtils).insert(site, COUNTRY_VIEWS_RESPONSE, DAYS, DATE)
    }

    @Test
    fun `returns error when country views call fail`() = test {
        val type = API_ERROR
        val message = "message"
        val errorPayload = FetchStatsPayload<CountryViewsResponse>(StatsError(type, message))
        val forced = true
        whenever(restClient.fetchCountryViews(site, DAYS, DATE, PAGE_SIZE + 1, forced)).thenReturn(errorPayload)

        val responseModel = store.fetchCountryViews(site, PAGE_SIZE, DAYS, DATE, forced)

        assertNotNull(responseModel.error)
        val error = responseModel.error!!
        assertEquals(type, error.type)
        assertEquals(message, error.message)
    }

    @Test
    fun `returns country views from db`() {
        whenever(sqlUtils.selectCountryViews(site, DAYS, DATE)).thenReturn(COUNTRY_VIEWS_RESPONSE)
        val model = mock<CountryViewsModel>()
        whenever(mapper.map(COUNTRY_VIEWS_RESPONSE, PAGE_SIZE)).thenReturn(model)

        val result = store.getCountryViews(site, DAYS, PAGE_SIZE, DATE)

        assertThat(result).isEqualTo(model)
    }
}
