package com.calorietracker.app.di

import android.content.Context
import android.content.SharedPreferences
import com.calorietracker.app.data.local.AppDatabase
import com.calorietracker.app.data.local.DailyLogDao
import com.calorietracker.app.data.local.MealDao
import com.calorietracker.app.data.local.MemoryDao
import com.calorietracker.app.data.local.WeightDao
import com.calorietracker.app.data.remote.adapter.ClaudeAdapter
import com.calorietracker.app.data.remote.adapter.DeepSeekAdapter
import com.calorietracker.app.data.remote.adapter.GeminiAdapter
import com.calorietracker.app.data.remote.adapter.OpenAiAdapter
import com.calorietracker.app.data.repository.AiNutritionRepositoryImpl
import com.calorietracker.app.data.repository.DietitianChatRepository
import com.calorietracker.app.data.repository.UserProfileRepository
import com.calorietracker.app.data.security.AesGcmCipher
import com.calorietracker.app.data.security.KeystoreKeyProvider
import com.calorietracker.app.data.security.SecretCipher
import com.calorietracker.app.domain.model.AiProviderType
import com.calorietracker.app.domain.repository.IAiNutritionRepository
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Process-wide singletons: database, DAOs, networking and the AI provider adapters. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  @Provides @Singleton fun provideGson(): Gson = Gson()

  @Provides
  @Singleton
  @Named(UserProfileRepository.PROFILE_PREFS)
  fun provideProfilePrefs(@ApplicationContext context: Context): SharedPreferences =
    context.getSharedPreferences(UserProfileRepository.PREFS_NAME, Context.MODE_PRIVATE)

  /** API keys are encrypted with an AES key that lives in the Android Keystore. */
  @Provides
  @Singleton
  fun provideSecretCipher(): SecretCipher = AesGcmCipher(KeystoreKeyProvider::secretKey)

  @Provides
  @Singleton
  fun provideHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .writeTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .build()

  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
    AppDatabase.getInstance(context)

  @Provides fun provideMealDao(db: AppDatabase): MealDao = db.mealDao()

  @Provides fun provideMemoryDao(db: AppDatabase): MemoryDao = db.memoryDao()

  @Provides fun provideWeightDao(db: AppDatabase): WeightDao = db.weightDao()

  @Provides fun provideDailyLogDao(db: AppDatabase): DailyLogDao = db.dailyLogDao()

  /** Injected wherever "today" matters so tests can pin the date. */
  @Provides @Singleton fun provideClock(): Clock = Clock.systemDefaultZone()

  @Provides
  @Singleton
  fun provideAdapters(client: OkHttpClient, gson: Gson): ProviderAdapters =
    ProviderAdapters(
      gemini = GeminiAdapter(client, gson),
      openAi = OpenAiAdapter(client, gson),
      claude = ClaudeAdapter(client, gson),
      deepSeek = DeepSeekAdapter(client, gson)
    )

  @Provides
  @Singleton
  fun provideAiNutritionRepository(adapters: ProviderAdapters, gson: Gson): IAiNutritionRepository =
    AiNutritionRepositoryImpl(
      geminiAdapter = adapters.gemini,
      openAiAdapter = adapters.openAi,
      claudeAdapter = adapters.claude,
      deepSeekAdapter = adapters.deepSeek,
      gson = gson
    )

  @Provides
  @Singleton
  fun provideDietitianChatRepository(adapters: ProviderAdapters): DietitianChatRepository =
    DietitianChatRepository(
      mapOf(
        AiProviderType.GEMINI to adapters.gemini,
        AiProviderType.OPENAI to adapters.openAi,
        AiProviderType.CLAUDE to adapters.claude,
        AiProviderType.DEEPSEEK to adapters.deepSeek
      )
    )

  private const val HTTP_TIMEOUT_SECONDS = 60L
}

/** The four provider adapters, grouped so they can be provided once and shared. */
data class ProviderAdapters(
  val gemini: GeminiAdapter,
  val openAi: OpenAiAdapter,
  val claude: ClaudeAdapter,
  val deepSeek: DeepSeekAdapter
)
