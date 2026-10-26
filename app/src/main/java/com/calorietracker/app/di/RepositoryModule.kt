package com.calorietracker.app.di

import com.calorietracker.app.data.exporter.DataExporter
import com.calorietracker.app.data.repository.RoomDailyLogRepository
import com.calorietracker.app.data.repository.RoomMealRepositoryImpl
import com.calorietracker.app.data.repository.UserProfileRepository
import com.calorietracker.app.domain.repository.IDailyLogRepository
import com.calorietracker.app.domain.repository.IMealRepository
import com.calorietracker.app.domain.repository.IUserProfileRepository
import com.calorietracker.app.domain.repository.MealExporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the domain repository interfaces to their local implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
  @Binds abstract fun bindMealRepository(impl: RoomMealRepositoryImpl): IMealRepository

  @Binds abstract fun bindDailyLogRepository(impl: RoomDailyLogRepository): IDailyLogRepository

  @Binds abstract fun bindMealExporter(impl: DataExporter): MealExporter

  @Binds abstract fun bindUserProfileRepository(impl: UserProfileRepository): IUserProfileRepository
}
