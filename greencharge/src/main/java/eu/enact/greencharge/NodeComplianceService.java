package eu.enact.greencharge;

import com.informationcatalyst.enact.application_controller.policymodel.Reconciler.DTO.AdaptationRecommendation;
import com.informationcatalyst.enact.application_controller.policymodel.Reconciler.DTO.ResourceMetrics;
import com.informationcatalyst.enact.application_controller.policymodel.Reconciler.config.PolicyModelConfig;
import com.informationcatalyst.enact.application_controller.policymodel.Reconciler.models.PolicyModel;
import com.informationcatalyst.enact.application_controller.policymodel.Reconciler.services.ComplianceAndAdaptationService;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/** Wraps the ENACT Application Controller: loads the policy model and evaluates node metrics against it. */
@Service
public class NodeComplianceService {

    private final ComplianceAndAdaptationService compliance = new ComplianceAndAdaptationService();

    public NodeComplianceService() {
        PolicyModelConfig config = new PolicyModelConfig();
        config.init(); // loads reconciliation/policymodel.yml
        PolicyModel policyModel = config.policyModel();

        Field field = ReflectionUtils.findField(ComplianceAndAdaptationService.class, "policyModel");
        ReflectionUtils.makeAccessible(field);
        ReflectionUtils.setField(field, compliance, policyModel);
    }

    public AdaptationRecommendation evaluate(ResourceMetrics metrics) {
        return compliance.checkAndAdapt(metrics);
    }

    public static ResourceMetrics metrics(String node, double cores, double memGi, long latencyMs) {
        ResourceMetrics m = new ResourceMetrics();
        m.setNodeName(node);

        ResourceMetrics.CpuMetrics cpu = new ResourceMetrics.CpuMetrics();
        cpu.setCores(cores);
        cpu.setUtilizationPercentage(40);
        m.setCpu(cpu);

        ResourceMetrics.MemoryMetrics mem = new ResourceMetrics.MemoryMetrics();
        mem.setCapacity(((long) memGi) + "Gi");
        mem.setBytes((long) (memGi * 1024 * 1024 * 1024));
        mem.setUtilizationPercentage(50);
        m.setMemory(mem);

        ResourceMetrics.NetworkMetrics net = new ResourceMetrics.NetworkMetrics();
        net.setBandwidthMbps(1000);
        net.setLatencyMs(latencyMs);
        net.setJitterMs(2);
        m.setNetwork(net);
        return m;
    }
}
