package dev.dani.drivecast.di

import dev.dani.drivecast.data.remote.GeocodingApi
import dev.dani.drivecast.data.remote.USNationalWeatherServiceAlertsApi
import dev.dani.drivecast.data.remote.RouteApi
import dev.dani.drivecast.data.remote.ReverseGeocodingApi
import dev.dani.drivecast.data.remote.WeatherApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val OSRM_BASE_URL = "https://router.project-osrm.org/"
    private const val WEATHER_BASE_URL = "https://api.open-meteo.com/"
    private const val GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/"
    private const val REVERSE_GEOCODING_BASE_URL = "https://api-bdc.net/"
    private const val NWS_BASE_URL = "https://api.weather.gov/"
    private const val NWS_USER_AGENT = "DriveCast/1.0 (github.com/dani-kochi/drivecast; dan.i@gmx.com)"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val dispatcher = Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 8
        }
        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor { chain ->
                val request = chain.request()
                val updatedRequest = if (request.url.toString().startsWith(NWS_BASE_URL)) {
                    request.newBuilder()
                        .header("User-Agent", NWS_USER_AGENT)
                        .build()
                } else {
                    request
                }
                chain.proceed(updatedRequest)
            }
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideOsrmApi(client: OkHttpClient, json: Json): RouteApi =
        retrofit(OSRM_BASE_URL, client, json).create(RouteApi::class.java)

    @Provides
    @Singleton
    fun provideWeatherApi(client: OkHttpClient, json: Json): WeatherApi =
        retrofit(WEATHER_BASE_URL, client, json).create(WeatherApi::class.java)

    @Provides
    @Singleton
    fun provideGeocodingApi(client: OkHttpClient, json: Json): GeocodingApi =
        retrofit(GEOCODING_BASE_URL, client, json).create(GeocodingApi::class.java)

    @Provides
    @Singleton
    fun provideNwsAlertsApi(client: OkHttpClient, json: Json): USNationalWeatherServiceAlertsApi =
        retrofit(NWS_BASE_URL, client, json).create(USNationalWeatherServiceAlertsApi::class.java)

    @Provides
    @Singleton
    fun provideReverseGeocodingApi(client: OkHttpClient, json: Json): ReverseGeocodingApi =
        retrofit(REVERSE_GEOCODING_BASE_URL, client, json).create(ReverseGeocodingApi::class.java)
}
