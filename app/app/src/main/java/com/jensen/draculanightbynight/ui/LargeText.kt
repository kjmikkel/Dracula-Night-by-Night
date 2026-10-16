package com.jensen.draculanightbynight.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

/** From 150 % text, layouts that put things side by side give them their own lines instead. */
@Composable
fun largeText(): Boolean = LocalDensity.current.fontScale >= LARGE_FONT_SCALE

private const val LARGE_FONT_SCALE = 1.5f
