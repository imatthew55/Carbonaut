package eu.enact.greencharge;

import com.informationcatalyst.enact.application_controller.policymodel.Reconciler.DTO.AdaptationRecommendation;
import com.informationcatalyst.enact.application_controller.policymodel.Reconciler.DTO.ResourceMetrics;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/compliance")
public class NodeComplianceController {

    private final NodeComplianceService service;

    public NodeComplianceController(NodeComplianceService service) {
        this.service = service;
    }

    /** e.g. /compliance/check?node=enact-dev-worker&cores=0.5&memGi=2&latencyMs=20 */
    @GetMapping("/check")
    public AdaptationRecommendation check(@RequestParam(defaultValue = "enact-dev-worker") String node,
                                          @RequestParam(defaultValue = "2") double cores,
                                          @RequestParam(defaultValue = "2") double memGi,
                                          @RequestParam(defaultValue = "20") long latencyMs) {
        return service.evaluate(NodeComplianceService.metrics(node, cores, memGi, latencyMs));
    }

    @PostMapping
    public AdaptationRecommendation check(@RequestBody ResourceMetrics metrics) {
        return service.evaluate(metrics);
    }
}
