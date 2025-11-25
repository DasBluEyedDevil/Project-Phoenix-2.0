package com.example.vitruvianredux

/**
 * Greeting class to verify multiplatform setup.
 */
class Greeting {
    private val platform = getPlatform()
    
    fun greet(): String {
        return "Vitruvian Project Phoenix running on ${platform.name}!"
    }
}
