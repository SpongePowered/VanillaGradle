package org.spongepowered.gradle.vanilla.repository.mappings;

import groovy.lang.Closure;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectCollectionSchema;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Namer;
import org.gradle.api.PolymorphicDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Rule;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.internal.NamedDomainObjectContainerConfigureDelegate;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.util.ConfigureUtil;
import org.spongepowered.gradle.vanilla.MinecraftExtension;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

public class MappingsContainer implements PolymorphicDomainObjectContainer<MappingsEntry> {
    private final Project project;
    private final MinecraftExtension extension;
    private final PolymorphicDomainObjectContainer<MappingsEntry> delegate;

    public MappingsContainer(Project project, MinecraftExtension extension) {
        this.project = project;
        this.extension = extension;
        delegate = project.getObjects().polymorphicDomainObjectContainer(MappingsEntry.class);
    }

    // -- Delegating to actual implementation -- //

    @Override
    public <U extends MappingsEntry> U create(String name, Class<U> type) throws InvalidUserDataException {
        return delegate.create(name, type);
    }

    @Override
    public <U extends MappingsEntry> U maybeCreate(String name, Class<U> type) throws InvalidUserDataException {
        return delegate.maybeCreate(name, type);
    }

    @Override
    public <U extends MappingsEntry> U create(String name, Class<U> type, Action<? super U> configuration) throws InvalidUserDataException {
        return delegate.create(name, type, configuration);
    }

    @Override
    public <U extends MappingsEntry> NamedDomainObjectContainer<U> containerWithType(Class<U> type) {
        return delegate.containerWithType(type);
    }

    @Override
    public <U extends MappingsEntry> NamedDomainObjectProvider<U> register(String name, Class<U> type, Action<? super U> configurationAction) throws InvalidUserDataException {
        return delegate.register(name, type, configurationAction);
    }

    @Override
    public <U extends MappingsEntry> NamedDomainObjectProvider<U> register(String name, Class<U> type) throws InvalidUserDataException {
        return delegate.register(name, type);
    }

    @Override
    public MappingsEntry create(final String name) throws InvalidUserDataException {
        return this.delegate.create(name);
    }

