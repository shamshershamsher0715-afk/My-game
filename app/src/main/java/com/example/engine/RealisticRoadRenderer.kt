package com.example.engine

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.data.TrackMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reusable zero-allocation 60fps pseudo-3D perspective road, scenery, vehicle, and superbike renderer.
 */
class RealisticRoadRenderer {

    private val drawCount = RacingEngine.DRAW_DISTANCE
    private val projX = FloatArray(drawCount + 1)
    private val projY = FloatArray(drawCount + 1)
    private val projW = FloatArray(drawCount + 1)
    private val projScale = FloatArray(drawCount + 1)
    private val segIndices = IntArray(drawCount)
    private val clipTopY = FloatArray(drawCount + 1)

    private val reusableQuadPath = Path()
    private val reusableTreePath = Path()
    private val reusableRiderPath = Path()

    fun DrawScope.renderFrame(
        engine: RacingEngine,
        skyBitmap: ImageBitmap,
        frameTicks: Long
    ) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return

        val isNight = engine.trackMode == TrackMode.NIGHT_CIRCUIT
        val speedNorm = (engine.speedKmh / RacingEngine.MAX_SPEED_KMH).coerceIn(0f, 1f)

        // Camera parameters with subtle speed FOV stretch & vibration
        val shakeX = if (engine.cameraShake > 0.005f) {
            sin(frameTicks * 0.9f) * engine.cameraShake * w * 0.012f
        } else 0f
        val shakeY = if (engine.cameraShake > 0.005f) {
            cos(frameTicks * 1.3f) * engine.cameraShake * h * 0.010f
        } else 0f

        val baseIndex = engine.segmentIndexAt(engine.playerZ)
        val segPercent = (engine.playerZ % RacingEngine.SEGMENT_LENGTH) / RacingEngine.SEGMENT_LENGTH
        val baseSeg = engine.segments[baseIndex]
        val playerWorldY = lerp(baseSeg.yStart, baseSeg.yEnd, segPercent)

        // Dynamic horizon pitch from road elevation + speed drop
        val cameraHeight = 960f - engine.aeroTuck * 90f
        val cameraDepth = 0.84f + speedNorm * 0.14f
        val horizonBaseY = h * 0.43f + shakeY

        // Project forward road segments
        var xCurveAcc = -(baseSeg.curve * segPercent)
        var dxCurve = baseSeg.curve * (1f - segPercent)
        var maxY = h

        for (n in 0 until drawCount) {
            val idx = (baseIndex + n) % RacingEngine.SEGMENT_COUNT
            segIndices[n] = idx
            val seg = engine.segments[idx]

            val relZ = ((n - segPercent) * RacingEngine.SEGMENT_LENGTH).coerceAtLeast(18f)
            val scale = cameraDepth / relZ
            val worldX = xCurveAcc * 420f - engine.playerX * RacingEngine.ROAD_HALF_WIDTH
            val worldY = seg.yStart - playerWorldY - cameraHeight

            val sx = w * 0.5f + (scale * worldX * w * 0.5f) + shakeX
            val sy = horizonBaseY - (scale * worldY * h * 0.5f)
            val sw = scale * RacingEngine.ROAD_HALF_WIDTH * w * 0.5f

            projX[n] = sx
            projY[n] = sy
            projW[n] = sw
            projScale[n] = scale
            clipTopY[n] = maxY
            if (sy < maxY) maxY = sy

            dxCurve += seg.curve
            xCurveAcc += dxCurve
        }
        // Final cap point
        val lastSeg = engine.segments[(baseIndex + drawCount) % RacingEngine.SEGMENT_COUNT]
        val lastRelZ = (drawCount - segPercent) * RacingEngine.SEGMENT_LENGTH
        val lastScale = cameraDepth / lastRelZ
        projX[drawCount] = w * 0.5f + (lastScale * (xCurveAcc * 420f - engine.playerX * RacingEngine.ROAD_HALF_WIDTH) * w * 0.5f) + shakeX
        projY[drawCount] = horizonBaseY - (lastScale * (lastSeg.yStart - playerWorldY - cameraHeight) * h * 0.5f)
        projW[drawCount] = lastScale * RacingEngine.ROAD_HALF_WIDTH * w * 0.5f
        projScale[drawCount] = lastScale
        clipTopY[drawCount] = maxY

        // 1. Skybox & Horizon Atmosphere
        val skyHorizonY = maxY.coerceIn(h * 0.25f, h * 0.62f)
        drawSkybox(skyBitmap, w, skyHorizonY + h * 0.08f, engine.skyScrollOffset, isNight)

        // Base distant ground fill below horizon
        drawRect(
            brush = Brush.verticalGradient(
                colors = if (isNight) {
                    listOf(Color(0xFF0A111C), Color(0xFF0F1722), Color(0xFF090D14))
                } else {
                    listOf(Color(0xFF3E3522), Color(0xFF282B1B), Color(0xFF1A1E15))
                },
                startY = skyHorizonY - 10f,
                endY = h
            ),
            topLeft = Offset(0f, skyHorizonY - 10f),
            size = Size(w, h - skyHorizonY + 12f)
        )

