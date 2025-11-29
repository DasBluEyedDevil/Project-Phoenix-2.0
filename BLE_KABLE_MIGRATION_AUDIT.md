# BLE/GATT Protocol Parity Audit: Kable Migration

**Audit Date:** 2025-11-29
**Auditor:** Claude (Automated Protocol Audit)
**Repository:** Project-Phoenix-2.0 (Kable-based KMP)
**Reference:** VitruvianProjectPhoenix (Nordic BLE / Android-specific)

---

## Executive Summary

This audit compares the Kable-based Kotlin Multiplatform BLE implementation against the parent Nordic BLE implementation. The migration is **largely complete** with good protocol parity, but several **CRITICAL** and **HIGH** severity gaps were identified that could cause workout failures or data issues.

### Quick Stats
- **UUIDs:** MATCH (Core NUS UUIDs identical)
- **Core Protocol:** PARTIAL (Missing 96-byte activation command)
- **Data Parsing:** MATCH (Byte ordering and offsets correct)
- **State Machine:** MATCH (Handle detection states equivalent)
- **Connection Handling:** IMPROVED (Better auto-reconnect in Kable)

---

## Phase 1: BLE Service & Characteristic Mapping

### 1.1 UUID Comparison

| Characteristic | Parent (Nordic) | Kable Build | Status |
|---------------|-----------------|-------------|--------|
| NUS Service | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | **MATCH** |
| NUS TX (Write) | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | **MATCH** |
| NUS RX (Notify) | `6e400003-b5a3-f393-e0a9-e50e24dcca9e` | `6e400003-b5a3-f393-e0a9-e50e24dcca9e` | **MATCH** |
| Monitor/Sample | `90e991a6-c548-44ed-969b-eb541014eae3` | `90e991a6-c548-44ed-969b-eb541014eae3` | **MATCH** |
| Reps Notify | `8308f2a6-0875-4a94-a86f-5c5c5e1b068a` | NOT USED DIRECTLY | **GAP** |
| Diagnostic | `5fa538ec-d041-42f6-bbd6-c30d475387b7` | DEFINED BUT NOT USED | **GAP** |
| Heuristic | `c7b73007-b245-4503-a1ed-9e4e97eb9802` | DEFINED BUT NOT USED | **GAP** |
| Version | `74e994ac-0e80-4c02-9cd0-76cb31d3959b` | DEFINED BUT NOT USED | **GAP** |
| Mode | `67d0dae0-5bfc-4ea2-acc9-ac784dee7f29` | DEFINED BUT NOT USED | **GAP** |
| Cable Left | `bc4344e9-8d63-4c89-8263-951e2d74f744` | DEFINED BUT NOT USED | **GAP** |
| Cable Right | `92ef83d6-8916-4921-8172-a9919bc82566` | DEFINED BUT NOT USED | **GAP** |

### 1.2 GATT Operation Comparison

| Operation | Parent | Kable Build | Status |
|-----------|--------|-------------|--------|
| Write Type | `WriteType.WithoutResponse` | `WriteType.WithoutResponse` | **MATCH** |
| MTU Request | 247 bytes (explicit) | 512 bytes (requested, auto-negotiated) | **ACCEPTABLE** |
| Notification Setup | Explicit descriptor write | Kable `observe()` auto-handles | **MATCH** |
| Connection Priority | HIGH | Not explicitly set | **MEDIUM GAP** |

### 1.3 Missing Characteristics (Severity Assessment)

| Characteristic | Purpose | Severity | Impact |
|---------------|---------|----------|--------|
| Reps Notify (`8308f2a6`) | Dedicated rep notifications | **LOW** | RX notifications cover this |
| Diagnostic | Fault codes, temperature | **MEDIUM** | No device health monitoring |
| Heuristic | Phase statistics (Echo mode) | **MEDIUM** | Missing Echo mode analytics |
| Version | Firmware version | **LOW** | Informational only |
| Cable Left/Right | Per-cable telemetry | **LOW** | Monitor char provides this |

---

## Phase 2: Connection Lifecycle

### 2.1 Scanning Comparison

