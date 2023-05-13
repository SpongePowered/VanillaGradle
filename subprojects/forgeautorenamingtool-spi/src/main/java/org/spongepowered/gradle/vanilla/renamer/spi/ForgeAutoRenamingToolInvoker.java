/*
 * This file is part of VanillaGradle, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.gradle.vanilla.renamer.spi;

import static java.util.Objects.requireNonNull;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.OptionSpecBuilder;
import net.minecraftforge.fart.api.Renamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.stream.Stream;

public final class ForgeAutoRenamingToolInvoker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeAutoRenamingToolInvoker.class);

    private ForgeAutoRenamingToolInvoker() {
    }

    public static void main(final String... args) {
        final Renamer renamer;
        try {
            renamer = ForgeAutoRenamingToolInvoker.createRenamer(args);
        } catch (TransformerProvisionException e) {
            LOGGER.error("Failed to provision the transformer '{}': {}", e.transformerId(), e.getMessage());
            if (e.getCause() != null) {
                LOGGER.error("Cause: ", e.getCause());
            }
            System.exit(2);
            return;
        }

        try {
            renamer.run();
        } catch (final Exception ex) {
            LOGGER.error("Failed to execute Renamer run with arguments {}", args, ex);
            System.exit(1);
        }
    }

    public static Renamer createRenamer(final String... args) throws TransformerProvisionException {
        return createRenamer(Arrays.asList(args));
    }

    public static Renamer createRenamer(final List<String> args) throws TransformerProvisionException {
        final ProviderHolder providers = ProviderHolder.INSTANCE.get();
        final OptionSet parsed;
        try {
            parsed = providers.parser.parse(expandArgs(new ArrayList<>(args)).toArray(new String[0]));
        } catch (final OptionException ex) {
            if (LOGGER.isWarnEnabled()) {
                final StringWriter helpHolder = new StringWriter();
                try {
                    providers.parser.printHelpOn(helpHolder);
                } catch (final IOException ex2) {
                    throw new IllegalStateException("Failed to write help", ex2);
                }
                LOGGER.warn(helpHolder.toString());
            }
            throw new TransformerProvisionException("Failed to parse renamer options due to errors in the options " + ex.options() + ": " + ex.getMessage());
        } catch (final IOException ex) {
            throw new TransformerProvisionException("Failed to read an argument file", ex);
        }

        final Renamer.Builder builder = Renamer.builder()
            .logger(LOGGER::info)
            .debug(LOGGER::debug)
            .input(parsed.valueOf(providers.inputO))
            .output(parsed.valueOf(providers.outputO))
            .threads(parsed.valueOf(providers.threadsO));

        for (final File lib : parsed.valuesOf(providers.libO)) {
            builder.lib(lib);
        }

        for (final TransformerProvider provider : providers.providers) {
            if (parsed.has(providers.triggerOptions.get(provider))) {
                try {
                    builder.add(requireNonNull(
                        provider.create(parsed),
                        () -> "The provider " + provider.id() + " returned a null value when creating a transformer"
                    ));
                } catch (final TransformerProvisionException ex) {
                    ex.initId(provider.id());
                    throw ex;
                }
            }
        }

        return builder.build();
    }

    /**
     * Expand {@code --cfg <argfile>}, {@code --cfg=<argfile>}, and {@code @argfile} to include the options provided.
     *
     * @param args the arguments to expand
     * @return the expanded arguments
     */
    private static List<String> expandArgs(final List<String> args) throws IOException {
        final List<String> ret = new ArrayList<>(args.size());
        boolean disableAtFiles = false;

        // TODO: can this be built into jopt-simple somehow?
        for (int i = 0, length = args.size(); i < length; i++) {
            final String arg = args.get(i);
            if (arg.equals("--cfg")) {
                if (i + 1 == length) {
                    throw new IllegalArgumentException("No value specified for '--cfg'");
                }
                disableAtFiles |= !ForgeAutoRenamingToolInvoker.consumeArgFile(args.get(++i), ret);
            } else if (arg.equals("--cfg=")) {
                disableAtFiles |= !ForgeAutoRenamingToolInvoker.consumeArgFile(arg.substring("--cfg=".length()), ret);
            } else if (!disableAtFiles && arg.startsWith("@")) {
                if (arg.startsWith("@@")) {
                    ret.add(arg.substring(1));
                } else {
                    disableAtFiles = !ForgeAutoRenamingToolInvoker.consumeArgFile(arg.substring(1), ret);
                }
            } else if (arg.equals("--disable-@files")){
                disableAtFiles = true;
            } else {
                ret.add(arg);
            }
        }

        return ret;
    }

    private static boolean consumeArgFile(final String fileName, final List<String> ret) throws IOException {
        // TODO: implement the full spec (https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html#java-command-line-argument-files)
        boolean shouldContinue = true;
        try (final Stream<String> lines = Files.lines(Paths.get(fileName), StandardCharsets.UTF_8)) {
            for (final Iterator<String> it = lines.iterator(); it.hasNext();) {
                final String line = it.next();

                if (line.equals("--disable-@files")) {
                    shouldContinue = false;
                    continue;
                }
                ret.add(line);
            }
        }

        return shouldContinue;
    }

    static class ProviderHolder {

        private static final ThreadLocal<ProviderHolder> INSTANCE = ThreadLocal.withInitial(ProviderHolder::create);

        final List<TransformerProvider> providers;
        final Map<TransformerProvider, OptionSpec<?>> triggerOptions;
        final OptionParser parser;

        // Built-in options

        final OptionSpec<File> inputO;
        final OptionSpec<File> outputO;
        final OptionSpec<File> libO;
        final OptionSpec<Integer> threadsO;

        ProviderHolder(final List<TransformerProvider> providers, final Map<TransformerProvider, OptionSpec<?>> triggerOptions, final OptionParser parser) {
            this.providers = Collections.unmodifiableList(new ArrayList<>(providers));
            this.triggerOptions = Collections.unmodifiableMap(triggerOptions);
            this.parser = parser;

            this.inputO = parser.accepts("input", "Input jar file")
                .withRequiredArg()
                .ofType(File.class);
            this.outputO = parser.accepts("output", "Output jar file")
                .withRequiredArg()
                .ofType(File.class);
            this.libO = parser.acceptsAll(Arrays.asList("lib", "e"), "Additional libraries to use for inheritance")
                .withRequiredArg()
                .ofType(File.class);
            this.threadsO = parser.accepts("threads", "Number of threads to use when processing")
                .withRequiredArg()
                .ofType(Integer.class)
                .defaultsTo(Runtime.getRuntime().availableProcessors());
        }

        static ProviderHolder create() {
            final List<TransformerProvider> transformers = ProviderHolder.discoverTransformers();
            if (!validateTransformers(transformers)) {
                throw new IllegalStateException("Invalid transformers picked up, see log output for more information.");
            }
            final Map<TransformerProvider, OptionSpec<?>> triggerOptions = new HashMap<>();
            final OptionParser parser = ProviderHolder.collectOptions(transformers, triggerOptions);
            return new ProviderHolder(transformers, triggerOptions, parser);
        }

        static List<TransformerProvider> discoverTransformers() {
            final List<TransformerProvider> results = new ArrayList<>();
            final ServiceLoader<TransformerProvider> loader = ServiceLoader.load(TransformerProvider.class, TransformerProvider.class.getClassLoader());

           for (final Iterator<TransformerProvider> it = loader.iterator(); it.hasNext();) {
               try {
                   final TransformerProvider provider = it.next();
                   results.add(provider);
                   LOGGER.debug(
                       "Discovered transformer provider '{}' in {} from {}",
                       provider.id(),
                       provider.getClass(),
                       provider.getClass().getProtectionDomain().getCodeSource()
                   );
               } catch (final ServiceConfigurationError ex) {
                   LOGGER.error("Failed to configure transformer provider", ex);
               }
           }
           return results;
        }

        static boolean validateTransformers(final List<TransformerProvider> transformers) {
            boolean success = true;
            for (final TransformerProvider provider : transformers) {
                if (provider.id() == null) {
                    LOGGER.error("Transformer provider in class {} has a null ID", provider.getClass());
                    success = false;
                    continue;
                }
                if (!TransformerProvider.ID_PATTERN.matcher(provider.id()).matches()) {
                    LOGGER.error("The transformer provider '{}' in {} has an ID that does not match the regex {}", provider.id(), provider.getClass(), TransformerProvider.ID_PATTERN_REGEX);
                    success = false;
                }
            }

            return success;
        }

        static OptionParser collectOptions(final List<TransformerProvider> providers, final Map<TransformerProvider, OptionSpec<?>> triggerOptions) {
            final OptionParser parser = new OptionParser();

            final class OptionConsumerImpl implements TransformerProvider.OptionConsumer {
                private final OptionSpec<?> baseArg;
                String prefix;

                OptionConsumerImpl(final OptionSpec<?> baseArg, final String prefix) {
                    this.baseArg = baseArg;
                    this.prefix = prefix;
                }

                private String prefixArgument(final String argument) {
                    if (this.prefix == null) {
                        throw new IllegalStateException("Tried to register argument outside of init() method");
                    }
                    if (!TransformerProvider.ID_PATTERN.matcher(argument).matches()) {
                        throw new IllegalArgumentException("The provided argument '" + argument + "' does not match the required pattern " + TransformerProvider.ID_PATTERN_REGEX);
                    }
                    return this.prefix + "-" + argument;
                }

                private List<String> prefixArguments(final List<String> argument) {
                    final List<String> out = new ArrayList<>(argument.size());
                    for (String arg : argument) {
                        out.add(this.prefixArgument(arg));
                    }
                    return out;
                }

                @Override
                public OptionSpecBuilder accepts(final String argument) {
                    return parser.accepts(this.prefixArgument(argument))
                        .availableIf(this.baseArg);
                }

                @Override
                public OptionSpecBuilder accepts(final String argument, final String description) {
                    return parser.accepts(this.prefixArgument(argument), description)
                        .availableIf(this.baseArg);
                }

                @Override
                public OptionSpecBuilder accepts(final List<String> arguments) {
                    return parser.acceptsAll(this.prefixArguments(arguments))
                        .availableIf(this.baseArg);
                }

                @Override
                public OptionSpecBuilder accepts(final List<String> arguments, final String description) {
                    return parser.acceptsAll(this.prefixArguments(arguments), description)
                        .availableIf(this.baseArg);
                }
            }

            for (final TransformerProvider provider : providers) {
                String id = "<unknown>";
                try {
                    id = provider.id();
                    final OptionSpec<?> triggerArg = provider.decorateTriggerOption(parser.accepts(id, provider.description()));

                    final OptionConsumerImpl optionConsumer = new OptionConsumerImpl(triggerArg, id);
                    provider.init(optionConsumer);
                    optionConsumer.prefix = null;
                    triggerOptions.put(provider, triggerArg);
                } catch (final Throwable thr) {
                    LOGGER.error("Error occurred while preparing options for transformer provider {} (in class {}):", id, provider.getClass(), thr);
                }
            }

            return parser;
        }

    }

}
