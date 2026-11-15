package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CineRigDao {
    @Query("SELECT * FROM camera_presets ORDER BY createdAt DESC")
    fun observePresets(): Flow<List<CameraPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: CameraPresetEntity): Long

    @Query("DELETE FROM camera_presets WHERE id = :id")
    suspend fun deletePreset(id: Long)

    @Query("SELECT COUNT(*) FROM camera_presets")
    suspend fun countPresets(): Int

    @Query("SELECT * FROM ui_layouts ORDER BY updatedAt DESC")
    fun observeLayouts(): Flow<List<UiLayoutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayout(layout: UiLayoutEntity): Long

    @Update
    suspend fun updateLayout(layout: UiLayoutEntity)

    @Query("DELETE FROM ui_layouts WHERE id = :id")
    suspend fun deleteLayout(id: Long)

    @Query("SELECT COUNT(*) FROM ui_layouts")
    suspend fun countLayouts(): Int

    @Query("SELECT * FROM captured_media_logs ORDER BY timestamp DESC")
    fun observeMediaLogs(): Flow<List<CapturedMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaLog(media: CapturedMediaEntity): Long

    @Query("UPDATE captured_media_logs SET isFavorite = :favorite WHERE id = :id")
    suspend fun setMediaFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM captured_media_logs WHERE id = :id")
    suspend fun deleteMediaLog(id: Long)
}
