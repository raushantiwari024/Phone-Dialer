package com.raushan.phone.telecom.model

import android.telecom.CallAudioState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallAudioModelTest {

    @Test
    fun `routeFromMask decodes each single route`() {
        assertEquals(AudioRoute.EARPIECE, CallAudioModel.routeFromMask(CallAudioState.ROUTE_EARPIECE))
        assertEquals(AudioRoute.SPEAKER, CallAudioModel.routeFromMask(CallAudioState.ROUTE_SPEAKER))
        assertEquals(AudioRoute.BLUETOOTH, CallAudioModel.routeFromMask(CallAudioState.ROUTE_BLUETOOTH))
        assertEquals(
            AudioRoute.WIRED_HEADSET,
            CallAudioModel.routeFromMask(CallAudioState.ROUTE_WIRED_HEADSET),
        )
    }

    /**
     * The regression this function exists for: `ROUTE_WIRED_OR_EARPIECE` is a *combined* mask, so
     * treating it as a single value reports the earpiece while a headset is plugged in.
     */
    @Test
    fun `routeFromMask prefers the wired headset over the earpiece in the combined mask`() {
        assertEquals(
            AudioRoute.WIRED_HEADSET,
            CallAudioModel.routeFromMask(CallAudioState.ROUTE_WIRED_OR_EARPIECE),
        )
    }

    @Test
    fun `routeFromMask ranks bluetooth above speaker above headset`() {
        val all = CallAudioState.ROUTE_BLUETOOTH or
            CallAudioState.ROUTE_SPEAKER or
            CallAudioState.ROUTE_WIRED_HEADSET or
            CallAudioState.ROUTE_EARPIECE
        assertEquals(AudioRoute.BLUETOOTH, CallAudioModel.routeFromMask(all))

        val withoutBluetooth = CallAudioState.ROUTE_SPEAKER or
            CallAudioState.ROUTE_WIRED_HEADSET or
            CallAudioState.ROUTE_EARPIECE
        assertEquals(AudioRoute.SPEAKER, CallAudioModel.routeFromMask(withoutBluetooth))
    }

    @Test
    fun `routeFromMask falls back to the earpiece for an empty mask`() {
        assertEquals(AudioRoute.EARPIECE, CallAudioModel.routeFromMask(0))
    }

    @Test
    fun `routesFromMask reports every set bit rather than ranking them`() {
        val mask = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER
        assertEquals(setOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER), CallAudioModel.routesFromMask(mask))
    }

    @Test
    fun `routesFromMask expands the combined wired-or-earpiece mask into both routes`() {
        assertEquals(
            setOf(AudioRoute.EARPIECE, AudioRoute.WIRED_HEADSET),
            CallAudioModel.routesFromMask(CallAudioState.ROUTE_WIRED_OR_EARPIECE),
        )
    }

    @Test
    fun `routesFromMask returns nothing for an empty mask`() {
        assertTrue(CallAudioModel.routesFromMask(0).isEmpty())
    }

    @Test
    fun `defaultEarRoute returns the headset when one is connected`() {
        val withHeadset = CallAudioModel(supportedRoutes = setOf(AudioRoute.WIRED_HEADSET))
        assertEquals(AudioRoute.WIRED_HEADSET, withHeadset.defaultEarRoute)

        val withoutHeadset = CallAudioModel(supportedRoutes = setOf(AudioRoute.EARPIECE))
        assertEquals(AudioRoute.EARPIECE, withoutHeadset.defaultEarRoute)
    }

    @Test
    fun `speaker and bluetooth flags follow the current route`() {
        assertTrue(CallAudioModel(route = AudioRoute.SPEAKER).isSpeakerOn)
        assertFalse(CallAudioModel(route = AudioRoute.SPEAKER).isBluetoothOn)
        assertTrue(CallAudioModel(route = AudioRoute.BLUETOOTH).isBluetoothOn)
        assertFalse(CallAudioModel.DEFAULT.isSpeakerOn)
    }

    @Test
    fun `telecomRoute round-trips through routeFromMask`() {
        AudioRoute.entries.forEach { route ->
            assertEquals(route, CallAudioModel.routeFromMask(route.telecomRoute))
        }
    }
}
