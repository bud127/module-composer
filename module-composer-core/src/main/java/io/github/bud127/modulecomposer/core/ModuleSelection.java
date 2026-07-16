package io.github.bud127.modulecomposer.core;

import java.util.List;

public record ModuleSelection(
        List<ModuleRegistration> modules,
        RuntimeOptions runtimeOptions,
        SelectionMode mode,
        DistributionDetails distributionDetails
) {
    public boolean isStandalone() {
        return modules.size() == 1;
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
