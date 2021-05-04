package org.legital.k8sprobesdemo

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api")
class ApplicationStateController(
    val applicationStateService: ApplicationStateService
) {

    @GetMapping("/readiness/off")
    fun disableReadinessProbe(
        @RequestParam(required = false) seconds: Optional<Int>
    ) {
        applicationStateService.disableReadiness(seconds.orElse(Int.MAX_VALUE))
    }

    @GetMapping("/liveness/off")
    fun disableLivenessProbe(
        @RequestParam( required = false) seconds: Optional<Int>
    ) {
        applicationStateService.disableLiveness(seconds.orElse(Int.MAX_VALUE))
    }
}