| Parameter | Parent | Kable Build | Status |
|-----------|--------|-------------|--------|
| Device Name Filter - Vee_ | YES | YES | **MATCH** |
| Device Name Filter - VIT | YES | YES | **MATCH** |
| Device Name Filter - V-Trainer | YES | NO | **GAP** |
| Device Name Filter - Vitruvian | YES | NO | **GAP** |
| Scan Timeout | 30000ms | Not implemented | **MEDIUM GAP** |
| Duplicate Handling | By identifier | By identifier | **MATCH** |

**Finding:** The Kable build only filters for `Vee_` and `VIT` prefixes, while the parent also accepts `V-Trainer` and `Vitruvian` patterns. This could prevent discovery of some device variants.

### 2.2 Connection Establishment

| Parameter | Parent | Kable Build | Status |
|-----------|--------|-------------|--------|
| Retry Count | 3 | 3 | **MATCH** |
| Retry Delay | 100ms | 100ms | **MATCH** |
| Connection Timeout | 15000ms | Not explicit (Kable default) | **MEDIUM GAP** |
| Auto-connect | No | No | **MATCH** |
| PHY Preference | Not specified | Not specified | **MATCH** |
| Connection Priority | HIGH | Not set | **MEDIUM GAP** |

### 2.3 Connection Maintenance

| Feature | Parent | Kable Build | Status |
|---------|--------|-------------|--------|
| Heartbeat Interval | 2000ms | 2000ms | **MATCH** |
| Heartbeat Read Timeout | Not specified | 1500ms | **IMPROVED** |
| Heartbeat Fallback | Write no-op | Read-first, write fallback | **IMPROVED** |
| No-op Command Size | 4 bytes | 4 bytes | **MATCH** |
| Monitor Polling | 100ms | 100ms | **MATCH** |

### 2.4 Disconnection Handling

| Feature | Parent | Kable Build | Status |
|---------|--------|-------------|--------|
| Explicit Disconnect Flag | YES | YES | **MATCH** |
| Auto-reconnect on Drop | YES | YES | **MATCH** |
| Resource Cleanup | Manual | Automatic via Kable | **IMPROVED** |
| State Reset | YES | YES | **MATCH** |

---

## Phase 3: Protocol Packet Implementation

### 3.1 Command Comparison

| Command | Parent | Kable Build | Status | Severity |
|---------|--------|-------------|--------|----------|
| STOP (0x50) | 1-2 bytes | 1 byte | **MATCH** | - |
| RESET/INIT (0x0A) | 4 bytes | NOT IMPLEMENTED | **CRITICAL GAP** | **CRITICAL** |
| REGULAR (0x4F) | 25 bytes | 25 bytes | **MATCH** | - |
| ECHO (0x4E) | 29-32 bytes | 29 bytes (stub) | **HIGH GAP** | **HIGH** |
| ACTIVATION (0x04) | 96-97 bytes | NOT IMPLEMENTED | **CRITICAL GAP** | **CRITICAL** |
| INIT PRESET (0x11) | 34 bytes | NOT IMPLEMENTED | **HIGH GAP** | **HIGH** |
| COLOR SCHEME | 34 bytes | NOT IMPLEMENTED | **LOW GAP** | **LOW** |

### 3.2 CRITICAL: Missing 96-byte Activation Command (0x04)

**Location:** Parent `ProtocolBuilder.kt` defines full structure
**Impact:** Cannot use official app protocol for workout initialization

**Parent Structure (96 bytes):**
```
Offset  Size  Type     Description
0x00    4     u32 LE   Command ID = 0x04
0x04    1     u8       Reps (0xFF for unlimited)
0x05    1     u8       Reserved (0x03)
0x06    1     u8       Reserved (0x03)
0x08    4     f32 LE   Constant (5.0)
0x0C    4     f32 LE   Constant (5.0)
...
0x30    32    u8[32]   Mode profile block (8 fields per mode)
0x54    4     f32 LE   Effective weight (kg + 10)
0x58    4     f32 LE   Total weight (kg)
0x5C    4     f32 LE   Progression/regression (kg per rep)
```

**Kable Build:** Uses simplified 25-byte 0x4F command only

