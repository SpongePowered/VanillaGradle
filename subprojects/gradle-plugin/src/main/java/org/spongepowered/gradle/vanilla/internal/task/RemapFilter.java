package org.spongepowered.gradle.vanilla.internal.task;

import org.apache.tools.ant.filters.BaseFilterReader;
import org.apache.tools.ant.util.ReaderInputStream;
import org.cadixdev.bombe.jar.JarClassEntry;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class RemapFilter extends BaseFilterReader {
    private @MonotonicNonNull JarEntryTransformerProvider remapper;
    private @MonotonicNonNull Reader delegate;

    public RemapFilter(Reader in) {
        super(in);
    }

    public JarEntryTransformerProvider getRemapper() {
        return remapper;
    }

    public void setRemapper(JarEntryTransformerProvider remapper) {
        this.remapper = remapper;
    }

    public static CopySpec createCopySpec(Project project, JarEntryTransformerProvider remapper) {
        return project.copySpec(spec -> {
            spec.include("**/*.class");
            spec.setFilteringCharset("ISO-8859-1");
            spec.filter(Collections.singletonMap("remapper", remapper), RemapFilter.class);
        });
    }

    private void initialize() throws IOException {
        InputStream in = new ReaderInputStream(this.in, StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            baos.write(buffer, 0, n);
        }
        byte[] bytes = baos.toByteArray();

        JarClassEntry transformed = remapper.getJarEntryTransformer().transform(new JarClassEntry("Foo.class", 0, bytes));
        if (transformed == null) {
            delegate = this.in;
        } else {
            delegate = new InputStreamReader(new ByteArrayInputStream(transformed.getContents()), StandardCharsets.ISO_8859_1);
        }
    }

    @Override
    public int read() throws IOException {
        if (!getInitialized()) {
            initialize();
            setInitialized(true);
        }
        return delegate.read();
    }
}
