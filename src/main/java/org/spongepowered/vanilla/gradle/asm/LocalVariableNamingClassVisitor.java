package org.spongepowered.vanilla.gradle.asm;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class LocalVariableNamingClassVisitor extends ClassVisitor {

    public LocalVariableNamingClassVisitor(final ClassVisitor classVisitor) {
        super(Opcodes.ASM7, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
        return new LocalVariableNamer((access & Opcodes.ACC_STATIC) != 0, super.visitMethod(access, name, descriptor, signature, exceptions));
    }
}