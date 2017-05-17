package org.xvm.proto.template;

import org.xvm.proto.*;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xType
        extends TypeCompositionTemplate
    {
    public static xType INSTANCE;

    public xType(TypeSet types)
        {
        super(types, "x:Type", "x:Object", Shape.Interface);

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        // TODO
        }

    public static TypeHandle makeHandle(Type type)
        {
        return new TypeHandle(INSTANCE.f_clazzCanonical, type);
        }

    public static class TypeHandle
            extends ObjectHandle
        {
        public Type m_type; // the type represented by this handle

        protected TypeHandle(TypeComposition clazz, Type type)
            {
            super(clazz);

            m_type = type;
            }
        }
    }
