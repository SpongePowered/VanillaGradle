package org.spongepowered.gradle.vanilla.internal;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Resolves the names of the Minecraft libraries, for use with the Shadow plugin.
 */
public class ResolveMinecraftLibNames implements Callable<Set<String>> {
    private final NamedDomainObjectProvider<Configuration> minecraftConfig;
    private Set<String> result;

    public ResolveMinecraftLibNames(NamedDomainObjectProvider<Configuration> minecraftConfig) {
        this.minecraftConfig = minecraftConfig;
    }

    @Override
    public Set<String> call() {
        if (this.result == null) {
            final ResolvedConfiguration conf = minecraftConfig.get().getResolvedConfiguration();
            conf.rethrowFailure();
            this.result = new HashSet<>();
            final Queue<ResolvedDependency> deps = new ArrayDeque<>(conf.getFirstLevelModuleDependencies());
            ResolvedDependency pointer;
            while ((pointer = deps.poll()) != null) {
                if (this.result.add(pointer.getName())) {
                    deps.addAll(pointer.getChildren());
                }
            }
        }
        return result;
    }
}