### 3.3 CRITICAL: Missing INIT/RESET Command (0x0A)

**Parent Usage:** Recovery from faults, device initialization
**Impact:** Cannot recover from machine faults without disconnect/reconnect

**Parent Structure (4 bytes):**
```
0x0A 0x00 0x00 0x00
```

**Kable Build:** Not implemented - `ProtocolTester.kt` references but no factory method

### 3.4 HIGH: Echo Command (0x4E) Incomplete

**Parent Structure (32 bytes):**
```
Offset  Size  Type     Description
0x00    4     u32 LE   Command ID = 0x4E
0x04    1     u8       Warmup reps
0x05    1     u8       Target reps (0xFF for unlimited)
0x06    2     u16 LE   Reserved
0x08    2     u16 LE   Eccentric percentage
0x0A    2     u16 LE   Concentric percentage
0x0C    4     f32 LE   Smoothing parameter
0x10    4     f32 LE   Gain
0x14    4     f32 LE   Cap
0x18    4     f32 LE   Floor
0x1C    4     f32 LE   Negative limit
```

**Kable Build (`BlePacketFactory.kt:53-65`):**
```kotlin
fun createEchoCommand(level: Int, eccentricLoad: Int): ByteArray {
    val buffer = ByteArray(29)
    buffer[0] = BleConstants.Commands.ECHO_COMMAND
    buffer[1] = level.toByte()
    // TODO: Verify Echo packet structure
    return buffer
}
```

**Issues:**
1. Missing eccentric/concentric percentage values
2. Missing smoothing, gain, cap, floor, negative limit parameters
3. Marked as TODO - incomplete implementation

### 3.5 Regular Command (0x4F) - VERIFIED MATCH

**Parent (`ProtocolBuilder.kt`):**
```
[0]: Command (0x4F)
[1]: Mode (0=OldSchool, 2=Pump, 3=TUT, 4=TUTBeast, 6=EccentricOnly)
[2-3]: Weight per cable (UInt16, scaled by 100, LITTLE-ENDIAN)
[4]: Target Reps
[5-24]: Padding
```

**Kable Build (`BlePacketFactory.kt:20-43`):**
```kotlin
val buffer = ByteArray(25)
buffer[0] = BleConstants.Commands.REGULAR_COMMAND  // 0x4F
buffer[1] = workoutType.mode.modeValue.toByte()    // Mode byte
val weightScaled = (weightPerCableKg * 100).toInt()
buffer[2] = (weightScaled and 0xFF).toByte()       // Weight LSB
buffer[3] = ((weightScaled shr 8) and 0xFF).toByte() // Weight MSB
buffer[4] = targetReps.toByte()                     // Reps
```

**Status:** **BYTE-FOR-BYTE MATCH** - Weight scaling, byte order, and structure identical

### 3.6 Stop Command (0x50) - VERIFIED MATCH

Both implementations send single byte `0x50` for stop command.

---

## Phase 4: Data Flow & State Management

### 4.1 Monitor Data Parsing

**Byte Layout Comparison:**

| Field | Parent Offset | Kable Offset | Endian | Status |
|-------|--------------|--------------|--------|--------|
| Ticks Low (f0) | 0-1 | 0-1 | LE | **MATCH** |
| Ticks High (f1) | 2-3 | 2-3 | LE | **MATCH** |
| Position A | 4-5 | 4-5 | LE | **MATCH** |
| Load A (*100) | 8-9 | 8-9 | LE | **MATCH** |
| Position B | 10-11 | 10-11 | LE | **MATCH** |
| Load B (*100) | 14-15 | 14-15 | LE | **MATCH** |
| Status Flags | 16-17 | 16-17 | LE | **MATCH** |

**Kable Implementation (`KableBleRepository.kt:664-781`):** Correctly implements:
- 32-bit tick reconstruction from two 16-bit values
- Spike filtering (>50000 treated as BLE error)
- Load scaling (divide by 100 for kg)
- Velocity calculation from delta position/time

### 4.2 Rep Notification Parsing

**24-byte Official Format:**

