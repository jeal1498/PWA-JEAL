package com.jeal.switchdriver

import org.junit.Assert.*
import org.junit.Test

class ValidationTest {
    @Test fun validPackages() {
        assertTrue(Validation.isPackageName("com.ubercab.driver"))
        assertTrue(Validation.isPackageName("com.didiglobal.driver"))
        assertTrue(Validation.isPackageName("a.b_c.D9"))
    }

    @Test fun rejectsInjectionAndGarbage() {
        listOf("", "uber", "com.x; rm -rf /", "com.x && id", "com.x\nid", "com.x $(id)", "../com.x", "com..x", ".com.x", "com.x ", "com.x|id", "com.x`id`")
            .forEach { assertFalse("debe rechazar «$it»", Validation.isPackageName(it)) }
        assertFalse(Validation.isPackageName(null))
        assertFalse(Validation.isPackageName("a." + "b".repeat(250)))
    }

    private val dump = """
        Packages:
          Package [com.x] (abc):
            userId=10100
            User 0: ceDataInode=1 installed=true hidden=false suspended=false distractionFlags=0 stopped=true notLaunched=false enabled=0
            User 10: ceDataInode=1 installed=true hidden=false suspended=false stopped=false notLaunched=false enabled=0
    """.trimIndent()

    @Test fun parsesStoppedPerUser() {
        assertEquals(1, Validation.parseStopped(dump, 0))
        assertEquals(0, Validation.parseStopped(dump, 10))
        assertEquals(-1, Validation.parseStopped(dump, 5))
        assertEquals(-1, Validation.parseStopped("", 0))
        // "User 1:" no debe confundirse con "User 10:"
        assertEquals(-1, Validation.parseStopped(dump, 1))
    }

    @Test fun planSkipsBlockedAndKeepsOthers() {
        val reasons = mapOf("com.maps" to "Protegida", "com.sys" to "App del sistema")
        val p = Planner.build(listOf("com.uber", "com.maps", "com.didi", "com.sys", "com.uber")) { reasons[it] }
        assertEquals(listOf("com.uber", "com.didi"), p.targets)
        assertEquals(listOf("com.maps" to "Protegida", "com.sys" to "App del sistema"), p.skipped)
    }

    @Test fun planNeverIncludesProtected() {
        val prot = setOf("a.a", "b.b")
        val p = Planner.build(listOf("a.a", "b.b", "c.c")) { if (it in prot) "Protegida" else null }
        assertTrue(p.targets.none { it in prot })
        assertEquals(1, p.targets.size)
    }
}
