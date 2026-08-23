package com.gameforge.engine

import kotlinx.coroutines.CompletableDeferred

/**
 * 브라우저 엔진 추상 인터페이스.
 * DevCompanion BrowserEngine 패턴을 GameForge에 맞게 포팅.
 * GeckoView 기반 구현만 사용 (WebView 제거됨).
 */
interface BrowserEngine {

    /** 현재 URL */
    val currentUrl: String

    /** 뒤로 갈 수 있는지 */
    val canGoBack: Boolean

    /** 앞으로 갈 수 있는지 */
    val canGoForward: Boolean

    /** 엔진 타입 식별자 */
    val engineType: String
        get() = "GeckoView"

    /** 엔진 상세 버전 */
    val engineDetail: String

    // ── 네비게이션 ────────────────────────────────────────────

    /** URL 로드 */
    fun loadUrl(url: String)

    /** HTML 데이터 로드 (게임 실행용) */
    fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String,
        encoding: String,
        historyUrl: String?,
    )

    /** 뒤로 가기 */
    fun goBack()

    /** 앞으로 가기 */
    fun goForward()

    /** 새로고침 */
    fun reload()

    /** 히스토리 초기화 */
    fun clearHistory()

    // ── JavaScript 평가 ────────────────────────────────────────

    /** JS 코드 실행 (비동기 결과 반환) */
    suspend fun evalJs(js: String): String

    /** JS 코드 실행 (결과 무시) */
    suspend fun evalJsVoid(js: String) {
        evalJs("(function() { $js })()")
    }

    // ── 게임 브릿지 ────────────────────────────────────────────

    /** 게임 상태 읽기 */
    suspend fun getGameState(): String = evalJs("JSON.stringify(window.getGameState())")

    /** 게임 상태 쓰기 */
    suspend fun setGameState(json: String) {
        val escaped = json
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        evalJsVoid("window.setGameState('$escaped')")
    }

    /** 게임 정보 읽기 */
    suspend fun getGameInfo(): String = evalJs("JSON.stringify(window.getGameInfo())")

    /** 게임 일시정지 */
    suspend fun pauseGame() = evalJsVoid("if(window.pauseGame) window.pauseGame()")

    /** 게임 재개 */
    suspend fun resumeGame() = evalJsVoid("if(window.resumeGame) window.resumeGame()")

    // ── 스크린샷 ────────────────────────────────────────────────

    /** 스크린샷 캡처 */
    suspend fun screenshot(): ByteArray?

    // ── 생명주기 ────────────────────────────────────────────────

    /** 엔진 초기화 */
    suspend fun initialize()

    /** 엔진 종료 */
    fun shutdown()

    /** 세션 초기화 (새 게임 로드 전 호출) */
    fun reset()
}