| Field | Parent Offset | Kable Offset | Type | Status |
|-------|--------------|--------------|------|--------|
| Up Counter | 0-3 | 1-4 (after opcode) | i32 LE | **MATCH** |
| Down Counter | 4-7 | 5-8 | i32 LE | **MATCH** |
| Range Top | 8-11 | 9-12 | f32 LE | **MATCH** |
| Range Bottom | 12-15 | 13-16 | f32 LE | **MATCH** |
| Reps ROM Count | 16-17 | 17-18 | u16 LE | **MATCH** |
| Reps Set Count | 20-21 | 21-22 | u16 LE | **MATCH** |

**Note:** Kable accounts for opcode byte at position 0, offsets adjusted accordingly.

**Legacy 6-byte Format:** Both implementations support backwards compatibility.

### 4.3 Status Flag Processing

| Flag | Bit | Parent | Kable | Status |
|------|-----|--------|-------|--------|
| REP_TOP_READY | 0 | YES | YES | **MATCH** |
| REP_BOTTOM_READY | 1 | YES | YES | **MATCH** |
| ROM_OUTSIDE_HIGH | 2 | YES | YES | **MATCH** |
| ROM_OUTSIDE_LOW | 3 | YES | YES | **MATCH** |
| ROM_UNLOAD_ACTIVE | 4 | YES | YES | **MATCH** |
| SPOTTER_ACTIVE | 5 | YES | YES | **MATCH** |
| DELOAD_WARN | 6 | YES | YES | **MATCH** |
| DELOAD_OCCURRED | 15 | YES | YES | **MATCH** |

**Kable Implementation (`SampleStatus.kt`):** Complete bit mask implementation matching parent.

### 4.4 Handle Activity State Machine

| State | Parent | Kable | Status |
|-------|--------|-------|--------|
| WaitingForRest | YES | YES | **MATCH** |
| Released/SetComplete | YES | SetComplete | **MATCH** (renamed) |
| Grabbed | YES | Active | **MATCH** (renamed) |
| Active/Moving | YES | Active | **MATCH** (combined) |

**Thresholds:**

| Threshold | Parent | Kable | Status |
|-----------|--------|-------|--------|
| Grab Position | 8.0 | 8.0 | **MATCH** |
| Rest Position | 5.0 | 5.0 | **MATCH** |
| Velocity | 100.0 | 100.0 | **MATCH** |
| Spike Filter | 50000 | 50000 | **MATCH** |

### 4.5 Deload Event Handling

| Feature | Parent | Kable | Status |
|---------|--------|-------|--------|
| Debounce Time | 2000ms | 2000ms | **MATCH** |
| Event Flow | SharedFlow | SharedFlow | **MATCH** |
| Buffer Capacity | Not specified | 8 | **IMPROVED** |

---

## Phase 5: Platform-Specific Considerations

### 5.1 Kable-Specific Adaptations

| Feature | Implementation | Status |
|---------|----------------|--------|
| MTU Negotiation | Auto-handled by Kable 0.40.0 | **CORRECT** |
| `peripheral.write()` | Uses `WriteType.WithoutResponse` | **CORRECT** |
| Notification Subscription | `observe()` pattern | **CORRECT** |
| Coroutine Scope | `SupervisorJob + Dispatchers.Default` | **CORRECT** |
| State Flow mapping | Kable State → ConnectionState | **CORRECT** |

### 5.2 Missing Platform Features

| Feature | Parent | Kable | Severity |
|---------|--------|-------|----------|
| Connection Priority HIGH | YES | NO | **MEDIUM** |
| Explicit MTU request | 247 bytes | 512 (auto) | **LOW** |
| Service Invalidation Handler | YES | Partial | **MEDIUM** |
| Diagnostic Polling (500ms) | YES | NO | **MEDIUM** |
| Heuristic Polling (250ms) | YES | NO | **MEDIUM** |

### 5.3 iOS Considerations

| Feature | Status | Notes |
|---------|--------|-------|
| CoreBluetooth State Restoration | NOT IMPLEMENTED | Kable may handle automatically |
| Background Mode | NOT IMPLEMENTED | Requires iOS-specific config |
| iOS Timing Constraints | UNKNOWN | Needs testing |

