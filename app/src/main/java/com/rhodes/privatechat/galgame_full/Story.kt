package com.rhodes.privatechat.galgame_full

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.io.File

data class DialogueLine(val speakerId: String, val speaker: String, val text: String, val spriteId: String? = null, val pageIndex: Int = 0, val pageCount: Int = 1)
data class CharacterConfig(
    val id: String = "",
    val name: String = "",
    val personality: String = "",
    val goal: String = "",
    val speechStyle: String = "",
    val relationshipToPlayer: String = "",
    val secret: String = "",
    val taboos: String = "",
    val sprites: List<SpriteRef> = emptyList()
)
data class SpriteRef(val assetId: String = "", val name: String = "", val usageCondition: String = "")
data class ChapterConfig(
    val id: Int = 1,
    val title: String = "",
    val goal: String = "",
    val allowedCharacterIds: List<String> = emptyList(),
    val completionHint: String = "",
    val minAffection: Int = 0,
    val requiredFlag: String = "",
    val contentDescription: String = "",
    val allowedBackgroundIds: List<String> = emptyList(),
    val openingDescription: String = "",
    val isFinal: Boolean = false,
    val atmosphere: String = "",
    val notes: String = "",
    val spoilers: String = ""
)
data class NumberVariableConfig(
    val id: String = "",
    val name: String = "",
    val initial: Int = 0,
    val minimum: Int = 0,
    val maximum: Int = 100,
    val perTurnMinimum: Int = -5,
    val perTurnMaximum: Int = 5,
    val description: String = ""
)
data class ItemConfig(val id: String = "", val name: String = "", val description: String = "")
data class EventConfig(val id: String = "", val name: String = "", val description: String = "")
data class AuthorDraft(
    val title: String = "",
    val description: String = "",
    val characters: List<CharacterConfig> = emptyList(),
    val chapters: List<ChapterConfig> = emptyList(),
    val variables: List<NumberVariableConfig> = emptyList(),
    val items: List<ItemConfig> = emptyList(),
    val events: List<EventConfig> = emptyList(),
    val questions: List<String> = emptyList()
)
data class ProgressReport(
    val statusDescription: String,
    val chapterStatus: String,
    val completion: Boolean,
    val unmetConditions: List<String>,
    val nextDirection: String,
    val evidence: List<String>,
    val chapterSummary: String = "",
    val keyFacts: List<String> = emptyList(),
    val relationshipNotes: List<String> = emptyList()
)
data class ProjectConfig(
    val title: String = "",
    val description: String = "",
    val style: String = "",
    val restrictions: String = "",
    val playerCharacterId: String = "",
    val characters: List<CharacterConfig> = emptyList(),
    val chapters: List<ChapterConfig> = emptyList(),
    val variables: List<NumberVariableConfig> = emptyList(),
    val items: List<ItemConfig> = emptyList(),
    val events: List<EventConfig> = emptyList()
)
data class SceneState(
    val backgroundId: String = "",
    val visibleCharacterId: String? = null,
    val visibleSpriteId: String? = null,
    val backgroundFocusX: Float = .5f,
    val backgroundFocusY: Float = .5f,
    val spriteScale: Float = 1f,
    val spriteOffsetX: Float = 0f,
    val spriteOffsetY: Float = 0f
)
data class SpriteAsset(val id: String, val characterId: String, val name: String, val uri: String, val tags: List<String>, val usageCondition: String = "", val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f)
data class BackgroundAsset(val id: String, val name: String, val uri: String, val tags: List<String>, val usageCondition: String = "", val focusX: Float = .5f, val focusY: Float = .5f)

