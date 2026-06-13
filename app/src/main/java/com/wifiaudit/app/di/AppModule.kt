package com.wifiaudit.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.wifiaudit.app.data.local.MIGRATION_8_9
import com.wifiaudit.app.data.local.WifiAuditDatabase
import com.wifiaudit.app.data.remote.NetworkConfig
import com.wifiaudit.app.data.remote.api.AuditApiService
import com.wifiaudit.app.data.repository.AuditRepositoryImpl
import com.wifiaudit.app.data.repository.SavedPlanRepositoryImpl
import com.wifiaudit.app.data.repository.WifiRepositoryImpl
import com.wifiaudit.app.domain.repository.AuditRepository
import com.wifiaudit.app.domain.repository.SavedPlanRepository
import com.wifiaudit.app.domain.repository.WifiRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("wifi_audit_prefs", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(NetworkConfig.CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(NetworkConfig.READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        prefs: SharedPreferences,
        gson: Gson,
        okHttpClient: OkHttpClient
    ): Retrofit {
        val baseUrl = prefs.getString(NetworkConfig.PREF_SERVER_URL, NetworkConfig.DEFAULT_BASE_URL)!!
        return Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuditApiService(retrofit: Retrofit): AuditApiService =
        retrofit.create(AuditApiService::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WifiAuditDatabase =
        Room.databaseBuilder(context, WifiAuditDatabase::class.java, "wifi_audits.db")
            .addMigrations(MIGRATION_8_9)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAuditDao(db: WifiAuditDatabase) = db.auditDao()

    @Provides
    fun provideMeasurementDao(db: WifiAuditDatabase) = db.measurementDao()

    @Provides
    fun provideSavedPlanDao(db: WifiAuditDatabase) = db.savedPlanDao()

    @Provides
    @Singleton
    fun provideAuditRepository(impl: AuditRepositoryImpl): AuditRepository = impl

    @Provides
    @Singleton
    fun provideSavedPlanRepository(impl: SavedPlanRepositoryImpl): SavedPlanRepository = impl

    @Provides
    @Singleton
    fun provideWifiRepository(impl: WifiRepositoryImpl): WifiRepository = impl
}
