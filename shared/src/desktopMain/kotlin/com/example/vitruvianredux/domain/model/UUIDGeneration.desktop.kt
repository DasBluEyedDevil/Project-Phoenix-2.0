package com.example.vitruvianredux.domain.model

import java.util.UUID

actual fun generateUUID(): String = UUID.randomUUID().toString()
