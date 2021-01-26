package org.spongepowered.vanilla.gradle.task;

import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.bundling.Zip;
import org.spongepowered.vanilla.gradle.Constants;

import javax.inject.Inject;

/**
 * Filter forbidden prefixes out of a class.
 */
public abstract class FilterJarTask extends Zip {

    @Input
    public abstract SetProperty<String> getAllowedPackages();

    @Inject
    protected abstract ArchiveOperations getArchiveOps();

    private String[] allowedPackages;

    public FilterJarTask() {
        this.setGroup(Constants.TASK_GROUP);
        this.getArchiveExtension().set("jar");
        this.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
        this.include(element -> {
            if (this.allowedPackages == null) {
                this.allowedPackages = this.getAllowedPackages().get().toArray(new String[0]);
            }
            if (element.isDirectory()) {
                return false;
            }

            if (!element.getPath().contains("/")) {
                return true;
            }

            if (!element.getName().endsWith(".class")) {
                return true;
            }

            for (final String pkg : this.allowedPackages) {
                if (element.getPath().startsWith(pkg)) {
                    return true;
                }
            }
            return false;
        });
    }

    public void fromJar(final Object jar) {
        this.from(this.getArchiveOps().zipTree(jar));
    }

}
