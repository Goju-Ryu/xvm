package org.xvm.runtime.template.numbers;


import java.math.BigInteger;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xString;
import org.xvm.runtime.template.xString.StringHandle;

import org.xvm.util.PackedInteger;


/**
 * Native IntLiteral implementation.
 */
public class xIntLiteral
        extends xConst
    {
    public static xIntLiteral INSTANCE;

    public xIntLiteral(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        markNativeMethod("construct", STRING, VOID);

        markNativeMethod("toString", VOID, STRING);

        markNativeMethod("toInt8"    , VOID, new String[]{"numbers.Int8"});
        markNativeMethod("toInt16"   , VOID, new String[]{"numbers.Int16"});
        markNativeMethod("toInt32"   , VOID, new String[]{"numbers.Int32"});
        markNativeMethod("toInt"     , VOID, new String[]{"numbers.Int64"});
        markNativeMethod("toInt128"  , VOID, new String[]{"numbers.Int128"});

        markNativeMethod("toByte"    , VOID, new String[]{"numbers.UInt8"});
        markNativeMethod("toUInt16"  , VOID, new String[]{"numbers.UInt16"});
        markNativeMethod("toUInt32"  , VOID, new String[]{"numbers.UInt32"});
        markNativeMethod("toUInt"    , VOID, new String[]{"numbers.UInt64"});
        markNativeMethod("toUInt128" , VOID, new String[]{"numbers.UInt128"});

        markNativeMethod("toVarInt"  , VOID, new String[]{"numbers.VarInt"});
        markNativeMethod("toVarUInt" , VOID, new String[]{"numbers.VarUInt"});
        markNativeMethod("toVarFloat", VOID, new String[]{"numbers.VarFloat"});
        markNativeMethod("toVarDec"  , VOID, new String[]{"numbers.VarDec"});

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        LiteralConstant constVal = (LiteralConstant) constant;
        StringHandle hText       = (StringHandle) frame.getConstHandle(constVal.getStringConstant());
        VarIntHandle hIntLiteral = makeIntLiteral(constVal.getPackedInteger(), hText);

        return frame.pushStack(hIntLiteral);
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, ClassComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        StringHandle hText = (StringHandle) ahVar[0];

        // TODO: large numbers
        try
            {
            long lValue = Long.parseLong(hText.getStringValue());

            return frame.assignValue(iReturn,
                makeIntLiteral(new PackedInteger(lValue), hText));
            }
        catch (NumberFormatException e)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Invalid number \"" + hText.getStringValue() + "\""));
            }
        }

    @Override
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        switch (idProp.getName())
            {
            case "text":
                return frame.assignValue(iReturn, ((VarIntHandle) hTarget).getText());
            }
        return frame.raiseException("not supported field: " + idProp.getName());
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((VarIntHandle) hTarget).getValue();
        PackedInteger pi2 = ((VarIntHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.add(pi2)));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((VarIntHandle) hTarget).getValue();
        PackedInteger pi2 = ((VarIntHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.sub(pi2)));
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((VarIntHandle) hTarget).getValue();
        PackedInteger pi2 = ((VarIntHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.mul(pi2)));
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((VarIntHandle) hTarget).getValue();
        PackedInteger pi2 = ((VarIntHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.div(pi2)));
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((VarIntHandle) hTarget).getValue();
        PackedInteger pi2 = ((VarIntHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.mod(pi2)));
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((VarIntHandle) hTarget).getValue();
        PackedInteger pi2 = ((VarIntHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.and(pi2)));
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((VarIntHandle) hTarget).getValue();
        PackedInteger pi2 = ((VarIntHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.or(pi2)));
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        VarIntHandle hLiteral = (VarIntHandle) hTarget;
        switch (method.getName())
            {
            case "toInt8":
            case "toInt16":
            case "toInt32":
            case "toInt":
            case "toInt128":
            case "toByte":
            case "toUInt16":
            case "toUInt32":
            case "toUInt":
            case "toUInt128":
            case "toVarInt":
            case "toVarUInt":
            case "toVarFloat":
            case "toVarDec":
                TypeConstant  typeRet  = method.getReturn(0).getType();
                ClassTemplate template = f_templates.getTemplate(typeRet);
                PackedInteger piValue  = hLiteral.getValue();

                if (template instanceof xConstrainedInteger)
                    {
                    return ((xConstrainedInteger) template).
                        convertLong(frame, piValue, iReturn);
                    }
                if (template instanceof BaseInt128)
                    {
                    BaseInt128  template128 = (BaseInt128) template;
                    BigInteger  biValue     = piValue.getBigInteger();
                    LongLong    llValue     = LongLong.fromBigInteger(biValue);

                    if (!template128.f_fSigned && llValue.signum() < 0)
                        {
                        return template128.overflow(frame);
                        }
                    return frame.assignValue(iReturn, template128.makeLongLong(llValue));
                    }
                break;
            }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    protected VarIntHandle makeIntLiteral(PackedInteger piValue)
        {
        return new VarIntHandle(getCanonicalClass(), piValue, null);
        }

    protected VarIntHandle makeIntLiteral(PackedInteger piValue, StringHandle hText)
        {
        return new VarIntHandle(getCanonicalClass(), piValue, hText);
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        VarIntHandle hLiteral = (VarIntHandle) hTarget;
        return frame.assignValue(iReturn, hLiteral.getText());
        }

    /**
     * This handle type is used by VarInt, VarUInt as well as IntLiteral.
     */
    public static class VarIntHandle
            extends ObjectHandle
        {
        public VarIntHandle(TypeComposition clazz, PackedInteger piValue, StringHandle hText)
            {
            super(clazz);

            assert piValue != null;

            m_piValue = piValue;
            }

        public StringHandle getText()
            {
            StringHandle hText = m_hText;
            if (hText == null)
                {
                m_hText = hText = xString.makeHandle(m_piValue.toString());
                }
            return hText;
            }

        public PackedInteger getValue()
            {
            return m_piValue;
            }

        @Override
        public int hashCode() { return m_piValue.hashCode(); }

        @Override
        public boolean equals(Object obj)
            {
            return obj instanceof VarIntHandle && m_piValue.equals(((VarIntHandle) obj).m_piValue);
            }

        @Override
        public String toString()
            {
            return super.toString() + m_piValue.toString();
            }

        protected PackedInteger m_piValue;
        protected StringHandle  m_hText; // (optional) cached text handle
        }
    }