---

## Gap Analysis Summary

### CRITICAL Gaps (Must Fix)

| ID | Gap | Impact | Files Affected |
|----|-----|--------|----------------|
| C1 | Missing 0x04 Activation Command (96 bytes) | Cannot use official app protocol | `BlePacketFactory.kt` |
| C2 | Missing 0x0A INIT/RESET Command | Cannot recover from machine faults | `BlePacketFactory.kt` |

### HIGH Gaps (Should Fix)

| ID | Gap | Impact | Files Affected |
|----|-----|--------|----------------|
| H1 | Echo Command (0x4E) incomplete | Echo mode parameters missing | `BlePacketFactory.kt:53-65` |
| H2 | Missing 0x11 INIT Preset Frame | Legacy web app compatibility | `BlePacketFactory.kt` |
| H3 | Diagnostic/Heuristic polling missing | No device health or Echo analytics | `KableBleRepository.kt` |

### MEDIUM Gaps (Consider Fixing)

| ID | Gap | Impact | Files Affected |
|----|-----|--------|----------------|
| M1 | Scan filter missing V-Trainer/Vitruvian | May miss device variants | `KableBleRepository.kt:191-193` |
| M2 | No explicit connection priority | Potential stability on Android | `KableBleRepository.kt` |
| M3 | Scan timeout not implemented | Battery drain if scan forgotten | `KableBleRepository.kt` |
| M4 | Connection timeout not explicit | May hang on slow connections | `KableBleRepository.kt` |

### LOW Gaps (Nice to Have)

| ID | Gap | Impact | Files Affected |
|----|-----|--------|----------------|
| L1 | Color scheme command missing | No LED customization | `BlePacketFactory.kt` |
| L2 | Version characteristic not read | No firmware version display | `KableBleRepository.kt` |

---

## Implementation Recommendations

### Priority 1: Critical Command Implementation

**File:** `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/BlePacketFactory.kt`

Add the following methods:

```kotlin
/**
 * Creates INIT/RESET command (0x0A) - 4 bytes
 * Used for device initialization and fault recovery
 */
fun createInitCommand(): ByteArray {
    return byteArrayOf(0x0A, 0x00, 0x00, 0x00)
}

/**
 * Creates 96-byte Activation command (0x04)
 * Full official app protocol for workout initialization
 */
fun createActivationCommand(
    mode: ProgramMode,
    weightKg: Float,
    targetReps: Int,
    progressionKg: Float = 0f
): ByteArray {
    val buffer = ByteArray(96)

    // Command ID (bytes 0-3, LE)
    buffer[0] = 0x04
    buffer[1] = 0x00
    buffer[2] = 0x00
    buffer[3] = 0x00

    // Reps (byte 4): 0xFF for unlimited
    buffer[4] = if (targetReps == 0) 0xFF.toByte() else targetReps.toByte()

    // Reserved (bytes 5-6)
    buffer[5] = 0x03
    buffer[6] = 0x03

    // Constants (5.0f at offsets 8, 12, 28)
    putFloatLE(buffer, 8, 5.0f)
    putFloatLE(buffer, 12, 5.0f)
    putFloatLE(buffer, 28, 5.0f)

    // Mode profile block at offset 48 (0x30)
    // ... mode-specific values ...

    // Effective weight at offset 84 (0x54)
    putFloatLE(buffer, 84, weightKg + 10f)

    // Total weight at offset 88 (0x58)
    putFloatLE(buffer, 88, weightKg)

    // Progression at offset 92 (0x5C)
    putFloatLE(buffer, 92, progressionKg)

    return buffer
}

private fun putFloatLE(buffer: ByteArray, offset: Int, value: Float) {
    val bits = value.toBits()
    buffer[offset] = (bits and 0xFF).toByte()
    buffer[offset + 1] = ((bits shr 8) and 0xFF).toByte()
    buffer[offset + 2] = ((bits shr 16) and 0xFF).toByte()
    buffer[offset + 3] = ((bits shr 24) and 0xFF).toByte()
}
```

### Priority 2: Complete Echo Command

**File:** `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/BlePacketFactory.kt`