data class GameState(
    val projectId: String = "",
    val project: ProjectConfig = ProjectConfig(),
    val chapter: Int = 1,
    val chapterTurns: Int = 0,
    val variables: Map<String, Int> = emptyMap(),
    val items: Set<String> = emptySet(),
    val events: Set<String> = emptySet(),
    val affection: Int = 0,
    val hasMap: Boolean = false,
    val agreed: Boolean = false,
    val goal: String = "",
    val messages: List<StoryMessage> = emptyList(),
    val lines: List<DialogueLine> = emptyList(),
    val lineIndex: Int = 0,
    val linePage: Int = 0,
    val choices: List<String> = emptyList(),
    val scene: SceneState = SceneState(),
    val endingShown: Boolean = false,
    val endingContinued: Boolean = false,
    val pendingTransition: StoryTransition? = null
    ,val storyMemory: String = "",
    val chapterProgress: ProgressReport? = null
) {
    val isPlayingLines: Boolean get() = lineIndex < lines.size
    val currentLine: DialogueLine? get() = lines.getOrNull(lineIndex)
    fun chapterConfig(): ChapterConfig? = project.chapters.find { it.id == chapter }
}

data class StoryMessage(val speaker: String, val text: String)
data class StoryTransition(val type: String, val title: String, val subtitle: String)

data class ProjectAssets(
    val sprites: List<SpriteAsset> = emptyList(),
    val backgrounds: List<BackgroundAsset> = emptyList(),
    val globalSpriteScale: Float = 1f,
    val globalSpriteOffsetX: Float = 0f,
    val globalSpriteOffsetY: Float = 0f
)
data class LibraryEntry(val id: String, val title: String, val description: String, val stateJson: String = "", val assetsJson: String = "")
data class SaveSlot(val slot: Int, val title: String = "", val chapter: Int = 0, val stateJson: String = "", val assetsJson: String = "")
data class EditorDraft(
    val step: Int,
    val stateJson: String,
    val assetsJson: String,
    val updatedAt: Long,
    val pendingSpriteUri: String = "",
    val pendingBackgroundUri: String = "",
    val pendingSpriteName: String = "",
    val pendingSpriteCondition: String = "",
    val pendingBackgroundName: String = "",
    val pendingBackgroundCondition: String = "",
    val selectedCharacterId: String = "",
    val selectedChapterId: Int = 0,
    val selectedSpriteId: String = "",
    val selectedBackgroundId: String = ""
)

fun GameState.unmetChapterRequirements(): List<String> {
    val chapter = chapterConfig() ?: return emptyList()
    if (affection < chapter.minAffection) return listOf("好感度还需要达到 ${chapter.minAffection}（当前 $affection）")
    val eventId = chapter.requiredFlag.trim()
    if (eventId.isBlank() || eventId in events) return emptyList()
    val eventName = project.events.find { it.id == eventId }?.name?.takeIf(String::isNotBlank) ?: eventId
    return listOf("需要完成事件：$eventName")
}

fun GameState.canCompleteCurrentChapter(): Boolean = unmetChapterRequirements().isEmpty()

fun createInitialGameState(projectId: String, project: ProjectConfig): GameState {
    val first = project.chapters.minByOrNull { it.id }
    val opening = first?.openingDescription?.takeIf(String::isNotBlank)
        ?: first?.contentDescription?.takeIf(String::isNotBlank)
        ?: "故事开始了。"
    return GameState(
        projectId = projectId,
        project = project,
        chapter = first?.id ?: 1,
        variables = project.variables.associate { it.id to it.initial },
        goal = first?.goal.orEmpty(),
        messages = listOf(StoryMessage("旁白", opening)),
        scene = SceneState(backgroundId = first?.allowedBackgroundIds?.firstOrNull().orEmpty())
    )
}

object GameStore {
    private const val PREFS = "galgame_full_module"
    private const val STATE = "game_state"
    private const val ASSETS = "project_assets"
    private const val LIBRARY = "game_library"
    private const val SAVES = "play_saves"
    private const val EDITOR_DRAFT = "editor_draft"

