package com.gameforge.data

import com.gameforge.engine.BrowserEngine

/**
 * 게임 상태 버저닝 및 롤백 관리자.
 * HTML 코드와 게임 상태를 독립적으로 버저닝하고,
 * 호환성을 추적하여 안전한 롤백을 보장한다.
 */
class GameStateManager(private val repository: GameRepository) {

    // ── 상태 스냅샷 생성 ────────────────────────────────────────

    suspend fun snapshotState(
        gameId: String,
        gameStateJson: String,
        htmlSourceHash: String,
        source: String,  // "auto" | "manual" | "llm" | "restore"
        label: String? = null,
        thumbnailPath: String? = null,
    ): GameStateSnapshot {
        return repository.saveStateSnapshot(
            gameId = gameId,
            gameStateJson = gameStateJson,
            htmlSourceHash = htmlSourceHash,
            source = source,
            label = label,
            thumbnailPath = thumbnailPath,
        )
    }

    // ── HTML 버전 생성 ─────────────────────────────────────────

    suspend fun snapshotHtml(
        gameId: String,
        htmlSource: String,
        source: String,  // "initial" | "llm_edit" | "manual"
        description: String? = null,
    ): GameHtmlVersion {
        return repository.saveHtmlVersion(
            gameId = gameId,
            htmlSource = htmlSource,
            source = source,
            description = description,
        )
    }

    // ── 롤백 ───────────────────────────────────────────────────

    /**
     * 상태만 롤백. 같은 HTML 버전 내에서 안전.
     * 다른 HTML 버전의 상태면 CompatibilityWarning 반환.
     */
    suspend fun restoreState(
        gameId: String,
        snapshotId: String,
    ): RestoreResult {
        val snapshot = repository.getStateSnapshotById(snapshotId)
            ?: return RestoreResult.NotFound

        val currentHtmlHash = repository.getCurrentHtmlHash(gameId)

        if (snapshot.htmlSourceHash != currentHtmlHash) {
            val snapshotHtml = repository.getHtmlVersionByHash(gameId, snapshot.htmlSourceHash)
            val currentHtml = repository.getCurrentHtmlVersion(gameId)
            return RestoreResult.CompatibilityWarning(
                snapshotHtmlVersion = snapshotHtml,
                currentHtmlVersion = currentHtml,
            )
        }

        return RestoreResult.Success(snapshot.version, snapshot.gameStateJson)
    }

    /**
     * 코드+상태 통합 롤백.
     * 특정 HTML 버전으로 되돌리고, 같은 해시의 상태 스냅샷이 있으면 복원.
     */
    suspend fun restoreFull(
        gameId: String,
        htmlVersionId: String,
        stateSnapshotId: String? = null,
    ): RestoreResult {
        val htmlVersion = repository.getHtmlVersionById(htmlVersionId)
            ?: return RestoreResult.NotFound

        var stateJson: String? = null

        if (stateSnapshotId != null) {
            val snapshot = repository.getStateSnapshotById(stateSnapshotId)
                ?: return RestoreResult.NotFound

            // 호환성 검증 — 다른 HTML 해시의 상태는 거부
            if (snapshot.htmlSourceHash != htmlVersion.htmlSourceHash) {
                return RestoreResult.CompatibilityWarning(
                    snapshotHtmlVersion = repository.getHtmlVersionByHash(gameId, snapshot.htmlSourceHash),
                    currentHtmlVersion = htmlVersion,
                )
            }
            stateJson = snapshot.gameStateJson
        }

        return RestoreResult.FullRestore(
            htmlVersion = htmlVersion.version,
            htmlSource = htmlVersion.htmlSource,
            stateJson = stateJson,
        )
    }
}

sealed class RestoreResult {
    data class Success(val version: Int, val stateJson: String) : RestoreResult()
    data class FullRestore(
        val htmlVersion: Int,
        val htmlSource: String,
        val stateJson: String?,
    ) : RestoreResult()

    data class CompatibilityWarning(
        val snapshotHtmlVersion: GameHtmlVersion?,
        val currentHtmlVersion: GameHtmlVersion?,
    ) : RestoreResult()

    data object NotFound : RestoreResult()
}