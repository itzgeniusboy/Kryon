package com.kryon.filemanager.core

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// --- Entities ---

@Entity(tableName = "automation_rules")
data class AutomationRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val triggerType: String, // "new_file" or "schedule" or "app_installed"
    val triggerPath: String = "", // for new_file folder monitoring
    val conditionType: String = "", // "file_type", "file_size", "file_age"
    val conditionParam: String = "", // e.g. "log", "10485760" (10MB), "30" (days)
    val actionType: String, // "move", "delete", "compress", "notify"
    val actionParam: String = "", // destination directory or options
    val isActive: Boolean = true
)

@Entity(tableName = "automation_history")
data class AutomationHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleName: String,
    val status: String, // "SUCCESS", "FAILED"
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "command_macros")
data class CommandMacro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val command: String,
    val category: String, // "Root", "ADB", "Standard"
    val description: String = ""
)

// --- DAOs ---

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automation_rules ORDER BY id DESC")
    fun getAllRules(): Flow<List<AutomationRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AutomationRule): Long

    @Query("DELETE FROM automation_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("UPDATE automation_rules SET isActive = :active WHERE id = :id")
    suspend fun updateRuleStatus(id: Long, active: Boolean)

    // History Log operations
    @Query("SELECT * FROM automation_history ORDER BY timestamp DESC LIMIT 100")
    fun getHistoryFlow(): Flow<List<AutomationHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: AutomationHistory)

    @Query("DELETE FROM automation_history")
    suspend fun clearHistory()

    // Macros operations
    @Query("SELECT * FROM command_macros ORDER BY id DESC")
    fun getAllMacros(): Flow<List<CommandMacro>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMacro(macro: CommandMacro): Long

    @Query("DELETE FROM command_macros WHERE id = :id")
    suspend fun deleteMacroById(id: Long)
}
