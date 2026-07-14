package io.github.bud127.modulecomposer.core;

public final class CompositionPlanner {

    public CompositionPlan plan(
            String framework,
            ModuleSelection selection
    ) {
        return new CompositionPlan(
                framework,
                selection.isStandalone()
                        ? ExecutionMode.STANDALONE
                        : ExecutionMode.GENERATED_HOST,
                selection.mode(),
                selection.modules(),
                selection.runtimeOptions(),
                selection.distribution()
        );
    }
}
