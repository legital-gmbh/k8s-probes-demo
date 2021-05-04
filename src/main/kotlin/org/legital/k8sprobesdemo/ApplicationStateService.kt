package org.legital.k8sprobesdemo

import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.LivenessState
import org.springframework.boot.availability.ReadinessState
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class ApplicationStateService(
    private val applicationContext: ApplicationContext
) {

    @Async
    fun disableReadiness(seconds: Int) {
        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC)
        Thread.sleep(seconds * 1000L)
        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC)
    }

    @Async
    fun disableLiveness(seconds: Int) {
        AvailabilityChangeEvent.publish(applicationContext, LivenessState.BROKEN)
        Thread.sleep(seconds * 1000L)
        AvailabilityChangeEvent.publish(applicationContext, LivenessState.CORRECT)
    }
}