    fun copyAssetToPrivate(context: Context, source: String, scope: String): String {
        val uri = Uri.parse(source)
        val directory = File(context.filesDir, "galgame_full/$scope/assets").apply { mkdirs() }
        if (uri.scheme == "file") {
            val sourceFile = File(uri.path.orEmpty()).canonicalFile
            val directoryPath = directory.canonicalFile.path + File.separator
            if (sourceFile.path.startsWith(directoryPath)) return source
        }
        val target = File(directory, "asset_${System.currentTimeMillis()}_${(0..9999).random()}.bin")
        openAssetInput(context, source)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            ?: error("无法读取图片资源")
        return Uri.fromFile(target).toString()
    }

    fun clearLegacyData(context: Context) {
        context.deleteSharedPreferences("galgame_module")
        File(context.filesDir, "galgame").deleteRecursively()
    }


    fun loadSaves(context: Context): List<SaveSlot> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SAVES, "[]") ?: "[]")
        List(3) { slot ->
            (0 until array.length()).firstOrNull { array.getJSONObject(it).optInt("slot") == slot }?.let { index ->
                val item = array.getJSONObject(index)
                SaveSlot(slot, item.optString("title"), item.optInt("chapter"), item.optString("stateJson"), item.optString("assetsJson"))
            } ?: SaveSlot(slot)
        }
    }.getOrDefault(List(3) { SaveSlot(it) })

    fun saveSlot(context: Context, slot: Int, state: GameState, assets: ProjectAssets) {
        save(context, state); saveAssets(context, assets)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = loadSaves(context).filterNot { it.slot == slot } + SaveSlot(slot, state.project.title, state.chapter, prefs.getString(STATE, "") ?: "", prefs.getString(ASSETS, "") ?: "")
        prefs.edit().putString(SAVES, JSONArray().apply { next.forEach { put(JSONObject().put("slot", it.slot).put("title", it.title).put("chapter", it.chapter).put("stateJson", it.stateJson).put("assetsJson", it.assetsJson)) } }.toString()).apply()
    }

    fun loadSlot(context: Context, slot: SaveSlot, expectedProjectId: String): Boolean {
        if (slot.stateJson.isBlank()) return false
        val savedProjectId = runCatching { JSONObject(slot.stateJson).optString("projectId") }.getOrDefault("")
        if (savedProjectId.isBlank() || savedProjectId != expectedProjectId) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(STATE, slot.stateJson).putString(ASSETS, slot.assetsJson).apply()
        return true
    }

    fun loadLibrary(context: Context): List<LibraryEntry> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LIBRARY, "[]") ?: "[]"
        val array = JSONArray(raw)
        List(array.length()) { index -> array.getJSONObject(index).let { LibraryEntry(it.optString("id"), it.optString("title"), it.optString("description"), it.optString("stateJson"), it.optString("assetsJson")) } }
    }.getOrDefault(emptyList())

    fun publish(context: Context, entry: LibraryEntry) {
        val next = loadLibrary(context).filterNot { it.id == entry.id } + entry
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LIBRARY, JSONArray().apply { next.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("description", it.description).put("stateJson", it.stateJson).put("assetsJson", it.assetsJson)) } }.toString()).apply()
    }

    fun deletePublished(context: Context, id: String) {
        val next = loadLibrary(context).filterNot { it.id == id }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LIBRARY, JSONArray().apply { next.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("description", it.description).put("stateJson", it.stateJson).put("assetsJson", it.assetsJson)) } }.toString()).apply()
        File(context.filesDir, "rdg/$id").deleteRecursively()
        File(context.filesDir, "galgame_full/projects/$id").deleteRecursively()
    }

    fun clearCurrent(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(STATE).remove(ASSETS).apply()
    }

    fun saveEditorDraft(context: Context, step: Int, state: GameState, assets: ProjectAssets, pendingSpriteUri: String = "", pendingBackgroundUri: String = "", pendingSpriteName: String = "", pendingSpriteCondition: String = "", pendingBackgroundName: String = "", pendingBackgroundCondition: String = "", selectedCharacterId: String = "", selectedChapterId: Int = 0, selectedSpriteId: String = "", selectedBackgroundId: String = "") {
        val snapshot = snapshot(context, state.projectId.ifBlank { "draft" }, state, assets)
        val value = JSONObject()
            .put("step", step)
            .put("stateJson", snapshot.stateJson)
            .put("assetsJson", snapshot.assetsJson)
            .put("updatedAt", System.currentTimeMillis())
            .put("pendingSpriteUri", pendingSpriteUri)
            .put("pendingBackgroundUri", pendingBackgroundUri)
            .put("pendingSpriteName", pendingSpriteName)
            .put("pendingSpriteCondition", pendingSpriteCondition)
            .put("pendingBackgroundName", pendingBackgroundName)
            .put("pendingBackgroundCondition", pendingBackgroundCondition)
            .put("selectedCharacterId", selectedCharacterId)
            .put("selectedChapterId", selectedChapterId)
            .put("selectedSpriteId", selectedSpriteId)
            .put("selectedBackgroundId", selectedBackgroundId)
            .toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(EDITOR_DRAFT, value).apply()
    }

    fun loadEditorDraft(context: Context): EditorDraft? = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(EDITOR_DRAFT, null) ?: return null
        val value = JSONObject(raw)
        EditorDraft(value.optInt("step", 1).coerceIn(1, 5), value.getString("stateJson"), value.getString("assetsJson"), value.optLong("updatedAt"), value.optString("pendingSpriteUri"), value.optString("pendingBackgroundUri"), value.optString("pendingSpriteName"), value.optString("pendingSpriteCondition"), value.optString("pendingBackgroundName"), value.optString("pendingBackgroundCondition"), value.optString("selectedCharacterId"), value.optInt("selectedChapterId"), value.optString("selectedSpriteId"), value.optString("selectedBackgroundId"))
    }.getOrNull()

    fun clearEditorDraft(context: Context) {
        loadEditorDraft(context)?.let { draft ->
            val projectId = runCatching { JSONObject(draft.stateJson).optString("projectId") }.getOrDefault("").ifBlank { "draft" }
            File(context.filesDir, "galgame_full/drafts/$projectId").deleteRecursively()
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(EDITOR_DRAFT).apply()
    }

    fun openAssetInput(context: Context, uriText: String) = Uri.parse(uriText).let { uri ->
        if (uri.scheme == "file") File(uri.path.orEmpty()).takeIf(File::exists)?.inputStream()
        else context.contentResolver.openInputStream(uri)
    }

    fun snapshot(context: Context, id: String, state: GameState, assets: ProjectAssets): LibraryEntry =
        LibraryEntry(id, state.project.title, state.project.description, state.toJson().toString(), assets.toJson().toString())

    fun restore(context: Context, entry: LibraryEntry) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentState = prefs.getString(STATE, null)
        val currentProjectId = currentState?.let { runCatching { JSONObject(it).optString("projectId") }.getOrDefault("") }
        if (currentProjectId == entry.id) return
        prefs.edit().putString(STATE, entry.stateJson).putString(ASSETS, entry.assetsJson).apply()
    }

    fun load(context: Context): GameState = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(STATE, null) ?: return GameState()
        stateFromJson(raw)
    }.getOrDefault(GameState())

    fun stateFromJson(raw: String): GameState {
        val j = JSONObject(raw)
        val project = j.optJSONObject("project")?.toProject() ?: ProjectConfig()
        val values = j.optJSONObject("variables")?.let { obj -> project.variables.associate { it.id to obj.optInt(it.id, it.initial) } } ?: emptyMap()
        return GameState(projectId = j.optString("projectId"), project = project, chapter = j.optInt("chapter", 1), chapterTurns = j.optInt("chapterTurns"), variables = values, items = j.optJSONArray("items")?.toStrings()?.toSet().orEmpty(), events = j.optJSONArray("events")?.toStrings()?.toSet().orEmpty(), affection = j.optInt("affection", values["affection_aya"] ?: 0), hasMap = j.optBoolean("hasMap"), agreed = j.optBoolean("agreed"), goal = j.optString("goal"),
            messages = j.optJSONArray("messages")?.toMessages().orEmpty(), lines = j.optJSONArray("lines")?.toLines().orEmpty(), lineIndex = j.optInt("lineIndex"), linePage = j.optInt("linePage"),
            choices = j.optJSONArray("choices")?.toStrings().orEmpty(), scene = j.optJSONObject("scene")?.toScene() ?: SceneState(), endingShown = j.optBoolean("endingShown"), endingContinued = j.optBoolean("endingContinued"), pendingTransition = j.optJSONObject("transition")?.toTransition(), storyMemory = j.optString("storyMemory"), chapterProgress = j.optJSONObject("chapterProgress")?.toProgressReport())
    }

    fun save(context: Context, state: GameState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(STATE, state.toJson().toString()).apply()
    }

    private fun GameState.toJson(): JSONObject = JSONObject().put("projectId", projectId).put("project", project.toJson()).put("chapter", chapter).put("chapterTurns", chapterTurns).put("variables", JSONObject().apply { variables.forEach { (id, value) -> put(id, value) } }).put("items", JSONArray(items.toList())).put("events", JSONArray(events.toList())).put("affection", affection).put("hasMap", hasMap).put("agreed", agreed).put("goal", goal)
            .put("messages", JSONArray().apply { messages.forEach { put(JSONObject().put("speaker", it.speaker).put("text", it.text)) } })
            .put("lines", JSONArray().apply { lines.forEach { put(JSONObject().put("speakerId", it.speakerId).put("speaker", it.speaker).put("text", it.text).put("spriteId", it.spriteId)) } })
            .put("lineIndex", lineIndex).put("linePage", linePage).put("choices", JSONArray(choices)).put("scene", scene.toJson()).put("endingShown", endingShown).put("endingContinued", endingContinued).put("transition", pendingTransition?.toJson()).put("storyMemory", storyMemory).put("chapterProgress", chapterProgress?.toJson())

    fun loadAssets(context: Context): ProjectAssets = runCatching {
        assetsFromJson(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ASSETS, "{}") ?: "{}")
    }.getOrDefault(ProjectAssets())

    fun assetsFromJson(raw: String): ProjectAssets {
        val j = JSONObject(raw)
        return ProjectAssets(j.optJSONArray("sprites")?.toSprites().orEmpty(), j.optJSONArray("backgrounds")?.toBackgrounds().orEmpty(), j.optDouble("globalSpriteScale", 1.0).toFloat(), j.optDouble("globalSpriteOffsetX", 0.0).toFloat(), j.optDouble("globalSpriteOffsetY", 0.0).toFloat())
    }

    fun saveAssets(context: Context, assets: ProjectAssets) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ASSETS, assets.toJson().toString()).apply()
    }

    private fun ProjectAssets.toJson(): JSONObject = JSONObject().put("sprites", JSONArray().apply { sprites.forEach { put(JSONObject().put("id", it.id).put("characterId", it.characterId).put("name", it.name).put("uri", it.uri).put("tags", JSONArray(it.tags)).put("usageCondition", it.usageCondition).put("scale", it.scale).put("offsetX", it.offsetX).put("offsetY", it.offsetY)) } })
            .put("backgrounds", JSONArray().apply { backgrounds.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("uri", it.uri).put("tags", JSONArray(it.tags)).put("usageCondition", it.usageCondition).put("focusX", it.focusX).put("focusY", it.focusY)) } })
            .put("globalSpriteScale", globalSpriteScale).put("globalSpriteOffsetX", globalSpriteOffsetX).put("globalSpriteOffsetY", globalSpriteOffsetY)

    private fun JSONArray.toStrings() = List(length()) { optString(it) }
    private fun JSONArray.toMessages() = List(length()) { getJSONObject(it).let { j -> StoryMessage(j.optString("speaker"), j.optString("text")) } }
    private fun JSONArray.toLines() = List(length()) { getJSONObject(it).let { j -> DialogueLine(j.optString("speakerId"), j.optString("speaker"), j.optString("text"), j.optString("spriteId").ifBlank { null }) } }
    private fun JSONArray.toSprites() = List(length()) { getJSONObject(it).let { j -> SpriteAsset(j.optString("id"), j.optString("characterId"), j.optString("name"), j.optString("uri"), j.optJSONArray("tags")?.toStrings().orEmpty(), j.optString("usageCondition"), j.optDouble("scale", 1.0).toFloat(), j.optDouble("offsetX", 0.0).toFloat(), j.optDouble("offsetY", 0.0).toFloat()) } }
    private fun JSONArray.toBackgrounds() = List(length()) { getJSONObject(it).let { j -> BackgroundAsset(j.optString("id"), j.optString("name"), j.optString("uri"), j.optJSONArray("tags")?.toStrings().orEmpty(), j.optString("usageCondition"), j.optDouble("focusX", .5).toFloat(), j.optDouble("focusY", .5).toFloat()) } }
    private fun SceneState.toJson() = JSONObject().put("backgroundId", backgroundId).put("visibleCharacterId", visibleCharacterId).put("visibleSpriteId", visibleSpriteId).put("focusX", backgroundFocusX).put("focusY", backgroundFocusY).put("scale", spriteScale).put("offsetX", spriteOffsetX).put("offsetY", spriteOffsetY)
    private fun JSONObject.toScene() = SceneState(optString("backgroundId"), optString("visibleCharacterId").ifBlank { null }, optString("visibleSpriteId").ifBlank { null }, optDouble("focusX", .5).toFloat(), optDouble("focusY", .5).toFloat(), optDouble("scale", 1.0).toFloat(), optDouble("offsetX", 0.0).toFloat(), optDouble("offsetY", 0.0).toFloat())
    private fun StoryTransition.toJson() = JSONObject().put("type", type).put("title", title).put("subtitle", subtitle)
    private fun JSONObject.toTransition() = StoryTransition(optString("type"), optString("title"), optString("subtitle"))
    private fun ProgressReport.toJson() = JSONObject().put("statusDescription", statusDescription).put("chapterStatus", chapterStatus).put("completion", completion).put("unmetConditions", JSONArray(unmetConditions)).put("nextDirection", nextDirection).put("evidence", JSONArray(evidence)).put("chapterSummary", chapterSummary).put("keyFacts", JSONArray(keyFacts)).put("relationshipNotes", JSONArray(relationshipNotes))
    private fun JSONObject.toProgressReport() = ProgressReport(optString("statusDescription"), optString("chapterStatus", "in_progress"), optBoolean("completion"), optJSONArray("unmetConditions")?.toStrings().orEmpty(), optString("nextDirection"), optJSONArray("evidence")?.toStrings().orEmpty(), optString("chapterSummary"), optJSONArray("keyFacts")?.toStrings().orEmpty(), optJSONArray("relationshipNotes")?.toStrings().orEmpty())
    private fun ProjectConfig.toJson() = JSONObject().put("title", title).put("description", description).put("style", style).put("restrictions", restrictions).put("playerCharacterId", playerCharacterId)
        .put("characters", JSONArray().apply { characters.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("personality", it.personality).put("goal", it.goal).put("speechStyle", it.speechStyle).put("relationshipToPlayer", it.relationshipToPlayer).put("secret", it.secret).put("taboos", it.taboos).put("sprites", JSONArray().apply { it.sprites.forEach { s -> put(JSONObject().put("assetId", s.assetId).put("name", s.name).put("usageCondition", s.usageCondition)) } })) } })
        .put("chapters", JSONArray().apply { chapters.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("goal", it.goal).put("allowedCharacterIds", JSONArray(it.allowedCharacterIds)).put("completionHint", it.completionHint).put("minAffection", it.minAffection).put("requiredFlag", it.requiredFlag).put("contentDescription", it.contentDescription).put("allowedBackgroundIds", JSONArray(it.allowedBackgroundIds)).put("openingDescription", it.openingDescription).put("isFinal", it.isFinal).put("atmosphere", it.atmosphere).put("notes", it.notes).put("spoilers", it.spoilers)) } })
        .put("variables", JSONArray().apply { variables.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("initial", it.initial).put("minimum", it.minimum).put("maximum", it.maximum).put("perTurnMinimum", it.perTurnMinimum).put("perTurnMaximum", it.perTurnMaximum).put("description", it.description)) } })
        .put("items", JSONArray().apply { items.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("description", it.description)) } })
        .put("events", JSONArray().apply { events.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("description", it.description)) } })
    private fun JSONObject.toProject(): ProjectConfig {
        val chars = optJSONArray("characters")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { j -> CharacterConfig(j.optString("id"), j.optString("name"), j.optString("personality"), j.optString("goal"), j.optString("speechStyle"), j.optString("relationshipToPlayer"), j.optString("secret"), j.optString("taboos"), j.optJSONArray("sprites")?.let { s -> List(s.length()) { k -> s.getJSONObject(k).let { x -> SpriteRef(x.optString("assetId"), x.optString("name"), x.optString("usageCondition")) } } }.orEmpty()) } } }.orEmpty()
        val chapters = optJSONArray("chapters")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { j -> ChapterConfig(j.optInt("id", i + 1), j.optString("title"), j.optString("goal"), j.optJSONArray("allowedCharacterIds")?.toStrings().orEmpty(), j.optString("completionHint"), j.optInt("minAffection"), j.optString("requiredFlag"), j.optString("contentDescription"), j.optJSONArray("allowedBackgroundIds")?.toStrings().orEmpty(), j.optString("openingDescription"), j.optBoolean("isFinal"), j.optString("atmosphere"), j.optString("notes"), j.optString("spoilers")) } } }.orEmpty()
        val variables = optJSONArray("variables")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { j -> NumberVariableConfig(j.optString("id"), j.optString("name"), j.optInt("initial"), j.optInt("minimum"), j.optInt("maximum", 100), j.optInt("perTurnMinimum", -5), j.optInt("perTurnMaximum", 5), j.optString("description")) } } }.orEmpty()
        val items = optJSONArray("items")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { j -> ItemConfig(j.optString("id"), j.optString("name"), j.optString("description")) } } }.orEmpty()
        val events = optJSONArray("events")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { j -> EventConfig(j.optString("id"), j.optString("name"), j.optString("description")) } } }.orEmpty()
        return ProjectConfig(optString("title"), optString("description"), optString("style"), optString("restrictions"), optString("playerCharacterId"), chars, chapters, variables, items, events)
    }
}

object FallbackStory {
    fun reply(old: GameState, input: String): GameState {
        val character = old.project.characters.firstOrNull { it.id != old.project.playerCharacterId && it.id in old.chapterConfig()?.allowedCharacterIds.orEmpty() }
            ?: old.project.characters.firstOrNull { it.id != old.project.playerCharacterId }
        val speakerId = character?.id.orEmpty()
        val speakerName = character?.name ?: "旁白"
        val text = if (speakerId.isBlank()) "故事暂时停在这里。请继续描述你想做的事。" else "${speakerName}沉默了片刻，认真听完你的话。我们可以继续按照自己的方式推进这件事。"
        val defaultSpriteId = old.project.characters.firstOrNull { it.id == speakerId }?.sprites?.firstOrNull()?.assetId
        return old.copy(lines = listOf(DialogueLine(speakerId, speakerName, text, defaultSpriteId)), lineIndex = 0, choices = listOf("继续追问", "表达自己的想法", "暂时观察"), scene = old.scene.copy(visibleCharacterId = speakerId.ifBlank { null }, visibleSpriteId = defaultSpriteId))
    }
}
