package com.example.vitruvianredux.domain.model

import platform.Foundation.NSUUID

actual fun generateUUID(): String = NSUUID().UUIDString()
