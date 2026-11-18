package com.example

import com.example.data.BikeCatalog
import com.example.data.TrackMode
import com.example.engine.RaceInput
import com.example.engine.RacingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingEngineTest {

    @Test
    fun superbikeAcceleratesTo300KmhAndCapsAt300() {
        val bike = BikeCatalog.bikes.first()
        val engine = RacingEngine(bike, TrackMode.SUNSET_HIGHWAY)
        engine.entities.clear() // Test unobstructed straightaway acceleration to 300 km/h

        // Advance past 2.6s countdown
        repeat(180) {
            engine.step(0.016f, RaceInput(throttle = true))
        }

        // Hold full throttle + aero boost on straight
        var maxReached = 0
        repeat(1500) {
            val hud = engine.step(0.016f, RaceInput(throttle = true, boost = true))
            if (hud.speedKmh > maxReached) maxReached = hud.speedKmh
        }

        assertEquals(300, maxReached)
        assertTrue(engine.speedKmh <= 300.0f)
        assertEquals(6, engine.buildHud().gear)
    }

    @Test
    fun brakingReducesSpeedProperly() {
        val bike = BikeCatalog.bikes[1]
        val engine = RacingEngine(bike, TrackMode.NIGHT_CIRCUIT)
        engine.entities.clear()

        repeat(200) { engine.step(0.016f, RaceInput(throttle = true)) }
        repeat(400) { engine.step(0.016f, RaceInput(throttle = true)) }
        val speedBeforeBrake = engine.speedKmh
        assertTrue(speedBeforeBrake > 180f)

        repeat(60) { engine.step(0.016f, RaceInput(brake = true)) }
        assertTrue(engine.speedKmh < speedBeforeBrake - 60f)
    }
}
