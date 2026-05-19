package com.worxbend.zephyr

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform