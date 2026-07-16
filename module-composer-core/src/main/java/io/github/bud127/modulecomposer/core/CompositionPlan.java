package io.github.bud127.modulecomposer.core;

import java.util.List;

public record CompositionPlan(
        String framework,
        ExecutionMode executionMode,
        SelectionMode selectionMode,
        List<ModuleRegistration> modules,
        RuntimeOptions runtimeOptions,
        DistributionDetails distributionDetails
) {
    public boolean isStandalone() {
        return executionMode == ExecutionMode.STANDALONE;
    }

    public List<String> moduleNames() {
        return modules.stream().map(ModuleRegistration::name).toList();
    }

    public String distribution() {
        return distributionDetails.distribution();
    }

    public String applicationName() {
        return distributionDetails.applicationName();
    }

    public DistributionArtifact artifact() {
        return distributionDetails.artifact();
    }

    public DistributionContainer container() {
        return distributionDetails.container();
    }
}
