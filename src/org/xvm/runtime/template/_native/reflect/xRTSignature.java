package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xInt64;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xString;

import org.xvm.runtime.template.collections.xArray;


/**
 * Native (abstract level) Method and Function implementation.
 */
public class xRTSignature
        extends xConst
    {
    public static xRTSignature INSTANCE;

    public xRTSignature(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public void initDeclared()
        {
        markNativeProperty("name");
        markNativeProperty("params");
        markNativeProperty("returns");
        markNativeProperty("conditionalResult");
        markNativeProperty("futureResult");

        markNativeMethod("hasTemplate", null, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        SignatureHandle hFunc = (SignatureHandle) hTarget;

        switch (sPropName)
            {
            case "name":
                return getNameProperty(frame, hFunc, iReturn);

            case "params":
                return getParamsProperty(frame, hFunc, iReturn);

            case "returns":
                return getReturnsProperty(frame, hFunc, iReturn);

            case "conditionalResult":
                return getConditionalResultProperty(frame, hFunc, iReturn);

            case "futureResult":
                return getFutureResultProperty(frame, hFunc, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        SignatureHandle hFunc = (SignatureHandle) hTarget;
        switch (method.getName())
            {
            case "hasTemplate":
                return calcHasTemplate(frame, hFunc, aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: name.get()
     */
    public int getNameProperty(Frame frame, SignatureHandle hFunc, int iReturn)
        {
        xString.StringHandle handle = xString.makeHandle(hFunc.getName());
        return frame.assignValue(iReturn, handle);
        }

    /**
     * Implements property: params.get()
     */
    public int getParamsProperty(Frame frame, SignatureHandle hFunc, int iReturn)
        {
        return new RTArrayConstructor(hFunc.getMethod(), false, iReturn).doNext(frame);
        }

    /**
     * Implements property: params.get()
     */
    public int getReturnsProperty(Frame frame, SignatureHandle hFunc, int iReturn)
        {
        return new RTArrayConstructor(hFunc.getMethod(), true, iReturn).doNext(frame);
        }

    /**
     * Implements property: conditionalResult.get()
     */
    public int getConditionalResultProperty(Frame frame, SignatureHandle hFunc, int iReturn)
        {
        MethodStructure        structFunc = hFunc.getMethod();
        xBoolean.BooleanHandle handle     = xBoolean.makeHandle(structFunc.isConditionalReturn());
        return frame.assignValue(iReturn, handle);
        }

    /**
     * Implements property: futureResult.get()
     */
    public int getFutureResultProperty(Frame frame, SignatureHandle hFunc, int iReturn)
        {
        xBoolean.BooleanHandle handle = xBoolean.makeHandle(hFunc.isAsync());
        return frame.assignValue(iReturn, handle);
        }


    // ----- method implementations --------------------------------------------------------------

    /**
     * Method implementation: `conditional MethodTemplate hasTemplate()`
     */
    public int calcHasTemplate(Frame frame, SignatureHandle hFunc, int[] aiReturn)
        {
        // TODO
        throw new UnsupportedOperationException();
        }


    // ----- Template and ClassComposition caching and helpers -------------------------------------

    /**
     * @return the TypeConstant for a Return
     */
    public TypeConstant ensureReturnType()
        {
        TypeConstant type = RETURN_TYPE;
        if (type == null)
            {
            ConstantPool pool = INSTANCE.pool();
            RETURN_TYPE = type = pool.ensureEcstasyTypeConstant("reflect.Return");
            assert type != null;
            }
        return type;
        }

    /**
     * @return the TypeConstant for an RTReturn
     */
    public TypeConstant ensureRTReturnType()
        {
        TypeConstant type = RTRETURN_TYPE;
        if (type == null)
            {
            ConstantPool pool = INSTANCE.pool();
            RTRETURN_TYPE = type = pool.ensureEcstasyTypeConstant("_native.reflect.RTReturn");
            assert type != null;
            }
        return type;
        }

    /**
     * @return the TypeConstant for a Parameter
     */
    public TypeConstant ensureParamType()
        {
        TypeConstant type = PARAM_TYPE;
        if (type == null)
            {
            ConstantPool pool = INSTANCE.pool();
            PARAM_TYPE = type = pool.ensureEcstasyTypeConstant("reflect.Parameter");
            assert type != null;
            }
        return type;
        }

    /**
     * @return the TypeConstant for an RTParameter
     */
    public TypeConstant ensureRTParamType()
        {
        TypeConstant type = RTPARAM_TYPE;
        if (type == null)
            {
            ConstantPool pool = INSTANCE.pool();
            RTPARAM_TYPE = type = pool.ensureEcstasyTypeConstant("_native.reflect.RTParameter");
            assert type != null;
            }
        return type;
        }

    /**
     * @return the ClassTemplate for an RTReturn
     */
    public xConst ensureRTReturnTemplate()
        {
        xConst template = RTRETURN_TEMPLATE;
        if (template == null)
            {
            RTRETURN_TEMPLATE = template = (xConst) f_templates.getTemplate(ensureRTReturnType());
            assert template != null;
            }
        return template;
        }

    /**
     * @return the ClassTemplate for an RTParameter
     */
    public xConst ensureRTParamTemplate()
        {
        xConst template = RTPARAM_TEMPLATE;
        if (template == null)
            {
            RTPARAM_TEMPLATE = template = (xConst) f_templates.getTemplate(ensureRTParamType());
            assert template != null;
            }
        return template;
        }

    /**
     * @return the ClassTemplate for an Array of Return
     */
    public xArray ensureReturnArrayTemplate()
        {
        xArray template = RETURN_ARRAY_TEMPLATE;
        if (template == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeTypeArray = pool.ensureParameterizedTypeConstant(pool.typeArray(),
                    ensureReturnType());
            RETURN_ARRAY_TEMPLATE = template = ((xArray) f_templates.getTemplate(typeTypeArray));
            assert template != null;
            }
        return template;
        }

    /**
     * @return the ClassTemplate for an Array of Parameter
     */
    public xArray ensureParamArrayTemplate()
        {
        xArray template = PARAM_ARRAY_TEMPLATE;
        if (template == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeTypeArray = pool.ensureParameterizedTypeConstant(pool.typeArray(),
                    ensureParamType());
            PARAM_ARRAY_TEMPLATE = template = ((xArray) f_templates.getTemplate(typeTypeArray));
            assert template != null;
            }
        return template;
        }

    /**
     * @return the ClassComposition for an RTReturn of the specified type
     */
    public ClassComposition ensureRTReturn(TypeConstant typeValue)
        {
        assert typeValue != null;
        ConstantPool pool = ConstantPool.getCurrentPool();
        TypeConstant typeRTReturn = pool.ensureParameterizedTypeConstant(ensureRTReturnType(), typeValue);
        return f_templates.resolveClass(typeRTReturn);
        }

    /**
     * @return the ClassComposition for a RTParameter of the specified type
     */
    public ClassComposition ensureRTParameter(TypeConstant typeValue)
        {
        assert typeValue != null;
        ConstantPool pool = ConstantPool.getCurrentPool();
        TypeConstant typeRTParam = pool.ensureParameterizedTypeConstant(ensureRTParamType(), typeValue);
        return f_templates.resolveClass(typeRTParam);
        }

    /**
     * @return the ClassComposition for an Array of Return
     */
    public ClassComposition ensureReturnArray()
        {
        ClassComposition clz = RETURN_ARRAY;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeReturnArray = pool.ensureParameterizedTypeConstant(pool.typeArray(),
                    ensureReturnType());
            RETURN_ARRAY = clz = f_templates.resolveClass(typeReturnArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the ClassComposition for an Array of Parameter
     */
    public ClassComposition ensureParamArray()
        {
        ClassComposition clz = PARAM_ARRAY;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeParamArray = pool.ensureParameterizedTypeConstant(pool.typeArray(),
                    ensureParamType());
            PARAM_ARRAY = clz = f_templates.resolveClass(typeParamArray);
            assert clz != null;
            }
        return clz;
        }

    static private TypeConstant RETURN_TYPE;
    static private TypeConstant PARAM_TYPE;
    static private TypeConstant RTRETURN_TYPE;
    static private TypeConstant RTPARAM_TYPE;

    static private xConst RTRETURN_TEMPLATE;
    static private xConst RTPARAM_TEMPLATE;

    static private xArray RETURN_ARRAY_TEMPLATE;
    static private xArray PARAM_ARRAY_TEMPLATE;

    static private ClassComposition RETURN_ARRAY;
    static private ClassComposition PARAM_ARRAY;


    // ----- Object handle -------------------------------------------------------------------------

    /**
     * Signature handle.
     */
    public abstract static class SignatureHandle
            extends ObjectHandle
        {
        // ----- constructors -----------------------------------------------------------------

        protected SignatureHandle(TypeComposition clz, MethodStructure function)
            {
            this(clz, function.getIdentityConstant(), function, function.getIdentityConstant().getType());
            }

        protected SignatureHandle(TypeComposition clz, MethodConstant idMethod, MethodStructure method, TypeConstant type)
            {
            super(clz);

            f_type     = type;
            f_idMethod = idMethod;
            f_method   = method;
            f_chain    = null;
            f_nDepth   = 0;
            }

        protected SignatureHandle(TypeComposition clz, CallChain chain, int nDepth)
            {
            super(clz);

            f_idMethod = chain.getMethod(nDepth).getIdentityConstant();
            f_type     = f_idMethod.getType();
            f_method   = null;
            f_chain    = chain;
            f_nDepth   = nDepth;
            }

        // ----- fields -----------------------------------------------------------------------

        @Override
        public TypeConstant getType()
            {
            return f_type;
            }

        public String getName()
            {
            MethodStructure method = getMethod();
            if (method != null)
                {
                return method.getName();
                }

            MethodConstant id = f_idMethod;
            if (id != null)
                {
                return id.getName();
                }

            return "?";
            }

        public MethodStructure getMethod()
            {
            return f_method == null
                    ? f_chain == null
                            ? null
                            : f_chain.getMethod(f_nDepth)
                    : f_method;
            }

        public int getParamCount()
            {
            return getMethod().getParamCount();
            }

        public int getUnboundParamCount()
            {
            return getMethod().getParamCount();
            }

        public Parameter getUnboundParam(int i)
            {
            return getMethod().getParam(i);
            }

        public int getVarCount()
            {
            return getMethod().getMaxVars();
            }

        public boolean isAsync()
            {
            return false;
            }

        // ----- Object methods --------------------------------------------------------------------

        @Override
        public int hashCode()
            {
            return f_idMethod == null ? -17 : f_idMethod.hashCode();
            }

        @Override
        public boolean equals(Object obj)
            {
            if (obj == this)
                {
                return true;
                }

            if (obj.getClass() == SignatureHandle.class)
                {
                SignatureHandle that = (SignatureHandle) obj;
                return this.getMethod().equals(that.getMethod());
                }
            else
                {
                // let the sub-class answer the question
                return obj.equals(this);
                }
            }

        @Override
        public String toString()
            {
            return "Signature: " + getMethod();
            }

        // ----- fields -----------------------------------------------------------------------

        /**
         * The underlying function/method constant id.
         */
        protected final MethodConstant f_idMethod;

        /**
         * The underlying function/method. Might be null sometimes for methods.
         */
        protected final MethodStructure f_method;

        /**
         * The function's or method's type.
         */
        protected final TypeConstant f_type;

        /**
         * The method call chain (not null only if function is null).
         */
        protected final CallChain f_chain;
        protected final int       f_nDepth;
        }


    // ----- inner class: RTArrayConstructor -------------------------------------------------------

    /**
     * A continuation helper to create an array of natural RTReturn or RTParameter objects.
     */
    class RTArrayConstructor
            implements Frame.Continuation
        {
        RTArrayConstructor(MethodStructure method, boolean fRetVals, int iReturn)
            {
            this.method     = method;
            this.fRetVals   = fRetVals;
            this.template   = fRetVals ? ensureRTReturnTemplate()    : ensureRTParamTemplate();
            this.cElements  = method == null ? 0 : fRetVals ? method.getReturnCount() : method.getParamCount();
            this.ahElement  = new ObjectHandle[cElements];
            this.construct  = template.f_struct.findMethod("construct", fRetVals ? 2 : 5);
            this.ahParams   = new ObjectHandle[fRetVals ? 2 : 5];
            this.index      = -1;
            this.iReturn    = iReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            ahElement[index] = frameCaller.popStack();
            return doNext(frameCaller);
            }

        public int doNext(Frame frameCaller)
            {
            while (++index < cElements)
                {
                Parameter        param = fRetVals ? method.getReturn(index) : method.getParam(index);
                TypeConstant     type  = param.getType();
                ClassComposition clz   = fRetVals ? ensureRTReturn(type) : ensureRTParameter(type);
                String           sName = param.getName();
                ahParams[0] = xInt64.makeHandle(index);
                ahParams[1] = sName == null ? xNullable.NULL : xString.makeHandle(sName);
                if (!fRetVals)
                    {
                    ahParams[2] = xBoolean.makeHandle(param.isTypeParameter());
                    if (param.hasDefaultValue())
                        {
                        ahParams[3] = xBoolean.TRUE;
                        ahParams[4] = frameCaller.getConstHandle(param.getDefaultValue());
                        }
                    else
                        {
                        ahParams[3] = xBoolean.FALSE;
                        ahParams[4] = xNullable.NULL;
                        }
                    }

                switch (template.construct(frameCaller, construct, clz, null, ahParams, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        ahElement[index] = frameCaller.popStack();
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }

            xArray templateArray = fRetVals ? ensureReturnArrayTemplate() : ensureParamArrayTemplate();
            ObjectHandle.ArrayHandle hArray = templateArray.createArrayHandle(
                    fRetVals ? ensureReturnArray() : ensureParamArray(), ahElement);
            return frameCaller.assignValue(iReturn, hArray);
            }

        private MethodStructure method;
        private int             cElements;
        private boolean         fRetVals;
        private ObjectHandle[]  ahElement;
        private xConst          template;
        private MethodStructure construct;
        private ObjectHandle[]  ahParams;
        private int             index;
        private int             iReturn;
        }
    }