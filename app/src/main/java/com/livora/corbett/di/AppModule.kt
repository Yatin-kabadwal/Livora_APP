package com.livora.corbett.di

import com.livora.corbett.BuildConfig
import com.livora.corbett.data.api.ApiService
import com.livora.corbett.data.api.AppJson
import com.livora.corbett.data.api.AuthApi
import com.livora.corbett.data.api.AuthInterceptor
import com.livora.corbett.data.api.TokenAuthenticator
import com.livora.corbett.data.local.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = AppJson

    private fun baseBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // Render free instances can take ~50s to wake up: be patient.
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)

    private fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    @Named("plain")
    fun providePlainClient(): OkHttpClient = baseBuilder().build()

    @Provides
    @Singleton
    fun provideAuthApi(@Named("plain") client: OkHttpClient, json: Json): AuthApi =
        retrofit(client, json).create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideOkHttp(session: SessionStore, authApi: AuthApi): OkHttpClient {
        val b = baseBuilder()
            .addInterceptor(AuthInterceptor(session))
            .authenticator(TokenAuthenticator(session, authApi))
        if (BuildConfig.DEBUG) {
            b.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        return b.build()
    }

    @Provides
    @Singleton
    fun provideApi(client: OkHttpClient, json: Json): ApiService =
        retrofit(client, json).create(ApiService::class.java)
}
