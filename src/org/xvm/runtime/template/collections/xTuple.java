package org.xvm.runtime.template.collections;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHeap;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xString;


/**
 * TODO:
 */
public class xTuple
        extends ClassTemplate
        implements IndexSupport
    {
    public static xTuple INSTANCE;
    public static xTuple.TupleHandle H_VOID;

    public xTuple(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            H_VOID = makeHandle(ensureCanonicalClass(), Utils.OBJECTS_NONE);
            }
        }

    @Override
    public void initDeclared()
        {
        // TODO: shouldn't that be generated by the compiler?
        f_templates.f_adapter.addMethod(f_struct, "construct", new String[]{"collections.Sequence<Object>"}, VOID);

        markNativeMethod("construct", new String[]{"collections.Sequence<Object>"}, VOID);
        }

    @Override
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        ArrayConstant constTuple = (ArrayConstant) constant;

        assert constTuple.getFormat() == Constant.Format.Tuple;

        ObjectHeap heap = f_templates.f_container.f_heapGlobal;

        Constant[] aconst = constTuple.getValue();
        int c = aconst.length;

        if (c == 0)
            {
            return H_VOID;
            }

        TypeConstant typeTuple = constTuple.getType().resolveGenerics(frame.getGenericsResolver());

        ObjectHandle[] ahValue = new ObjectHandle[c];
        for (int i = 0; i < c; i++)
            {
            ahValue[i] = heap.ensureConstHandle(frame, aconst[i].getPosition());
            }

        TupleHandle hTuple = makeHandle(typeTuple, ahValue);
        hTuple.makeImmutable();
        return hTuple;
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor,
                         TypeComposition clazz, ObjectHandle[] ahVar, int iReturn)
        {
        ObjectHandle hSequence = ahVar[0];
        IndexSupport support = (IndexSupport) hSequence.getTemplate();

        ObjectHandle[] ahValue;

        try
            {
            ahValue = support.toArray(hSequence);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }

        TupleHandle hTuple = new TupleHandle(clazz, ahValue);

        return frame.assignValue(iReturn, hTuple);
        }

    // ----- IndexSupport methods -----

    @Override
    public ObjectHandle extractArrayValue(ObjectHandle hTarget, long lIndex)
            throws ExceptionHandle.WrapperException
        {
        TupleHandle hTuple = (TupleHandle) hTarget;
        ObjectHandle[] ahValue = hTuple.m_ahValue;
        int cElements = ahValue == null ? 0 : ahValue.length;

        if (lIndex < 0 || lIndex >= cElements)
            {
            throw IndexSupport.outOfRange(lIndex, cElements).getException();
            }

        return hTuple.m_ahValue[(int) lIndex];
        }

    @Override
    public ExceptionHandle assignArrayValue(ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;
        ObjectHandle[] ahValue = hTuple.m_ahValue;
        int cElements = ahValue == null ? 0 : ahValue.length;

        if (lIndex < 0 || lIndex >= cElements)
            {
            return IndexSupport.outOfRange(lIndex, cElements);
            }

        if (!hTuple.isMutable())
            {
            return xException.makeHandle("Immutable object");
            }

        hTuple.m_ahValue[(int) lIndex] = hValue;
        return null;
        }

    @Override
    public TypeConstant getElementType(ObjectHandle hTarget, long lIndex)
                throws ExceptionHandle.WrapperException
        {
        TupleHandle hTuple = (TupleHandle) hTarget;
        ObjectHandle[] ahValue = hTuple.m_ahValue;
        int cElements = ahValue == null ? 0 : ahValue.length;

        if (lIndex < 0 || lIndex >= cElements)
            {
            throw IndexSupport.outOfRange(lIndex, cElements).getException();
            }

        return hTuple.m_aType[(int) lIndex];
        }

    @Override
    public long size(ObjectHandle hTarget)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;

        return hTuple.m_ahValue.length;
        }

    @Override
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        TupleHandle hTuple = (TupleHandle) hTarget;

        StringBuilder sb = new StringBuilder()
          .append(hTuple.getComposition().toString())
          .append('(');

        ObjectHandle[] ahValue = hTuple.m_ahValue;
        if (ahValue.length > 0)
            {
            Frame.Continuation stepNext = frameCaller ->
                frameCaller.assignValue(iReturn, xString.makeHandle(sb.toString()));

            return new Utils.ArrayToString(
                sb, hTuple.m_ahValue, null, stepNext).doNext(frame);
            }
        else
            {
            sb.append(')');
            return frame.assignValue(iReturn, xString.makeHandle(sb.toString()));
            }
        }

    // ----- ObjectHandle helpers -----

    public static TupleHandle makeHandle(TypeConstant typeTuple, ObjectHandle[] ahValue)
        {
        return new TupleHandle(INSTANCE.ensureClass(typeTuple), ahValue);
        }

    public static TupleHandle makeHandle(TypeComposition clazz, ObjectHandle[] ahValue)
        {
        return new TupleHandle(clazz, ahValue);
        }

    public static class TupleHandle
            extends ObjectHandle
        {
        public TypeConstant[] m_aType;
        public ObjectHandle[] m_ahValue;
        public boolean m_fFixedSize;
        public boolean m_fPersistent;

        protected TupleHandle(TypeComposition clazz, ObjectHandle[] ahValue)
            {
            super(clazz);

            m_fMutable = true;
            m_ahValue = ahValue;
            }

        @Override
        public String toString()
            {
            return super.toString() + Arrays.toString(m_ahValue);
            }
        }
    }
