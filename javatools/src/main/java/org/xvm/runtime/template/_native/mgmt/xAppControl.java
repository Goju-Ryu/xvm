package org.xvm.runtime.template._native.mgmt;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;

import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * Native implementation of _native.mgmt.AppControl class.
 */
public class xAppControl
        extends ClassTemplate
    {
    public static xAppControl INSTANCE;

    public xAppControl(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

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
    public void initNative()
        {
        TypeConstant typeControl = pool().ensureEcstasyTypeConstant("mgmt.Container.ApplicationControl");

        m_clzControl = ensureClass(getCanonicalType(), typeControl);

        markNativeMethod("invoke", null, null);
        markNativeMethod("mainService", null, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "invoke":
                return invokeInvoke(frame, (ControlHandle) hTarget,
                        (StringHandle) ahArg[0], (TupleHandle) ahArg[1], iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "mainService":
                {
                ControlHandle  hCtrl = (ControlHandle) hTarget;
                ServiceContext ctx   = hCtrl.m_container.getServiceContext();
                return ctx == null
                    ? frame.assignValue(aiReturn[0], xBoolean.FALSE)
                    : frame.assignValues(aiReturn, xBoolean.TRUE, ctx.getService());
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    /**
     * Method implementation: `@Op("()") ReturnTypes invoke(ParamTypes args)`
     */
    public int invokeInvoke(Frame frame, ControlHandle hCtrl,
                            StringHandle hName, TupleHandle hTupleArg, int iReturn)
        {
        Container      container    = hCtrl.m_container;
        ServiceContext ctxContainer = container.ensureServiceContext();

        ObjectHandle[] ahArg    = hTupleArg.m_ahValue;
        String         sMethod  = hName.getStringValue();
        ModuleConstant idModule = container.getModule();
        MethodConstant idMethod = container.findModuleMethod(sMethod, ahArg);

        if (idMethod == null)
            {
            return frame.raiseException("Missing " + idMethod.getValueString() +
                " method for " + idModule.getValueString());
            }

        ClassComposition clzModule = f_templates.resolveClass(idModule.getType());
        CallChain        chain     = clzModule.getMethodCallChain(idMethod.getSignature());
        FunctionHandle   hFunction = new xRTFunction.AsyncHandle(chain)
            {
            @Override
            protected ObjectHandle getContextTarget(Frame frame, ServiceHandle hService)
                {
                return frame.getConstHandle(idModule);
                }
            };

        return hFunction.callT(frame, ctxContainer.getService(), ahArg, iReturn);
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    public ObjectHandle makeHandle(Container container)
        {
        return new ControlHandle(m_clzControl, container);
        }

    protected static class ControlHandle
            extends ObjectHandle
        {
        protected ControlHandle(TypeComposition clazz, Container container)
            {
            super(clazz);

            m_container = container;
            m_fMutable  = false;
            }

        /**
         * The container this AppControl instance is responsible for managing.
         */
        protected Container m_container;
        }

    private ClassComposition m_clzControl;
    }