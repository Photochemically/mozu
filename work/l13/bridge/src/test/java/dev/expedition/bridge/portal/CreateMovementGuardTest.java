package dev.expedition.bridge.portal;

import com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Decision mapping only; the Create runtime check itself is pinned by CreateApiContractTest. */
class CreateMovementGuardTest {
    @Test void protectedPositionsFailAndOrdinaryPositionsPassWithoutInterfering() {
        assertEquals(CheckResult.FAIL, CreateMovementGuard.decide(true));
        assertEquals(CheckResult.PASS, CreateMovementGuard.decide(false));
        assertFalse(CheckResult.FAIL.toBoolean());
        assertTrue(CheckResult.SUCCESS.toBoolean());
    }
}
