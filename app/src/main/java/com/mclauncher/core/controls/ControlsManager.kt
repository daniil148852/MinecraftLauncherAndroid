package com.mclauncher.core.controls

import android.content.Context
import com.mclauncher.data.local.preferences.PreferencesManager
import com.mclauncher.domain.models.ControlButton
import com.mclauncher.domain.models.ControlLayout
import com.mclauncher.utils.Constants
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ControlsManager @Inject constructor(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    private val moshi = Moshi.Builder().build()
    private val controlsDir = File(context.filesDir, "controls")
    
    private var currentLayout: ControlLayout? = null

    init {
        controlsDir.mkdirs()
    }

    suspend fun getDefaultLayout(): ControlLayout {
        return ControlLayout(
            id = "default",
            name = "Default Layout",
            isDefault = true,
            buttons = createDefaultButtons()
        )
    }

    private fun createDefaultButtons(): List<ControlButton> {
        val prefs = preferencesManager.preferences
        
        return listOf(
            // Movement controls (left side)
            ControlButton(
                id = "forward",
                keyCode = 87, // W
                x = 0.1f,
                y = 0.5f,
                width = 0.08f,
                height = 0.12f,
                label = "W"
            ),
            ControlButton(
                id = "left",
                keyCode = 65, // A
                x = 0.02f,
                y = 0.65f,
                width = 0.08f,
                height = 0.12f,
                label = "A"
            ),
            ControlButton(
                id = "backward",
                keyCode = 83, // S
                x = 0.1f,
                y = 0.65f,
                width = 0.08f,
                height = 0.12f,
                label = "S"
            ),
            ControlButton(
                id = "right",
                keyCode = 68, // D
                x = 0.18f,
                y = 0.65f,
                width = 0.08f,
                height = 0.12f,
                label = "D"
            ),
            
            // Jump and Sneak
            ControlButton(
                id = "jump",
                keyCode = 32, // Space
                x = 0.85f,
                y = 0.7f,
                width = 0.12f,
                height = 0.15f,
                label = "Jump"
            ),
            ControlButton(
                id = "sneak",
                keyCode = Constants.KeyCodes.SNEAK,
                x = 0.02f,
                y = 0.82f,
                width = 0.1f,
                height = 0.1f,
                label = "Sneak"
            ),
            ControlButton(
                id = "sprint",
                keyCode = Constants.KeyCodes.SPRINT,
                x = 0.14f,
                y = 0.82f,
                width = 0.1f,
                height = 0.1f,
                label = "Sprint"
            ),
            
            // Action buttons (right side)
            ControlButton(
                id = "attack",
                keyCode = Constants.KeyCodes.MOUSE_LEFT,
                x = 0.75f,
                y = 0.5f,
                width = 0.1f,
                height = 0.15f,
                label = "Attack"
            ),
            ControlButton(
                id = "use",
                keyCode = Constants.KeyCodes.MOUSE_RIGHT,
                x = 0.87f,
                y = 0.5f,
                width = 0.1f,
                height = 0.15f,
                label = "Use"
            ),
            
            // Inventory and other
            ControlButton(
                id = "inventory",
                keyCode = Constants.KeyCodes.INVENTORY,
                x = 0.5f,
                y = 0.02f,
                width = 0.08f,
                height = 0.08f,
                label = "E"
            ),
            ControlButton(
                id = "drop",
                keyCode = Constants.KeyCodes.DROP,
                x = 0.4f,
                y = 0.02f,
                width = 0.08f,
                height = 0.08f,
                label = "Q"
            ),
            ControlButton(
                id = "chat",
                keyCode = Constants.KeyCodes.CHAT,
                x = 0.6f,
                y = 0.02f,
                width = 0.08f,
                height = 0.08f,
                label = "T"
            ),
            ControlButton(
                id = "perspective",
                keyCode = Constants.KeyCodes.PERSPECTIVE,
                x = 0.7f,
                y = 0.02f,
                width = 0.08f,
                height = 0.08f,
                label = "F5"
            ),
            
            // Hotbar slots
            *createHotbarButtons()
        )
    }

    private fun createHotbarButtons(): Array<ControlButton> {
        return (1..9).map { slot ->
            ControlButton(
                id = "hotbar_$slot",
                keyCode = Constants.KeyCodes.HOTBAR_1 + slot - 1,
                x = 0.1f + (slot - 1) * 0.085f,
                y = 0.92f,
                width = 0.07f,
                height = 0.07f,
                label = slot.toString()
            )
        }.toTypedArray()
    }

    suspend fun loadLayout(layoutId: String? = null): ControlLayout {
        val id = layoutId ?: preferencesManager.preferences.first().customControlsJson?.let {
            // Try to parse saved layout ID
            try {
                val json = org.json.JSONObject(it)
                json.optString("currentLayoutId")
            } catch (e: Exception) {
                null
            }
        }

        return if (id != null && id != "default") {
            loadCustomLayout(id) ?: getDefaultLayout()
        } else {
            getDefaultLayout()
        }
    }

    private fun loadCustomLayout(layoutId: String): ControlLayout? {
        val file = File(controlsDir, "$layoutId.json")
        if (!file.exists()) return null

        return try {
            val adapter = moshi.adapter(ControlLayout::class.java)
            adapter.fromJson(file.readText())
        } catch (e: Exception) {
            Timber.e(e, "Failed to load control layout: $layoutId")
            null
        }
    }

    suspend fun saveLayout(layout: ControlLayout): Result<Unit> {
        return try {
            val file = File(controlsDir, "${layout.id}.json")
            val adapter = moshi.adapter(ControlLayout::class.java)
            file.writeText(adapter.toJson(layout))
            
            currentLayout = layout
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save control layout")
            Result.failure(e)
        }
    }

    suspend fun createLayout(name: String): ControlLayout {
        val layout = ControlLayout(
            id = UUID.randomUUID().toString(),
            name = name,
            buttons = createDefaultButtons(),
            isDefault = false
        )
        saveLayout(layout)
        return layout
    }

    suspend fun duplicateLayout(sourceLayout: ControlLayout, newName: String): ControlLayout {
        val newLayout = sourceLayout.copy(
            id = UUID.randomUUID().toString(),
            name = newName,
            isDefault = false
        )
        saveLayout(newLayout)
        return newLayout
    }

    suspend fun deleteLayout(layoutId: String): Result<Unit> {
        if (layoutId == "default") {
            return Result.failure(Exception("Cannot delete default layout"))
        }

        return try {
            val file = File(controlsDir, "$layoutId.json")
            if (file.exists()) {
                file.delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAllLayouts(): List<ControlLayout> {
        val layouts = mutableListOf<ControlLayout>()
        
        // Add default layout
        layouts.add(
            ControlLayout(
                id = "default",
                name = "Default Layout",
                buttons = createDefaultButtons(),
                isDefault = true
            )
        )

        // Load custom layouts
        controlsDir.listFiles()?.filter { it.extension == "json" }?.forEach { file ->
            try {
                val adapter = moshi.adapter(ControlLayout::class.java)
                adapter.fromJson(file.readText())?.let { layout ->
                    layouts.add(layout)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load layout: ${file.name}")
            }
        }

        return layouts
    }

    fun updateButton(layout: ControlLayout, updatedButton: ControlButton): ControlLayout {
        val newButtons = layout.buttons.map { button ->
            if (button.id == updatedButton.id) updatedButton else button
        }
        return layout.copy(buttons = newButtons)
    }

    fun addButton(layout: ControlLayout, button: ControlButton): ControlLayout {
        return layout.copy(buttons = layout.buttons + button)
    }

    fun removeButton(layout: ControlLayout, buttonId: String): ControlLayout {
        return layout.copy(buttons = layout.buttons.filter { it.id != buttonId })
    }

    suspend fun applyScale(layout: ControlLayout, scale: Float): ControlLayout {
        val scaledButtons = layout.buttons.map { button ->
            button.copy(
                width = button.width * scale,
                height = button.height * scale
            )
        }
        return layout.copy(buttons = scaledButtons)
    }

    suspend fun applyOpacity(layout: ControlLayout, opacity: Float): ControlLayout {
        val adjustedButtons = layout.buttons.map { button ->
            button.copy(opacity = opacity)
        }
        return layout.copy(buttons = adjustedButtons)
    }

    fun resetToDefault(): ControlLayout {
        return ControlLayout(
            id = "default",
            name = "Default Layout",
            buttons = createDefaultButtons(),
            isDefault = true
        )
    }
}