    @Override
    public MappingsEntry maybeCreate(final String name) {
        return this.delegate.maybeCreate(name);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public MappingsEntry create(final String name, final Closure configureClosure) throws InvalidUserDataException {
        return this.delegate.create(name, configureClosure);
    }

    @Override
    public MappingsEntry create(final String name, final Action<? super MappingsEntry> configureAction)
            throws InvalidUserDataException {
        return this.delegate.create(name, configureAction);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public NamedDomainObjectContainer<MappingsEntry> configure(final Closure configureClosure) {
        // TODO: This uses internal API, see if there's a more 'public' way to do this
        return ConfigureUtil.configureSelf(configureClosure, this, new NamedDomainObjectContainerConfigureDelegate(configureClosure, this));
    }

    @Override
    public NamedDomainObjectProvider<MappingsEntry> register(final String name, final Action<? super MappingsEntry> configurationAction)
            throws InvalidUserDataException {
        return this.delegate.register(name, configurationAction);
    }

    @Override
    public NamedDomainObjectProvider<MappingsEntry> register(final String name) throws InvalidUserDataException {
        return this.delegate.register(name);
    }

    @Override
    public int size() {
        return this.delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return this.delegate.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
        return this.delegate.contains(o);
    }

    @Override
    public Iterator<MappingsEntry> iterator() {
        return this.delegate.iterator();
    }

    @Override
    public Object[] toArray() {
        return this.delegate.toArray();
    }

    @Override public <T> T[] toArray(final T[] a) {
        return this.delegate.toArray(a);
    }

    @Override
    public boolean add(final MappingsEntry e) {
        return this.delegate.add(e);
    }

    @Override
    public boolean remove(final Object o) {
        return this.delegate.remove(o);
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        return this.delegate.containsAll(c);
    }

    @Override
    public boolean addAll(final Collection<? extends MappingsEntry> c) {
        return this.delegate.addAll(c);
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        return this.delegate.removeAll(c);
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        return this.delegate.retainAll(c);
    }

    @Override
    public void clear() {
        this.delegate.clear();
    }

    @Override
    public Namer<MappingsEntry> getNamer() {
        return this.delegate.getNamer();
    }

    @Override
    public SortedMap<String, MappingsEntry> getAsMap() {
        return this.delegate.getAsMap();
    }

    @Override
    public SortedSet<String> getNames() {
        return this.delegate.getNames();
    }

    @Nullable
    @Override
    public MappingsEntry findByName(final String name) {
        return this.delegate.findByName(name);
    }

    @Override
    public MappingsEntry getByName(final String name) throws UnknownDomainObjectException {
        return this.delegate.getByName(name);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public MappingsEntry getByName(final String name, final Closure configureClosure) throws UnknownDomainObjectException {
        return this.delegate.getByName(name, configureClosure);
    }

    @Override
    public MappingsEntry getByName(final String name, final Action<? super MappingsEntry> configureAction) throws UnknownDomainObjectException {
        return this.delegate.getByName(name, configureAction);
    }

    @Override
    public MappingsEntry getAt(final String name) throws UnknownDomainObjectException {
        return this.delegate.getAt(name);
    }

    @Override
    public Rule addRule(final Rule rule) {
        return this.delegate.addRule(rule);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Rule addRule(final String description, final Closure ruleAction) {
        return this.delegate.addRule(description, ruleAction);
    }

    @Override
    public Rule addRule(final String description, final Action<String> ruleAction) {
        return this.delegate.addRule(description, ruleAction);
    }

    @Override
    public List<Rule> getRules() {
        return this.delegate.getRules();
    }

    @Override
    public void addLater(final Provider<? extends MappingsEntry> provider) {
        this.delegate.addLater(provider);
    }

    @Override
    public void addAllLater(
            final Provider<? extends Iterable<MappingsEntry>> provider) {
        this.delegate.addAllLater(provider);
    }

    @Override
    public <S extends MappingsEntry> NamedDomainObjectSet<S> withType(final Class<S> type) {
        return this.delegate.withType(type);
    }

    @Override
    public <S extends MappingsEntry> DomainObjectCollection<S> withType(final Class<S> type, final Action<? super S> configureAction) {
        return this.delegate.withType(type, configureAction);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public <S extends MappingsEntry> DomainObjectCollection<S> withType(final Class<S> type, final Closure configureClosure) {
        return this.delegate.withType(type, configureClosure);
    }

    @Override
    public NamedDomainObjectSet<MappingsEntry> matching(final Spec<? super MappingsEntry> spec) {
        return this.delegate.matching(spec);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public NamedDomainObjectSet<MappingsEntry> matching(final Closure spec) {
        return this.delegate.matching(spec);
    }

    @Override
    public Action<? super MappingsEntry> whenObjectAdded(final Action<? super MappingsEntry> action) {
        return this.delegate.whenObjectAdded(action);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void whenObjectAdded(final Closure action) {
        this.delegate.whenObjectAdded(action);
    }

    @Override
    public Action<? super MappingsEntry> whenObjectRemoved(final Action<? super MappingsEntry> action) {
        return this.delegate.whenObjectRemoved(action);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void whenObjectRemoved(final Closure action) {
        this.delegate.whenObjectRemoved(action);
    }

    @Override
    public void all(final Action<? super MappingsEntry> action) {
        this.delegate.all(action);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void all(final Closure action) {
        this.delegate.all(action);
    }

    @Override
    public void configureEach(final Action<? super MappingsEntry> action) {
        this.delegate.configureEach(action);
    }

    @Override
    public NamedDomainObjectProvider<MappingsEntry> named(final String name) throws UnknownDomainObjectException {
        return this.delegate.named(name);
    }

    @Override
    public NamedDomainObjectProvider<MappingsEntry> named(
            final String name,
            final Action<? super MappingsEntry> configurationAction
    ) throws UnknownDomainObjectException {
        return this.delegate.named(name, configurationAction);
    }

    @Override
    public <S extends MappingsEntry> NamedDomainObjectProvider<S> named(
            final String name,
            final Class<S> type
    ) throws UnknownDomainObjectException {
        return this.delegate.named(name, type);
    }

    @Override
    public <S extends MappingsEntry> NamedDomainObjectProvider<S> named(
            final String name,
            final Class<S> type,
            final Action<? super S> configurationAction
    ) throws UnknownDomainObjectException {
        return this.delegate.named(name, type, configurationAction);
    }

    @Override
    public NamedDomainObjectCollectionSchema getCollectionSchema() {
        return this.delegate.getCollectionSchema();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Set<MappingsEntry> findAll(final Closure spec) {
        return this.delegate.findAll(spec);
    }
}