        // 2. Render road segments Back-to-Front
        for (n in drawCount - 1 downTo 0) {
            val y1 = projY[n]
            val y2 = projY[n + 1]
            // Hill occlusion cull
            if (y2 >= y1 || y2 >= clipTopY[n]) continue

            val seg = engine.segments[segIndices[n]]
            val x1 = projX[n]
            val w1 = projW[n]
            val x2 = projX[n + 1]
            val w2 = projW[n + 1]
            val fogAlpha = (n.toFloat() / drawCount).let { it * it * 0.72f }

            drawRoadSegment(
                seg = seg,
                x1 = x1, y1 = y1, w1 = w1,
                x2 = x2, y2 = y2, w2 = w2,
                screenW = w,
                isNight = isNight,
                fogAlpha = fogAlpha
            )
        }

        // 3. Render Roadside Props & Overhead Arches Back-to-Front
        for (n in drawCount - 1 downTo 1) {
            val seg = engine.segments[segIndices[n]]
            val sx = projX[n]
            val sy = projY[n]
            val sw = projW[n]
            if (sy > clipTopY[n]) continue
            val fogAlpha = (n.toFloat() / drawCount).let { it * it * 0.75f }

            if (seg.treeLeftKind > 0) {
                drawRoadsideProp(
                    kind = seg.treeLeftKind,
                    centerX = sx - sw * 1.62f,
                    baseY = sy,
                    roadHalfW = sw,
                    isNight = isNight,
                    fogAlpha = fogAlpha
                )
            }
            if (seg.treeRightKind > 0) {
                drawRoadsideProp(
                    kind = seg.treeRightKind,
                    centerX = sx + sw * 1.62f,
                    baseY = sy,
                    roadHalfW = sw,
                    isNight = isNight,
                    fogAlpha = fogAlpha
                )
            }
            if (seg.hasLampLeft) {
                drawStreetLamp(
                    baseX = sx - sw * 1.24f,
                    baseY = sy,
                    roadHalfW = sw,
                    isLeft = true,
                    isNight = isNight
                )
            }
            if (seg.hasLampRight) {
                drawStreetLamp(
                    baseX = sx + sw * 1.24f,
                    baseY = sy,
                    roadHalfW = sw,
                    isLeft = false,
                    isNight = isNight
                )
            }
            if (seg.hasArch) {
                drawOverheadArch(
                    centerX = sx,
                    baseY = sy,
                    roadHalfW = sw,
                    isNight = isNight,
                    isFinish = seg.index < 15
                )
            }
        }

        // 4. Render Traffic Vehicles & Rival Superbikes sorted Back-to-Front
        val visibleEntities = engine.entities
            .mapNotNull { entity ->
                val relZ = entity.totalZ - engine.playerTotalZ
                if (relZ in 45f..(drawCount * RacingEngine.SEGMENT_LENGTH * 0.92f)) {
                    entity to relZ
                } else null
            }
            .sortedByDescending { it.second }

        for ((entity, relZ) in visibleEntities) {
            val segFloat = (relZ / RacingEngine.SEGMENT_LENGTH)
            val n0 = segFloat.toInt().coerceIn(0, drawCount - 1)
            val n1 = (n0 + 1).coerceAtMost(drawCount)
            val frac = (segFloat - n0).coerceIn(0f, 1f)

            val roadX = lerp(projX[n0], projX[n1], frac)
            val roadY = lerp(projY[n0], projY[n1], frac)
            val roadW = lerp(projW[n0], projW[n1], frac)
            if (roadY > clipTopY[n0] + 10f) continue

            val vx = roadX + entity.laneX * roadW
            val fog = (n0.toFloat() / drawCount).coerceIn(0f, 0.7f)
            drawTrafficEntity(
                entity = entity,
                centerX = vx,
                groundY = roadY,
                roadHalfW = roadW,
                isNight = isNight,
                fogAlpha = fog
            )
        }

        // 5. High-Speed 300 km/h Wind Tunnel Streaks & Slipstream Ribbons
        if (speedNorm > 0.65f || engine.isSlipstreaming) {
            drawSpeedEffects(
                w = w,
                h = h,
                speedNorm = speedNorm,
                isBoosting = engine.isBoosting,
                isSlipstreaming = engine.isSlipstreaming,
                frameTicks = frameTicks
            )
        }

        // 6. Player Superbike & Leaning Rider
        val bikeCenterX = w * 0.5f + shakeX * 0.5f
        val bikeGroundY = h * 0.875f + shakeY * 0.4f
        val bikeScale = (minOf(w, h) / 410f).coerceIn(0.85f, 1.55f)
        drawPlayerSuperbike(
            engine = engine,
            centerX = bikeCenterX,
            groundY = bikeGroundY,
            scale = bikeScale,
            isNight = isNight,
            frameTicks = frameTicks
        )

