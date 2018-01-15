package org.xvm.runtime.template;


import java.util.Collections;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;

import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;


/**
 * TODO:
 */
public class xNullable
        extends Enum
    {
    public static NullHandle NULL;

    public xNullable(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initDeclared()
        {
        if (f_struct.getFormat() == Component.Format.ENUM)
            {
            NULL = new NullHandle(ensureCanonicalClass());

            m_listNames = Collections.singletonList("Null");
            m_listHandles = Collections.singletonList(NULL);
            }
        }

    private static class NullHandle
                extends EnumHandle
        {
        NullHandle(TypeComposition clz)
            {
            super(clz, 0);
            }
        }
    }
