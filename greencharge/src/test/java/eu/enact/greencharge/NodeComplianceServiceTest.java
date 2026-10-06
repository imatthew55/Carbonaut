package eu.enact.greencharge;

import com.informationcatalyst.enact.application_controller.policymodel.Reconciler.DTO.AdaptationRecommendation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeComplianceServiceTest {

    private final NodeComplianceService service = new NodeComplianceService();

    private static String action(AdaptationRecommendation rec) {
        return rec.getCpu().getAction().toLowerCase().replace('-', '_');
    }

    @Test
    void wellProvisionedNodeIsCompliant() {
        AdaptationRecommendation rec = service.evaluate(
                NodeComplianceService.metrics("enact-dev-worker", 2, 2, 20));
        assertTrue(rec.isCompliant(), rec.toString());
        assertEquals("no_action", action(rec), rec.toString());
    }

    @Test
    void underProvisionedNodeNeedsScaleUp() {
        AdaptationRecommendation rec = service.evaluate(
                NodeComplianceService.metrics("enact-dev-worker2", 0.5, 2, 20));
        assertFalse(rec.isCompliant(), rec.toString());
        assertEquals("scale_up", action(rec), rec.toString());
    }
}