```kotlin
/**
 * Creates complete Echo command (0x4E) - 32 bytes
 */
fun createEchoCommand(
    warmupReps: Int,
    targetReps: Int,
    eccentricPercent: Int,
    concentricPercent: Int,
    smoothing: Float = 0.5f,
    gain: Float = 1.0f,
    cap: Float = 150f,
    floor: Float = 0f,
    negativeLimit: Float = -50f
): ByteArray {
    val buffer = ByteArray(32)

    // Command ID (bytes 0-3, LE)
    buffer[0] = 0x4E
    buffer[1] = 0x00
    buffer[2] = 0x00
    buffer[3] = 0x00

    // Warmup reps (byte 4)
    buffer[4] = warmupReps.toByte()

    // Target reps (byte 5): 0xFF for unlimited
    buffer[5] = if (targetReps == 0) 0xFF.toByte() else targetReps.toByte()

    // Reserved (bytes 6-7)
    buffer[6] = 0x00
    buffer[7] = 0x00

    // Eccentric percentage (bytes 8-9, LE)
    buffer[8] = (eccentricPercent and 0xFF).toByte()
    buffer[9] = ((eccentricPercent shr 8) and 0xFF).toByte()

    // Concentric percentage (bytes 10-11, LE)
    buffer[10] = (concentricPercent and 0xFF).toByte()
    buffer[11] = ((concentricPercent shr 8) and 0xFF).toByte()

    // Float parameters
    putFloatLE(buffer, 12, smoothing)
    putFloatLE(buffer, 16, gain)
    putFloatLE(buffer, 20, cap)
    putFloatLE(buffer, 24, floor)
    putFloatLE(buffer, 28, negativeLimit)

    return buffer
}
```

### Priority 3: Scan Filter Update

**File:** `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt`

Update line 191-193:
```kotlin
.filter { advertisement ->
    val name = advertisement.name ?: return@filter false
    name.startsWith("Vee_") ||
    name.startsWith("VIT") ||
    name.startsWith("V-Trainer") ||
    name.matches(Regex("^Vitruvian.*$"))
}
```

---

## Test Cases for Verification

### Protocol Verification Tests

1. **0x4F Command Byte Dump Test**
   - Send command with weight=50kg, mode=OldSchool, reps=10
   - Verify bytes: `4F 00 88 13 0A 00 00 ...` (50*100=5000=0x1388, LE=88 13)

2. **0x04 Activation Command Test**
   - Send full 96-byte frame
   - Verify machine enters configured mode

3. **0x0A Reset Recovery Test**
   - Induce fault state
   - Send 0x0A command
   - Verify fault clears without reconnect

4. **Rep Notification Format Test**
   - Trigger rep completion
   - Verify 24-byte packet parsing
   - Verify legacy 6-byte fallback

5. **Deload Event Test**
   - Trigger deload condition
   - Verify 0x8000 flag detection
   - Verify 2000ms debounce

---

## Risk Assessment

| Change | Risk Level | Mitigation |
|--------|------------|------------|
| Add 0x04 command | **MEDIUM** | Test on single device first, verify no side effects |
| Add 0x0A command | **LOW** | Simple 4-byte command, well-documented |
| Complete 0x4E | **MEDIUM** | Echo mode parameters need firmware verification |
| Scan filter update | **LOW** | Additive change, won't break existing |
| Polling additions | **LOW** | Independent from core workout flow |

---

## Conclusion

The Kable migration successfully implements core BLE communication with accurate data parsing and state management. However, **two critical gaps** (0x04 activation and 0x0A reset commands) should be addressed before production release to ensure:

1. Full official app protocol compatibility
2. Machine fault recovery without reconnection
3. Complete Echo mode functionality

The recommended implementation order is:
1. Add 0x0A INIT command (simple, enables fault recovery)
2. Complete 0x4E Echo command (enables full Echo mode)
3. Add 0x04 Activation command (enables official protocol)
4. Update scan filters (ensures device discovery)
5. Add diagnostic/heuristic polling (nice-to-have)

---

*Generated by automated protocol audit - manual review recommended for production deployment.*
