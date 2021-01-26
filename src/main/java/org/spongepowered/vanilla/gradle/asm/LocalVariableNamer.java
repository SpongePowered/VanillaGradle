package org.spongepowered.vanilla.gradle.asm;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.Map;

public final class LocalVariableNamer extends MethodVisitor {

    private final boolean isStatic;
    private final Map<String, Integer> byDescriptor = new HashMap<>();

    public LocalVariableNamer(final boolean isStatic, final MethodVisitor methodVisitor) {
        super(Opcodes.ASM7, methodVisitor);
        this.isStatic = isStatic;
    }

    @Override
    public void visitLocalVariable(final String name, final String descriptor, final String signature, final Label start, final Label end,
            final int index) {
        super.visitLocalVariable(index == 0 && !this.isStatic ? "this" : "var" + index, descriptor, signature, start, end, index);
    }
}