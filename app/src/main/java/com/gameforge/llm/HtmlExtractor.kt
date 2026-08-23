package com.gameforge.llm

import android.util.Log

/**
 * LLM 출력에서 HTML 게임 코드를 추출하고 검증.
 */
object HtmlExtractor {

    private const val TAG = "HtmlExtractor"

    /**
     * LLM 응답에서 HTML 코드를 추출.
     * 마크다운 코드 블록, 설명 텍스트 등을 제거하고 순수 HTML만 반환.
     */
    fun extractHtml(response: String): String? {
        // 1. 마크다운 코드 블록에서 추출
        val codeBlockRegex = Regex("```(?:html)?\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val codeBlockMatch = codeBlockRegex.find(response)
        if (codeBlockMatch != null) {
            val html = codeBlockMatch.groupValues[1].trim()
            if (isValidHtml(html)) return html
        }

        // 2. <!DOCTYPE html> 또는 <html 로 시작하는 블록 찾기
        val htmlStartRegex = Regex("(<!DOCTYPE\\s+html|<html)[\\s\\S]*?(</html>)", RegexOption.IGNORE_CASE)
        val htmlMatch = htmlStartRegex.find(response)
        if (htmlMatch != null) {
            val html = htmlMatch.value.trim()
            if (isValidHtml(html)) return html
        }

        // 3. 전체 응답이 HTML인지 확인
        val trimmed = response.trim()
        if (isValidHtml(trimmed)) return trimmed

        Log.w(TAG, "No valid HTML found in LLM response (${response.length} chars)")
        return null
    }

    /**
     * HTML이 유효한 게임 코드인지 검증.
     * 필수 JS 인터페이스가 포함되어 있는지 확인.
     */
    fun isValidHtml(html: String): Boolean {
        if (html.isBlank()) return false
        if (html.length > 200_000) return false // 200KB 제한

        // 기본 HTML 구조 확인
        val hasHtmlTag = html.contains("<html", ignoreCase = true) ||
                         html.contains("<!DOCTYPE", ignoreCase = true) ||
                         html.contains("<body", ignoreCase = true)
        if (!hasHtmlTag) return false

        // 필수 JS 인터페이스 확인
        val hasGetGameState = html.contains("getGameState", ignoreCase = true)
        val hasSetGameState = html.contains("setGameState", ignoreCase = true)
        val hasGetGameInfo = html.contains("getGameInfo", ignoreCase = true)

        if (!hasGetGameState || !hasSetGameState || !hasGetGameInfo) {
            Log.w(TAG, "HTML missing required JS interfaces: " +
                "getGameState=$hasGetGameState, setGameState=$hasSetGameState, getGameInfo=$hasGetGameInfo")
            return false
        }

        return true
    }

    /**
     * HTML에서 위험한 패턴을 검출.
     * 외부 네트워크 접근, eval, Function 등 보안 위험 차단.
     */
    fun validateSecurity(html: String): SecurityCheckResult {
        val violations = mutableListOf<String>()

        // 외부 네트워크 접근 차단
        if (html.contains("XMLHttpRequest", ignoreCase = true)) {
            violations.add("XMLHttpRequest 사용 감지 — 외부 네트워크 접근 금지")
        }
        if (html.contains("fetch(", ignoreCase = true)) {
            // fetch(COLOR) 같은 false positive 방지 — URL 패턴만 차단
            val urlFetch = Regex("""fetch\s*\(\s*['"]https?://""", RegexOption.IGNORE_CASE)
            if (urlFetch.containsMatchIn(html)) {
                violations.add("fetch(URL) 사용 감지 — 외부 네트워크 접근 금지")
            }
        }

        // 동적 코드 실행 차단
        if (html.contains("eval(", ignoreCase = true)) {
            violations.add("eval() 사용 감지 — 동적 코드 실행 금지")
        }
        if (html.contains("new Function(", ignoreCase = true)) {
            violations.add("new Function() 사용 감지 — 동적 코드 실행 금지")
        }
        if (html.contains("import(", ignoreCase = true)) {
            violations.add("import() 사용 감지 — 동적 모듈 로딩 금지")
        }

        // 외부 스크립트 로딩 차단
        val externalScript = Regex("""<script[^>]*src\s*=\s*['"]https?://""", RegexOption.IGNORE_CASE)
        if (externalScript.containsMatchIn(html)) {
            violations.add("외부 스크립트 로딩 감지 — 자가완결 HTML만 허용")
        }

        // 외부 스타일시트 차단
        val externalCss = Regex("""<link[^>]*href\s*=\s*['"]https?://""", RegexOption.IGNORE_CASE)
        if (externalCss.containsMatchIn(html)) {
            violations.add("외부 스타일시트 로딩 감지 — 자가완결 HTML만 허용")
        }

        return SecurityCheckResult(
            isSecure = violations.isEmpty(),
            violations = violations,
        )
    }

    /**
     * getGameInfo()에서 게임 메타데이터 추출.
     */
    fun parseGameInfo(json: String): GameMetadata? {
        return try {
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            GameMetadata(
                name = obj.get("name")?.asString ?: "이름 없음",
                emoji = obj.get("emoji")?.asString ?: "🎮",
                difficulty = obj.get("difficulty")?.asInt ?: 1,
                tags = obj.get("tags")?.asJsonArray?.map { it.asString } ?: emptyList(),
                version = obj.get("version")?.asInt ?: 1,
            )
        } catch (_: Exception) {
            null
        }
    }

    data class SecurityCheckResult(
        val isSecure: Boolean,
        val violations: List<String>,
    )

    data class GameMetadata(
        val name: String,
        val emoji: String,
        val difficulty: Int,
        val tags: List<String>,
        val version: Int,
    )
}