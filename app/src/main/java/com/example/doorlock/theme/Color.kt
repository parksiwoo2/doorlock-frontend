package com.example.doorlock.theme

import androidx.compose.ui.graphics.Color

// ------------------------------------------------------------
// KHLUG Blue — 브랜드 강조색 (기존 값 그대로 유지)
// ------------------------------------------------------------
val PrimaryBlue = Color(0xFF00A0E9)
val LightBlue = Color(0xFF7AC8ED)

// ------------------------------------------------------------
// 중립 톤 — 요구사항: White / Light Gray / Gray / Dark Gray-Black 만 사용
// (보라/연보라 계열은 여기서부터 완전히 배제)
// ------------------------------------------------------------
val NeutralWhite = Color(0xFFFFFFFF)
val NeutralLightGray = Color(0xFFF2F2F2)   // 화면 배경, surfaceVariant 등
val NeutralGray = Color(0xFFBDBDBD)        // outline, 비활성 요소
val NeutralDarkGray = Color(0xFF4A4A4A)    // 보조 텍스트
val NeutralAlmostBlack = Color(0xFF1A1A1A) // 본문 텍스트

// 다크 테마용 중립 톤
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF2A2A2A)
val DarkBackground = Color(0xFF121212)
val DarkPrimaryContainer = Color(0xFF01527A)

val ErrorRed = Color(0xFFD32F2F)

// 기존 코드에서 참조하던 이름을 그대로 쓸 수 있도록 남겨둔 별칭.
// (다른 파일에서 BackgroundGray를 직접 import하는 곳이 있을 수 있어 하위 호환 유지)
val BackgroundGray = NeutralLightGray
