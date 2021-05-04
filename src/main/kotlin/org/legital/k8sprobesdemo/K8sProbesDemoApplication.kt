package org.legital.k8sprobesdemo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.web.servlet.config.annotation.EnableWebMvc

@EnableWebMvc
@EnableAsync
@SpringBootApplication
class K8sProbesDemoApplication

fun main(args: Array<String>) {
	runApplication<K8sProbesDemoApplication>(*args)
}