        // 7. Sparks & Exhaust Backfire Particles
        for (p in engine.particles) {
            val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
            drawCircle(
                color = p.color.copy(alpha = alpha),
                radius = (4.5f * alpha + 1.5f) * bikeScale,
                center = Offset(p.xNorm * w, p.yNorm * h)
            )
        }
    }

    private fun DrawScope.drawSkybox(
        skyBitmap: ImageBitmap,
        w: Float,
        skyHeight: Float,
        scrollOffset: Float,
        isNight: Boolean
    ) {
        val imgW = skyBitmap.width.toFloat()
        val imgH = skyBitmap.height.toFloat()
        if (imgW <= 0f || imgH <= 0f) return

        // Tile seamlessly horizontally using modulo offset
        val wrappedScroll = ((scrollOffset % 1f) + 1f) % 1f
        val drawW = maxOf(w, skyHeight * (imgW / imgH))
        val xStart = -wrappedScroll * drawW

        drawImage(
            image = skyBitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(skyBitmap.width, skyBitmap.height),
            dstOffset = IntOffset(xStart.toInt(), 0),
            dstSize = IntSize(drawW.toInt() + 2, skyHeight.toInt() + 2)
        )
        if (xStart + drawW < w) {
            drawImage(
                image = skyBitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(skyBitmap.width, skyBitmap.height),
                dstOffset = IntOffset((xStart + drawW).toInt(), 0),
                dstSize = IntSize(drawW.toInt() + 2, skyHeight.toInt() + 2)
            )
        }

        // Atmospheric mist near horizon for depth realism
        val mistColor = if (isNight) Color(0xFF0A111C) else Color(0x66F59E0B)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, mistColor),
                startY = skyHeight * 0.55f,
                endY = skyHeight
            ),
            topLeft = Offset(0f, skyHeight * 0.55f),
            size = Size(w, skyHeight * 0.45f)
        )
    }

    private fun DrawScope.drawRoadSegment(
        seg: RoadSegment,
        x1: Float, y1: Float, w1: Float,
        x2: Float, y2: Float, w2: Float,
        screenW: Float,
        isNight: Boolean,
        fogAlpha: Float
    ) {
        val alt = seg.isAltBand
        val grassColor = when {
            isNight && alt -> Color(0xFF0E1621)
            isNight -> Color(0xFF0A1019)
            alt -> Color(0xFF28341E)
            else -> Color(0xFF212C18)
        }
        drawRect(
            color = grassColor,
            topLeft = Offset(0f, y2),
            size = Size(screenW, (y1 - y2) + 1.5f)
        )

        // Concrete / Gravel Runoff Shoulder
        val shoulderColor = if (isNight) Color(0xFF1A222E) else Color(0xFF3E3A33)
        drawQuad(x1, y1, w1 * 1.28f, x2, y2, w2 * 1.28f, shoulderColor)

        // FIA Red/White Rumble Curbs
        val curbColor = if (alt) Color(0xFFE10600) else Color(0xFFF1F5F9)
        drawQuad(x1, y1, w1 * 1.11f, x2, y2, w2 * 1.11f, curbColor)

        // Asphalt Road Surface with subtle band variation
        val asphaltColor = when {
            isNight && alt -> Color(0xFF171D26)
            isNight -> Color(0xFF1D2430)
            alt -> Color(0xFF22262E)
            else -> Color(0xFF2A2F39)
        }
        drawQuad(x1, y1, w1, x2, y2, w2, asphaltColor)

        // Realistic wet/worn tire-track grooves & center specular sheen
        val sheenColor = if (isNight) Color(0x1838BDF8) else Color(0x18FDE68A)
        drawQuad(x1, y1, w1 * 0.48f, x2, y2, w2 * 0.48f, sheenColor)

        // White outer boundary lines
        val edgeWidth1 = (w1 * 0.032f).coerceAtLeast(1f)
        val edgeWidth2 = (w2 * 0.032f).coerceAtLeast(0.6f)
        drawStrip(x1 - w1 * 0.94f, y1, edgeWidth1, x2 - w2 * 0.94f, y2, edgeWidth2, Color(0xFFE2E8F0))
        drawStrip(x1 + w1 * 0.94f, y1, edgeWidth1, x2 + w2 * 0.94f, y2, edgeWidth2, Color(0xFFE2E8F0))

        // Lane dividers (4-lane highway / GP racing markers)
        if (!alt) {
            val dashColor = if (isNight) Color(0xFFCBD5E1) else Color(0xFFFBBF24)
            val dW1 = (w1 * 0.020f).coerceAtLeast(0.8f)
            val dW2 = (w2 * 0.020f).coerceAtLeast(0.5f)
            drawStrip(x1, y1, dW1, x2, y2, dW2, dashColor)
            drawStrip(x1 - w1 * 0.47f, y1, dW1 * 0.85f, x2 - w2 * 0.47f, y2, dW2 * 0.85f, Color(0xAA94A3B8))
            drawStrip(x1 + w1 * 0.47f, y1, dW1 * 0.85f, x2 + w2 * 0.47f, y2, dW2 * 0.85f, Color(0xAA94A3B8))
        }

        // Start/Finish Checkerboard Banner on segment 0..2
        if (seg.index in 0..2) {
            val checkColor = if (seg.index % 2 == 0) Color.White else Color(0xFF0F172A)
            drawQuad(x1, y1, w1 * 0.92f, x2, y2, w2 * 0.92f, checkColor.copy(alpha = 0.85f))
        }

        // Metallic Armco Guardrails on outer shoulders
        val railOffset1 = w1 * 1.30f
        val railOffset2 = w2 * 1.30f
        val railThickness1 = (w1 * 0.035f).coerceAtLeast(1f)
        val railThickness2 = (w2 * 0.035f).coerceAtLeast(0.6f)
        val railColor = if (alt) Color(0xFF64748B) else Color(0xFF94A3B8)
        drawStrip(x1 - railOffset1, y1, railThickness1, x2 - railOffset2, y2, railThickness2, railColor)
        drawStrip(x1 + railOffset1, y1, railThickness1, x2 + railOffset2, y2, railThickness2, railColor)

        // Distance Atmospheric Fog Haze
        if (fogAlpha > 0.04f) {
            val fogTint = if (isNight) Color(0xFF0A111C) else Color(0xFFD97706)
            drawRect(
                color = fogTint.copy(alpha = fogAlpha * 0.48f),
                topLeft = Offset(0f, y2),
                size = Size(screenW, (y1 - y2) + 1.5f)
            )
        }
    }

    private fun DrawScope.drawRoadsideProp(
        kind: Int,
        centerX: Float,
        baseY: Float,
        roadHalfW: Float,
        isNight: Boolean,
        fogAlpha: Float
    ) {
        val h = roadHalfW * 0.95f
        if (h < 5f) return

        if (kind == 1) {
            // Layered Pine / Cypress Tree with ground shadow
            drawOval(
                color = Color(0x55000000),
                topLeft = Offset(centerX - h * 0.25f, baseY - h * 0.04f),
                size = Size(h * 0.5f, h * 0.12f)
            )
            // Trunk
            drawRect(
                color = Color(0xFF3E2723),
                topLeft = Offset(centerX - h * 0.04f, baseY - h * 0.28f),
                size = Size(h * 0.08f, h * 0.28f)
            )
            val leafDark = if (isNight) Color(0xFF0A241A) else Color(0xFF1B4332)
            val leafLight = if (isNight) Color(0xFF133A29) else Color(0xFF2D6A4F)

            for (tier in 0..2) {
                val tierW = h * (0.34f - tier * 0.07f)
                val tierBottom = baseY - h * (0.22f + tier * 0.21f)
                val tierTop = tierBottom - h * 0.34f
                reusableTreePath.reset()
                reusableTreePath.moveTo(centerX, tierTop)
                reusableTreePath.lineTo(centerX - tierW, tierBottom)
                reusableTreePath.lineTo(centerX + tierW, tierBottom)
                reusableTreePath.close()
                drawPath(reusableTreePath, if (tier % 2 == 0) leafDark else leafLight)
            }
        } else {
            // Motorsport Distance / Sponsor Signboard
            val boardW = h * 0.65f
            val boardH = h * 0.36f
            drawRect(
                color = Color(0xFF475569),
                topLeft = Offset(centerX - boardW * 0.06f, baseY - h * 0.65f),
                size = Size(boardW * 0.12f, h * 0.65f)
            )
            drawRoundRect(
                color = Color(0xFF0F172A),
                topLeft = Offset(centerX - boardW * 0.5f, baseY - h * 0.92f),
                size = Size(boardW, boardH),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRect(
                color = Color(0xFFE10600),
                topLeft = Offset(centerX - boardW * 0.44f, baseY - h * 0.86f),
                size = Size(boardW * 0.88f, boardH * 0.28f)
            )
        }

        if (fogAlpha > 0.25f) {
            drawCircle(
                color = (if (isNight) Color(0xFF0A111C) else Color(0xFFD97706)).copy(alpha = fogAlpha * 0.25f),
                radius = h * 0.35f,
                center = Offset(centerX, baseY - h * 0.45f)
            )
        }
    }

    private fun DrawScope.drawStreetLamp(
        baseX: Float,
        baseY: Float,
        roadHalfW: Float,
        isLeft: Boolean,
        isNight: Boolean
    ) {
        val poleH = roadHalfW * 1.15f
        if (poleH < 6f) return
        val armX = if (isLeft) baseX + roadHalfW * 0.28f else baseX - roadHalfW * 0.28f
        val topY = baseY - poleH

        drawLine(
            color = Color(0xFF64748B),
            start = Offset(baseX, baseY),
            end = Offset(baseX, topY),
            strokeWidth = (roadHalfW * 0.032f).coerceAtLeast(1.2f)
        )
        drawLine(
            color = Color(0xFF94A3B8),
            start = Offset(baseX, topY),
            end = Offset(armX, topY + poleH * 0.04f),
            strokeWidth = (roadHalfW * 0.025f).coerceAtLeast(1f)
        )

        val lightColor = if (isNight) Color(0xFF38BDF8) else Color(0xFFFDE68A)
        val glowRadius = roadHalfW * (if (isNight) 0.26f else 0.09f)
        if (isNight) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lightColor.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(armX, topY + poleH * 0.05f),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(armX, topY + poleH * 0.05f)
            )
        }
        drawCircle(
            color = lightColor,
            radius = (roadHalfW * 0.035f).coerceAtLeast(1.5f),
            center = Offset(armX, topY + poleH * 0.05f)
        )
    }

    private fun DrawScope.drawOverheadArch(
        centerX: Float,
        baseY: Float,
        roadHalfW: Float,
        isNight: Boolean,
        isFinish: Boolean
    ) {
        if (roadHalfW < 10f) return
        val archH = roadHalfW * 0.92f
        val leftX = centerX - roadHalfW * 1.15f
        val rightX = centerX + roadHalfW * 1.15f
        val topY = baseY - archH
        val pillarW = (roadHalfW * 0.08f).coerceAtLeast(2f)

        // Pillars
        drawRect(Color(0xFF334155), Offset(leftX - pillarW * 0.5f, topY), Size(pillarW, archH))
        drawRect(Color(0xFF334155), Offset(rightX - pillarW * 0.5f, topY), Size(pillarW, archH))

        // Main overhead truss
        val bannerH = (roadHalfW * 0.22f).coerceAtLeast(4f)
        drawRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(leftX, topY),
            size = Size(rightX - leftX, bannerH)
        )
        drawRect(
            color = if (isFinish) Color(0xFFE10600) else if (isNight) Color(0xFF00E5FF) else Color(0xFFFFB300),
            topLeft = Offset(leftX + pillarW, topY + bannerH * 0.2f),
            size = Size((rightX - leftX) - pillarW * 2f, bannerH * 0.5f)
        )
    }

    private fun DrawScope.drawTrafficEntity(
        entity: TrafficEntity,
        centerX: Float,
        groundY: Float,
        roadHalfW: Float,
        isNight: Boolean,
        fogAlpha: Float
    ) {
        if (roadHalfW < 5f) return

        when (entity.kind) {
            VehicleKind.RIVAL_BIKE -> {
                val bikeW = roadHalfW * 0.14f
                val bikeH = roadHalfW * 0.24f
                // Ground shadow
                drawOval(
                    color = Color(0x77000000),
                    topLeft = Offset(centerX - bikeW * 0.7f, groundY - bikeH * 0.08f),
                    size = Size(bikeW * 1.4f, bikeH * 0.18f)
                )
                rotate(degrees = entity.leanDeg, pivot = Offset(centerX, groundY)) {
                    // Rear tire
                    drawRoundRect(
                        color = Color(0xFF0B0E14),
                        topLeft = Offset(centerX - bikeW * 0.22f, groundY - bikeH * 0.44f),
                        size = Size(bikeW * 0.44f, bikeH * 0.44f),
                        cornerRadius = CornerRadius(bikeW * 0.15f, bikeW * 0.15f)
                    )
                    // Fairing / Tail
                    drawRoundRect(
                        color = entity.bodyColor,
                        topLeft = Offset(centerX - bikeW * 0.42f, groundY - bikeH * 0.68f),
                        size = Size(bikeW * 0.84f, bikeH * 0.34f),
                        cornerRadius = CornerRadius(bikeW * 0.25f, bikeW * 0.25f)
                    )
                    // Rider torso & helmet
                    drawOval(
                        color = entity.accentColor,
                        topLeft = Offset(centerX - bikeW * 0.36f, groundY - bikeH * 0.92f),
                        size = Size(bikeW * 0.72f, bikeH * 0.42f)
                    )
                    drawCircle(
                        color = Color(0xFFE2E8F0),
                        radius = bikeW * 0.27f,
                        center = Offset(centerX, groundY - bikeH * 0.95f)
                    )
                    // LED Taillight
                    drawRoundRect(
                        color = Color(0xFFFF1E1E),
                        topLeft = Offset(centerX - bikeW * 0.26f, groundY - bikeH * 0.56f),
                        size = Size(bikeW * 0.52f, bikeH * 0.08f),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                }
            }

            VehicleKind.SPORTS_CAR, VehicleKind.SUV -> {
                val isSuv = entity.kind == VehicleKind.SUV
                val carW = roadHalfW * (if (isSuv) 0.38f else 0.36f)
                val carH = roadHalfW * (if (isSuv) 0.29f else 0.21f)

                // Drop shadow
                drawOval(
                    color = Color(0x88000000),
                    topLeft = Offset(centerX - carW * 0.58f, groundY - carH * 0.12f),
                    size = Size(carW * 1.16f, carH * 0.24f)
                )

                // Rear tires
                val tireW = carW * 0.17f
                val tireH = carH * 0.28f
                drawRoundRect(
                    color = Color(0xFF0A0D12),
                    topLeft = Offset(centerX - carW * 0.48f, groundY - tireH),
                    size = Size(tireW, tireH),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                drawRoundRect(
                    color = Color(0xFF0A0D12),
                    topLeft = Offset(centerX + carW * 0.48f - tireW, groundY - tireH),
                    size = Size(tireW, tireH),
                    cornerRadius = CornerRadius(3f, 3f)
                )

                // Lower chassis / bumper
                val bodyTop = groundY - carH * 0.62f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(entity.bodyColor, entity.accentColor),
                        startY = bodyTop,
                        endY = groundY - carH * 0.1f
                    ),
                    topLeft = Offset(centerX - carW * 0.5f, bodyTop),
                    size = Size(carW, carH * 0.52f),
                    cornerRadius = CornerRadius(carW * 0.08f, carW * 0.08f)
                )

                // Cabin & Rear Windshield
                val roofW = carW * (if (isSuv) 0.80f else 0.72f)
                val cabinTop = groundY - carH
                drawRoundRect(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(centerX - roofW * 0.5f, cabinTop),
                    size = Size(roofW, carH * 0.42f),
                    cornerRadius = CornerRadius(carW * 0.10f, carW * 0.10f)
                )
                // Glass reflection
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0x5538BDF8), Color(0x11FFFFFF)),
                        start = Offset(centerX - roofW * 0.4f, cabinTop),
                        end = Offset(centerX + roofW * 0.4f, bodyTop)
                    ),
                    topLeft = Offset(centerX - roofW * 0.42f, cabinTop + carH * 0.06f),
                    size = Size(roofW * 0.84f, carH * 0.28f),
                    cornerRadius = CornerRadius(carW * 0.05f, carW * 0.05f)
                )

                // Glowing LED Taillights
                val tailW = carW * 0.20f
                val tailH = carH * 0.11f
                val tailY = groundY - carH * 0.46f
                if (isNight) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x66FF1744), Color.Transparent),
                            center = Offset(centerX - carW * 0.34f, tailY + tailH * 0.5f),
                            radius = carW * 0.24f
                        ),
                        radius = carW * 0.24f,
                        center = Offset(centerX - carW * 0.34f, tailY + tailH * 0.5f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x66FF1744), Color.Transparent),
                            center = Offset(centerX + carW * 0.34f, tailY + tailH * 0.5f),
                            radius = carW * 0.24f
                        ),
                        radius = carW * 0.24f,
                        center = Offset(centerX + carW * 0.34f, tailY + tailH * 0.5f)
                    )
                }
                drawRoundRect(
                    color = Color(0xFFFF1744),
                    topLeft = Offset(centerX - carW * 0.44f, tailY),
                    size = Size(tailW, tailH),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                drawRoundRect(
                    color = Color(0xFFFF1744),
                    topLeft = Offset(centerX + carW * 0.44f - tailW, tailY),
                    size = Size(tailW, tailH),
                    cornerRadius = CornerRadius(2f, 2f)
                )

                // Rear license plate highlight
                drawRect(
                    color = Color(0xFFE2E8F0),
                    topLeft = Offset(centerX - carW * 0.10f, groundY - carH * 0.30f),
                    size = Size(carW * 0.20f, carH * 0.11f)
                )
            }
        }

        if (fogAlpha > 0.22f) {
            drawCircle(
                color = (if (isNight) Color(0xFF0A111C) else Color(0xFFD97706)).copy(alpha = fogAlpha * 0.25f),
                radius = roadHalfW * 0.22f,
                center = Offset(centerX, groundY - roadHalfW * 0.12f)
            )
        }
    }

    private fun DrawScope.drawPlayerSuperbike(
        engine: RacingEngine,
        centerX: Float,
        groundY: Float,
        scale: Float,
        isNight: Boolean,
        frameTicks: Long
    ) {
        val bike = engine.bike
        val lean = engine.leanDeg
        val tuckDrop = engine.aeroTuck * 14f * scale

        // Ground contact shadow + red taillight asphalt reflection
        drawOval(
            color = Color(0xAA000000),
            topLeft = Offset(centerX - 38f * scale + lean * 0.4f, groundY - 10f * scale),
            size = Size(76f * scale, 20f * scale)
        )
        if (engine.isBraking || isNight) {
            val glowAlpha = if (engine.isBraking) 0.55f else 0.28f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF1744).copy(alpha = glowAlpha), Color.Transparent),
                    center = Offset(centerX + lean * 0.35f, groundY + 4f * scale),
                    radius = 65f * scale
                ),
                topLeft = Offset(centerX - 65f * scale + lean * 0.35f, groundY - 12f * scale),
                size = Size(130f * scale, 34f * scale)
            )
        }

        rotate(degrees = lean, pivot = Offset(centerX, groundY)) {
            // Wide Rear Racing Slick Tire
            val tireW = 30f * scale
            val tireH = 54f * scale
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF0A0C10), Color(0xFF272B34), Color(0xFF0A0C10)),
                    startX = centerX - tireW * 0.5f,
                    endX = centerX + tireW * 0.5f
                ),
                topLeft = Offset(centerX - tireW * 0.5f, groundY - tireH),
                size = Size(tireW, tireH),
                cornerRadius = CornerRadius(12f * scale, 12f * scale)
            )

            // Swingarm & Gold Monoshock
            drawRect(
                color = Color(0xFF64748B),
                topLeft = Offset(centerX - 20f * scale, groundY - 40f * scale),
                size = Size(6f * scale, 26f * scale)
            )
            drawRect(
                color = Color(0xFF64748B),
                topLeft = Offset(centerX + 14f * scale, groundY - 40f * scale),
                size = Size(6f * scale, 26f * scale)
            )
            drawRect(
                color = Color(0xFFF59E0B),
                topLeft = Offset(centerX - 3f * scale, groundY - 56f * scale),
                size = Size(6f * scale, 18f * scale)
            )

            // Dual Titanium Exhaust Pipes + Backfire Flames
            val exOffset = 22f * scale
            val exY = groundY - 44f * scale
            for (side in listOf(-1f, 1f)) {
                val exCenter = centerX + side * exOffset
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF475569), Color(0xFFE2E8F0), Color(0xFF334155)),
                        startX = exCenter - 6f * scale,
                        endX = exCenter + 6f * scale
                    ),
                    topLeft = Offset(exCenter - 5.5f * scale, exY),
                    size = Size(11f * scale, 20f * scale),
                    cornerRadius = CornerRadius(4f * scale, 4f * scale)
                )
                // Heat-blued exhaust bore
                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = 4f * scale,
                    center = Offset(exCenter, exY + 14f * scale)
                )
                drawCircle(
                    color = Color(0xFF38BDF8),
                    radius = 4.6f * scale,
                    center = Offset(exCenter, exY + 14f * scale),
                    style = Stroke(width = 1.4f * scale)
                )

                // Exhaust flame burst on Aero-300 Boost or redline upshift
                if (engine.isBoosting || engine.buildHud().shiftFlash) {
                    val flicker = 0.8f + 0.35f * sin(frameTicks * 1.4f + side)
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, bike.flameCore, Color.Transparent),
                            center = Offset(exCenter, exY + 20f * scale),
                            radius = 16f * scale * flicker
                        ),
                        topLeft = Offset(exCenter - 9f * scale, exY + 11f * scale),
                        size = Size(18f * scale, 28f * scale * flicker)
                    )
                }
            }

            // Sculpted Aerodynamic Tail & Fairings
            val fairingTop = groundY - 90f * scale + tuckDrop * 0.3f
            reusableRiderPath.reset()
            reusableRiderPath.moveTo(centerX, fairingTop - 16f * scale)
            reusableRiderPath.lineTo(centerX - 30f * scale, fairingTop + 18f * scale)
            reusableRiderPath.lineTo(centerX - 22f * scale, groundY - 38f * scale)
            reusableRiderPath.lineTo(centerX + 22f * scale, groundY - 38f * scale)
            reusableRiderPath.lineTo(centerX + 30f * scale, fairingTop + 18f * scale)
            reusableRiderPath.close()
            drawPath(
                path = reusableRiderPath,
                brush = Brush.verticalGradient(
                    colors = listOf(bike.primaryLivery, bike.secondaryLivery),
                    startY = fairingTop,
                    endY = groundY - 36f * scale
                )
            )

            // Winglet / Clip-on Handlebars
            drawLine(
                color = Color(0xFFCBD5E1),
                start = Offset(centerX - 38f * scale, fairingTop + 2f * scale),
                end = Offset(centerX + 38f * scale, fairingTop + 2f * scale),
                strokeWidth = 4.5f * scale,
                cap = StrokeCap.Round
            )

            // Dual Razor LED Taillights
            val tailColor = if (engine.isBraking) Color(0xFFFF3B30) else Color(0xFFE10600)
            drawRoundRect(
                color = tailColor,
                topLeft = Offset(centerX - 20f * scale, groundY - 66f * scale),
                size = Size(14f * scale, 5.5f * scale),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawRoundRect(
                color = tailColor,
                topLeft = Offset(centerX + 6f * scale, groundY - 66f * scale),
                size = Size(14f * scale, 5.5f * scale),
                cornerRadius = CornerRadius(3f, 3f)
            )

            // Rider Suit & Knee Sliders (body hangs slightly into turn)
            val hangX = lean * 0.32f * scale
            val riderHipY = groundY - 76f * scale + tuckDrop * 0.5f
            val riderShoulderY = groundY - 122f * scale + tuckDrop

            // Rider Legs & Knee Sliders
            drawRoundRect(
                color = bike.suitPrimary,
                topLeft = Offset(centerX - 26f * scale + hangX * 0.4f, riderHipY - 4f * scale),
                size = Size(15f * scale, 34f * scale),
                cornerRadius = CornerRadius(6f * scale, 6f * scale)
            )
            drawRoundRect(
                color = bike.suitPrimary,
                topLeft = Offset(centerX + 11f * scale + hangX * 0.4f, riderHipY - 4f * scale),
                size = Size(15f * scale, 34f * scale),
                cornerRadius = CornerRadius(6f * scale, 6f * scale)
            )

            // Rider Torso + Aero Speed Hump
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(bike.suitPrimary, Color(0xFF0F172A)),
                    startY = riderShoulderY,
                    endY = riderHipY + 8f * scale
                ),
                topLeft = Offset(centerX - 24f * scale + hangX * 0.7f, riderShoulderY),
                size = Size(48f * scale, (riderHipY - riderShoulderY) + 12f * scale),
                cornerRadius = CornerRadius(18f * scale, 18f * scale)
            )
            // Aero spine hump stripe
            drawRoundRect(
                color = bike.suitAccent,
                topLeft = Offset(centerX - 7f * scale + hangX * 0.75f, riderShoulderY + 6f * scale),
                size = Size(14f * scale, 26f * scale),
                cornerRadius = CornerRadius(7f * scale, 7f * scale)
            )

            // Racing Helmet & Glossy Iridium Visor Reflection
            val helmetCenter = Offset(centerX + hangX, riderShoulderY - 12f * scale + tuckDrop * 0.25f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, bike.suitAccent, Color(0xFF1E293B)),
                    center = Offset(helmetCenter.x - 5f * scale, helmetCenter.y - 6f * scale),
                    radius = 22f * scale
                ),
                radius = 17.5f * scale,
                center = helmetCenter
            )
            // Helmet rear aero spoiler fin
            drawRoundRect(
                color = bike.primaryLivery,
                topLeft = Offset(helmetCenter.x - 9f * scale, helmetCenter.y + 4f * scale),
                size = Size(18f * scale, 6f * scale),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
    }

    private fun DrawScope.drawSpeedEffects(
        w: Float,
        h: Float,
        speedNorm: Float,
        isBoosting: Boolean,
        isSlipstreaming: Boolean,
        frameTicks: Long
    ) {
        val intensity = ((speedNorm - 0.62f) / 0.38f).coerceIn(0f, 1f)
        val cx = w * 0.5f
        val cy = h * 0.44f
        val streakCount = if (isBoosting) 24 else 16
        val streakColor = when {
            isBoosting -> Color(0xFF00E5FF)
            isSlipstreaming -> Color(0xFF10B981)
            else -> Color.White
        }

        for (i in 0 until streakCount) {
            val angle = (i.toFloat() / streakCount) * 2f * PI.toFloat() + (frameTicks % 10) * 0.04f
            // Keep center clear so road stays crystal sharp
            val innerR = minOf(w, h) * (0.42f + (i % 3) * 0.05f)
            val outerR = maxOf(w, h) * 0.88f
            val alpha = (0.10f + intensity * 0.26f) * if ((i + frameTicks) % 2 == 0L) 1f else 0.55f

            drawLine(
                color = streakColor.copy(alpha = alpha),
                start = Offset(cx + cos(angle) * innerR, cy + sin(angle) * innerR),
                end = Offset(cx + cos(angle) * outerR, cy + sin(angle) * outerR),
                strokeWidth = if (isBoosting) 2.8f else 1.8f,
                cap = StrokeCap.Round
            )
        }
    }

    private fun DrawScope.drawQuad(
        x1: Float, y1: Float, halfW1: Float,
        x2: Float, y2: Float, halfW2: Float,
        color: Color
    ) {
        reusableQuadPath.reset()
        reusableQuadPath.moveTo(x1 - halfW1, y1 + 0.8f)
        reusableQuadPath.lineTo(x1 + halfW1, y1 + 0.8f)
        reusableQuadPath.lineTo(x2 + halfW2, y2 - 0.5f)
        reusableQuadPath.lineTo(x2 - halfW2, y2 - 0.5f)
        reusableQuadPath.close()
        drawPath(reusableQuadPath, color)
    }

    private fun DrawScope.drawStrip(
        cx1: Float, y1: Float, halfW1: Float,
        cx2: Float, y2: Float, halfW2: Float,
        color: Color
    ) {
        reusableQuadPath.reset()
        reusableQuadPath.moveTo(cx1 - halfW1, y1 + 0.8f)
        reusableQuadPath.lineTo(cx1 + halfW1, y1 + 0.8f)
        reusableQuadPath.lineTo(cx2 + halfW2, y2 - 0.5f)
        reusableQuadPath.lineTo(cx2 - halfW2, y2 - 0.5f)
        reusableQuadPath.close()
        drawPath(reusableQuadPath, color)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
