package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native unchecked UInt64 support.
 */
public class xUncheckedUInt64
        extends xUncheckedConstrainedInt
    {
    public static xUncheckedUInt64 INSTANCE;

    public xUncheckedUInt64(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 0L, 0xFFFF_FFFF_FFFF_FFFFL, 64, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUncheckedInt64.INSTANCE;
        }
    }