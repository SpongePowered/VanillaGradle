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
package org.spongepowered.gradle.vanilla.internal.transformer;

import net.minecraftforge.fart.api.Inheritance;
import net.minecraftforge.fart.api.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.gradle.vanilla.internal.Constants;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class RecordSignatureFixer implements Transformer {
    private final Consumer<String> debug;
    private final Inheritance inh;

    public RecordSignatureFixer(final Consumer<String> debug, final Inheritance inh) {
        this.debug = debug;
        this.inh = inh;
    }

    @Override
    public ClassEntry process(final ClassEntry entry) {
        final ClassReader reader = new ClassReader(entry.getData());
        final ClassWriter writer = new ClassWriter(reader, 0);

        reader.accept(new Fixer(writer), 0);
        return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
    }

    private class Fixer extends ClassVisitor {
        private TypeParameterCollector paramCollector;
        private boolean isRecord;
        private boolean patchSignature;
        private final ClassVisitor originalParent;
        private ClassNode node;

        public Fixer(final ClassVisitor parent) {
            super(Constants.ASM_VERSION, parent);
            this.originalParent = parent;
        }

        @Override
        public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
            this.isRecord = "java/lang/Record".equals(superName);
            this.patchSignature = signature == null;
            if (this.isRecord && this.patchSignature) {
                this.node = new ClassNode();
                this.cv = this.node;
            }
            // todo: validate type parameters from superinterfaces
            // this would need to get signature information from bytecode + runtime classes
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public RecordComponentVisitor visitRecordComponent(final String name, final String descriptor, final String signature) {
            if (signature != null && this.patchSignature) { // signature implies non-primitive type
                if (this.paramCollector == null) {
                    this.paramCollector = new TypeParameterCollector();
                }
                this.paramCollector.baseType = Type.getType(descriptor);
                this.paramCollector.param = TypeParameterCollector.FIELD;
                new SignatureReader(signature).accept(this.paramCollector);
            }
            return super.visitRecordComponent(name, descriptor, signature);
        }

        @Override
        public FieldVisitor visitField(final int access, final String name, final String descriptor, final String signature, final Object value) {
            if (this.isRecord && signature != null && this.patchSignature) { // signature implies non-primitive type
                if (this.paramCollector == null)
                    this.paramCollector = new TypeParameterCollector();
                this.paramCollector.baseType = Type.getType(descriptor);
                this.paramCollector.param = TypeParameterCollector.FIELD;
                new SignatureReader(signature).accept(this.paramCollector);
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
            if (this.isRecord && signature != null && this.patchSignature) { // signature implies non-primitive type
                if (this.paramCollector == null)
                    this.paramCollector = new TypeParameterCollector();
                this.paramCollector.baseType = Type.getType(descriptor);
                this.paramCollector.param = TypeParameterCollector.FIELD; // start out before parameters come in
                new SignatureReader(signature).accept(this.paramCollector);
                if (this.paramCollector.declaredParams != null) {
                    this.paramCollector.declaredParams.clear();
                }
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        @Override
        public void visitEnd() {
            if (this.isRecord && this.patchSignature && this.paramCollector != null && !this.paramCollector.typeParameters.isEmpty()) {
                // Proguard also strips the Signature attribute, so we have to reconstruct that, to a point where this class is accepted by
                // javac when on the classpath. This requires every type parameter referenced to have been declared within the class.
                // Records are implicitly static and have a defined superclass of java/lang/Record, so there can be type parameters in play from:
                // - fields
                // - methods (which can declare their own formal parameters)
                // - record components
                // - superinterfaces (less important, we just get raw type warnings)
                //
                // This will not be perfect, but provides enough information to allow compilation and enhance decompiler output.
                // todo: allow type-specific rules to infer deeper levels (for example, T with raw type Comparable is probably Comparable<T>)

                final SignatureWriter sw = new SignatureWriter();
                // Formal parameters
                // find all used type parameters, plus guesstimated bounds
                for (final Map.Entry<String, String> param : this.paramCollector.typeParameters.entrySet()) {
                    sw.visitFormalTypeParameter(param.getKey());
                    if (!param.getValue().equals(TypeParameterCollector.UNKNOWN)) {
                        final Inheritance.IClassInfo cls = RecordSignatureFixer.this.inh.getClass(param.getValue()).orElse(null);
                        if (cls != null) {
                            final SignatureVisitor parent;
                            if ((cls.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
                                parent = sw.visitInterfaceBound();
                            } else {
                                parent = sw.visitClassBound();
                            }
                            parent.visitClassType(param.getValue());
                            parent.visitEnd();
                            continue;
                        } else {
                            RecordSignatureFixer.this.debug.accept("Unable to find information for type " + param.getValue());
                        }
                    }
                    final SignatureVisitor cls = sw.visitClassBound();
                    cls.visitClassType("java/lang/Object");
                    cls.visitEnd();
                }

                // Supertype (always Record)
                final SignatureVisitor sv = sw.visitSuperclass();
                sv.visitClassType(this.node.superName);
                sv.visitEnd();

                // Superinterfaces
                for (final String superI : this.node.interfaces) {
                    final SignatureVisitor itfV = sw.visitInterface();
                    itfV.visitClassType(superI);
                    sv.visitEnd();
                }
                final String newSignature = sw.toString();
                RecordSignatureFixer.this.debug.accept("New signature for " + this.node.name + ": " + newSignature);
                this.node.signature = newSignature;
            }
            // feed node through to the original output visitor
            if (this.node != null && this.originalParent != null) {
                this.node.accept(this.originalParent);
            }
        }
    }

    static class TypeParameterCollector extends SignatureVisitor {
        private static final int RETURN_TYPE = -2;
        static final int FIELD = -1;
        static final String UNKNOWN = "???";
        Map<String, String> typeParameters = new HashMap<>(); // <Parameter, FieldType>
        Type baseType;
        int param = -1;
        int level;
        Set<String> declaredParams;

        public TypeParameterCollector() {
            super(Constants.ASM_VERSION);
        }

        @Override
        public void visitFormalTypeParameter(final String name) {
            if (this.declaredParams == null)
                this.declaredParams = new HashSet<>();
            this.declaredParams.add(name);
        }

        @Override
        public void visitTypeVariable(final String name) {
            if (!this.typeParameters.containsKey(name) || this.typeParameters.get(name).equals(TypeParameterCollector.UNKNOWN)) {
                if (this.level == 0 && this.baseType != null && (this.declaredParams == null || !this.declaredParams.contains(name))) {
                    final String typeName;
                    switch (this.param) {
                        case TypeParameterCollector.FIELD: // field
                            typeName = this.baseType.getInternalName();
                            break;
                        case TypeParameterCollector.RETURN_TYPE: // method return value
                            typeName = this.baseType.getReturnType().getInternalName();
                            break;
                        default:
                            typeName = this.baseType.getArgumentTypes()[this.param].getInternalName();
                            break;
                    }
                    this.typeParameters.put(name, typeName);
                } else {
                    this.typeParameters.put(name, TypeParameterCollector.UNKNOWN);
                }
            }
            super.visitTypeVariable(name);
        }

        @Override
        public void visitClassType(final String name) {
            this.level++;
            super.visitClassType(name);
        }

        @Override
        public void visitInnerClassType(final String name) {
            this.level++;
            super.visitInnerClassType(name);
        }

        @Override
        public SignatureVisitor visitTypeArgument(final char wildcard) {
            this.level++;
            return super.visitTypeArgument(wildcard);
        }

        @Override
        public void visitEnd() {
            if (this.level-- <= 0) {
                throw new IllegalStateException("Unbalanced signature levels");
            }
            super.visitEnd();
        }

        // for methods

        @Override
        public SignatureVisitor visitParameterType() {
            this.param++;
            return super.visitParameterType();
        }

        @Override
        public SignatureVisitor visitReturnType() {
            this.param = TypeParameterCollector.RETURN_TYPE;
            return super.visitReturnType();
        }
    }

}
