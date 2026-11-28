package dev.expedition.bridge.portal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure rewrite decision; the DD signatures it protects are pinned by DimDoorsRiftContractTest. */
class ManagedRiftGuardTest {
    @Test void bridgeOwnedTargetsMayAlwaysReplaceAnything() {
        // System installation paths set managed targets onto ordinary or protected rifts.
        assertTrue(ManagedRiftGuard.allowsRewrite(false, false, true));
        assertTrue(ManagedRiftGuard.allowsRewrite(false, true, true));
        assertTrue(ManagedRiftGuard.allowsRewrite(true, true, true));
    }
    @Test void ordinaryUnprotectedRiftsStayConfigurable() {
        assertTrue(ManagedRiftGuard.allowsRewrite(false, false, false));
    }
    @Test void managedOrProtectedRiftsRefuseForeignTargets() {
        assertFalse(ManagedRiftGuard.allowsRewrite(true, false, false));
        assertFalse(ManagedRiftGuard.allowsRewrite(true, true, false));
        assertFalse(ManagedRiftGuard.allowsRewrite(false, true, false));
    }
}
