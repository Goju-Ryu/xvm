package org.xvm.proto;

import org.xvm.asm.Constants.Access;
import org.xvm.asm.constants.CharStringConstant;
import org.xvm.asm.constants.IntConstant;

import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

import org.xvm.proto.template.IndexSupport;
import org.xvm.proto.template.xException;
import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xFunction.FullyBoundHandle;
import org.xvm.proto.template.xFutureRef.FutureHandle;
import org.xvm.proto.template.xRef.RefHandle;

import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.ObjectHandle.JavaLong;

import org.xvm.proto.template.xTuple;

import java.util.function.Supplier;

/**
 * A call stack frame.
 *
 * @author gg 2017.02.15
 */
public class Frame
    {
    public final Fiber f_fiber;
    public final ServiceContext f_context;
    public final InvocationTemplate f_function;
    public final Op[]           f_aOp;          // the op-codes
    public final ObjectHandle   f_hTarget;      // target
    public final ObjectHandle[] f_ahVar;        // arguments/local var registers
    public final VarInfo[]      f_aInfo;        // optional info for var registers
    public final int            f_iReturn;      // an index for a single return value
                                                // a negative value below -65000 indicates an
                                                // automatic tuple conversion into a (-i-1) register
    public final int[]          f_aiReturn;     // indexes for multiple return values
    public final Frame          f_framePrev;    // the caller's frame
    public final int[]          f_anNextVar;    // at index i, the "next available" var register for scope i

    public int                  m_iScope;       // current scope index (starts with 0)
    public int                  m_iGuard = -1;  // current guard index (-1 if none)
    public int                  m_iPC;          // the program counter
    public Guard[]              m_aGuard;       // at index i, the guard for the guard index i
    public ExceptionHandle      m_hException;   // an exception
    public FullyBoundHandle     m_hfnFinally;   // a "finally" method for the constructors
    public Frame                m_frameNext;    // the next frame to call
    public Supplier<Frame>      m_continuation; // a frame supplier to call after this frame returns
    private ObjectHandle        m_hFrameLocal;  // a "frame local" holding area

    public final static int RET_LOCAL = -65000;   // an indicator for the "frame local single value"
    public final static int RET_UNUSED = -65001;  // an indicator for an "unused return value"
    public final static int RET_MULTI = -65002;   // an indicator for "multiple return values"

    public static final int VAR_STANDARD = 0;
    public static final int VAR_DYNAMIC_REF = 1;
    public static final int VAR_WAITING = 2;

    // construct a frame
    protected Frame(Frame framePrev, InvocationTemplate function,
                    ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn, int[] aiReturn)
        {
        f_context = framePrev.f_context;
        f_fiber = framePrev.f_fiber;

        f_framePrev = framePrev;
        f_function = function;
        f_aOp = function == null ? Op.STUB : function.m_aop;

        f_hTarget = hTarget;
        f_ahVar = ahVar; // [0] - target:private for methods
        f_aInfo = new VarInfo[ahVar.length];

        int cScopes = function == null ? 1 : function.m_cScopes;
        f_anNextVar = new int[cScopes];

        if (hTarget == null)
            {
            f_anNextVar[0] = function == null ? 0 : function.m_cArgs;
            }
        else  // #0 - this:private
            {
            f_ahVar[0]     = hTarget.f_clazz.ensureAccess(hTarget, Access.PRIVATE);
            f_anNextVar[0] = 1 + function.m_cArgs;
            }

        f_iReturn = iReturn;
        f_aiReturn = aiReturn;
        }

    // construct a initial (native) frame
    protected Frame(Fiber fiber, Op[] aopNative,
                    ObjectHandle[] ahVar, int iReturn, int[] aiReturn)
        {
        f_context = fiber.f_context;
        f_fiber = fiber;
        f_framePrev = null;
        f_function = null;
        f_aOp = aopNative;

        f_hTarget = null;
        f_ahVar = ahVar;
        f_aInfo = new VarInfo[ahVar.length];

        f_anNextVar = null;

        f_iReturn = iReturn;
        f_aiReturn = aiReturn;
        }

    // a convenience method; ahVar - prepared variables
    public int call1(InvocationTemplate template, ObjectHandle hTarget,
                                 ObjectHandle[] ahVar, int iReturn)
        {
        m_frameNext = f_context.createFrame1(this, template, hTarget, ahVar, iReturn);
        return Op.R_CALL;
        }

    // a convenience method
    public int callN(InvocationTemplate template, ObjectHandle hTarget,
                                 ObjectHandle[] ahVar, int[] aiReturn)
        {
        m_frameNext = f_context.createFrameN(this, template, hTarget, ahVar, aiReturn);
        return Op.R_CALL;
        }

    // find a first matching guard; unwind the scope and initialize the next var with the exception
    // return the PC of the catch or the R_EXCEPTION value
    protected int findGuard(ExceptionHandle hException)
        {
        Guard[] aGuard = m_aGuard;
        if (aGuard != null)
            {
            TypeComposition clzException = hException.f_clazz;

            for (int iGuard = m_iGuard; iGuard >= 0; iGuard--)
                {
                Guard guard = aGuard[iGuard];

                for (int iCatch = 0, c = guard.f_anClassConstId.length; iCatch < c; iCatch++)
                    {
                    TypeComposition clzCatch = f_context.f_types.
                            ensureComposition(this, guard.f_anClassConstId[iCatch]);
                    if (clzException.extends_(clzCatch))
                        {
                        int nScope = guard.f_nScope;

                        clearAllScopes(nScope - 1);

                        // implicit "enter" with an exception variable introduction
                        m_iScope = nScope;
                        m_iGuard = iGuard - 1;

                        int nNextVar = f_anNextVar[nScope - 1];

                        CharStringConstant constVarName = (CharStringConstant)
                                f_context.f_constantPool.getConstantValue(guard.f_anNameConstId[iCatch]);

                        introduceVar(nNextVar, clzException, constVarName.getValue(), VAR_STANDARD, hException);

                        f_anNextVar[nScope] = nNextVar + 1;
                        m_hException = null;

                        return guard.f_nStartAddress + guard.f_anCatchRelAddress[iCatch];
                        }
                    }
                }
            }
        return Op.R_EXCEPTION;
        }

    // return one of the pre-defined arguments
    public ObjectHandle getPredefinedArgument(int nArgId)
        {
        switch (nArgId)
            {
            case Op.A_LOCAL:
                return m_hFrameLocal;

            case Op.A_SUPER:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return xFunction.makeHandle(((MethodTemplate) f_function).getSuper()).bind(0, f_hTarget);

            case Op.A_TARGET:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hTarget;

            case Op.A_PUBLIC:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hTarget.f_clazz.ensureAccess(f_hTarget, Access.PUBLIC);

            case Op.A_PROTECTED:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hTarget.f_clazz.ensureAccess(f_hTarget, Access.PROTECTED);

            case Op.A_PRIVATE:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hTarget.f_clazz.ensureAccess(f_hTarget, Access.PRIVATE);

            case Op.A_STRUCT:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hTarget.f_clazz.ensureAccess(f_hTarget, Access.STRUCT);

            case Op.A_TYPE:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
            case Op.A_FRAME:
            case Op.A_MODULE:
                throw new UnsupportedOperationException("TODO");

            case Op.A_SERVICE:
                return ServiceContext.getCurrentContext().m_hService;

            default:
                throw new IllegalStateException("Invalid argument" + nArgId);
            }
        }

    // clear the var info for the specified scope
    public void clearScope(int iScope)
        {
        int iVarFrom = f_anNextVar[iScope - 1];
        int iVarTo   = f_anNextVar[iScope] - 1;

        for (int i = iVarFrom; i <= iVarTo; i++)
            {
            VarInfo info = f_aInfo[i];

            if (info != null)
                {
                info.release();

                f_aInfo[i] = null;
                f_ahVar[i] = null;
                }
            }
        }

    // clear the var info for all scopes above the specified one
    public void clearAllScopes(int iScope)
        {
        int iVarFrom = f_anNextVar[iScope];
        int iVarTo   = f_ahVar.length - 1;

        for (int i = iVarFrom; i <= iVarTo; i++)
            {
            VarInfo info = f_aInfo[i];

            if (info != null)
                {
                info.release();

                f_aInfo[i] = null;
                f_ahVar[i] = null;
                }
            }
        }

    // return "private:this"
    public ObjectHandle getThis()
        {
        assert f_hTarget != null;
        return f_hTarget;
        }

    public ObjectHandle getFrameLocal()
        {
        return m_hFrameLocal;
        }

    public void forceValue(int nVar, ObjectHandle hValue)
        {
        int nResult = assignValue(nVar, hValue);
        switch (nResult)
            {
            case Op.R_NEXT:
                return;

            case Op.R_EXCEPTION:
                // TODO: call an error handler?
                System.out.println("Out-of-context exception: " + m_hException);
                return;

            default:
                throw new IllegalStateException(); // assert
            }
        }

    // return R_NEXT, R_EXCEPTION or R_BLOCK
    public int assignValue(int nVar, ObjectHandle hValue)
        {
        if (nVar >= 0)
            {
            VarInfo info = f_aInfo[nVar];

            switch (info.m_nStyle)
                {
                case VAR_DYNAMIC_REF:
                    ExceptionHandle hException = ((RefHandle) f_ahVar[nVar]).set(hValue);
                    if (hException != null)
                        {
                        m_hException = hException;
                        return Op.R_EXCEPTION;
                        }
                    return Op.R_NEXT;

                case VAR_STANDARD:
                    if (hValue instanceof FutureHandle)
                        {
                        FutureHandle hFuture = (FutureHandle) hValue;
                        if (hFuture.f_fSynthetic)
                            {
                            if (hFuture.m_future.isDone())
                                {
                                try
                                    {
                                    hValue = hFuture.get();
                                    }
                                catch (ExceptionHandle.WrapperException e)
                                    {
                                    m_hException = e.getExceptionHandle();
                                    return Op.R_EXCEPTION;
                                    }
                                }
                            else
                                {
                                // mark the register as "waiting for a result",
                                // blocking the next op-code from being executed
                                f_ahVar[nVar] = hFuture;
                                info.m_nStyle = VAR_WAITING;
                                return Op.R_BLOCK;
                                }
                            }
                        }
                    break;
                }

            f_ahVar[nVar] = hValue;
            return Op.R_NEXT;
            }

        switch (nVar)
            {
            case RET_UNUSED:
                return Op.R_NEXT;

            case RET_MULTI:
                throw new IllegalStateException();

            default:
                // any other negative value indicates an automatic tuple conversion
                m_hFrameLocal = hValue;
                return Op.R_NEXT;
            }
        }

    public int returnValue(int iReturn, int iArg)
        {
        assert iReturn >= 0 || iReturn == RET_LOCAL;

        int iResult = f_framePrev.assignValue(iReturn, getReturnValue(iArg));
        switch (iResult)
            {
            case Op.R_EXCEPTION:
                return Op.R_RETURN_EXCEPTION;

            case Op.R_BLOCK:
                return Op.R_BLOCK_RETURN;

            default:
                return Op.R_RETURN;
            }
        }

    public int returnTuple(int iReturn, int[] aiArg)
        {
        assert iReturn >= 0;

        int c = aiArg.length;
        ObjectHandle[] ahValue = new ObjectHandle[c];
        Type[] aType = new Type[c];
        for (int i = 0; i < c; i++)
            {
            aType[i] = f_function.getReturnType(i, null);
            ahValue[i] = getReturnValue(aiArg[i]);
            }

        int iResult = f_framePrev.assignValue(iReturn, xTuple.makeHandle(aType, ahValue));
        switch (iResult)
            {
            case Op.R_EXCEPTION:
                return Op.R_RETURN_EXCEPTION;

            case Op.R_BLOCK:
                return Op.R_BLOCK_RETURN;

            default:
                return Op.R_RETURN;
            }
        }

    private ObjectHandle getReturnValue(int iArg)
        {
        return iArg >= 0 ?
                    f_ahVar[iArg] :
               iArg <= -Op.MAX_CONST_ID ?
                    getPredefinedArgument(iArg) :
                    f_context.f_heapGlobal.ensureConstHandle(-iArg);
        }

    public int checkWaitingRegisters()
        {
        ExceptionHandle hException = null;

        VarInfo[] aInfo = f_aInfo;
        for (int i = 0, c = aInfo.length; i < c; i++)
            {
            VarInfo info = aInfo[i];

            if (info != null && info.m_nStyle == VAR_WAITING)
                {
                FutureHandle hFuture = (FutureHandle) f_ahVar[i];
                if (hFuture.m_future.isDone())
                    {
                    try
                        {
                        f_ahVar[i] = hFuture.get();
                        }
                    catch (ExceptionHandle.WrapperException e)
                        {
                        // use just the last exception
                        hException = e.getExceptionHandle();
                        }
                    info.m_nStyle = VAR_STANDARD;
                    }
                else
                    {
                    return Op.R_BLOCK;
                    }
                }
            }

        if (hException != null)
            {
            m_hException = hException;
            return Op.R_EXCEPTION;
            }
        return Op.R_NEXT;
        }

    // return the class of the specified argument
    public TypeComposition getArgumentClass(int iArg)
        {
        return iArg >= 0 ? getVarInfo(iArg).f_clazz :
            f_context.f_heapGlobal.getConstTemplate(-iArg).f_clazzCanonical;
        }

    // return the ObjectHandle, or null if the value is "pending future", or
    // throw if the async assignment has failed
    public ObjectHandle getArgument(int iArg)
                throws ExceptionHandle.WrapperException
        {
        if (iArg >= 0)
            {
            VarInfo info = f_aInfo[iArg];
            ObjectHandle hValue = f_ahVar[iArg];

            if (hValue == null)
                {
                throw xException.makeHandle("Unassigned value").getException();
                }

            if (info != null && info.m_nStyle == VAR_DYNAMIC_REF)
                {
                hValue = ((RefHandle) hValue).get();

                if (hValue == null)
                    {
                    info.m_nStyle = VAR_WAITING;
                    }
                }

            return hValue;
            }

        return iArg < -Op.MAX_CONST_ID ? getPredefinedArgument(iArg) :
            f_context.f_heapGlobal.ensureConstHandle(-iArg);
        }

    // return the ObjectHandle[] or null if the value is "pending future", or
    // throw if the async assignment has failed
    public ObjectHandle[] getArguments(int[] aiArg, int cVars, int ofStart)
                throws ExceptionHandle.WrapperException
        {
        int cArgs = aiArg.length;

        assert cArgs <= cVars;

        ObjectHandle[] ahArg = new ObjectHandle[cVars];

        for (int i = 0, c = cArgs; i < c; i++)
            {
            ObjectHandle hArg = getArgument(aiArg[i]);
            if (hArg == null)
                {
                return null;
                }

            ahArg[ofStart + i] = hArg;
            }

        return ahArg;
        }

    // return a non-negative value or -1 if the value is "pending future", or
    // throw if the async assignment has failed
    public long getIndex(int iArg)
            throws ExceptionHandle.WrapperException
        {
        long lIndex;
        if (iArg >= 0)
            {
            JavaLong hLong = (JavaLong) getArgument(iArg);
            if (hLong == null)
                {
                return -1l;
                }
            lIndex = hLong.m_lValue;
            }
        else
            {
            IntConstant constant = (IntConstant)
                    f_context.f_heapGlobal.f_constantPool.getConstantValue(-iArg);
            lIndex = constant.getValue().getLong();
            }

        if (lIndex < 0)
            {
            throw IndexSupport.outOfRange(lIndex, 0).getException();
            }
        return lIndex;
        }

    // if the class is not specified, it will be inferred from the handle,
    // which in that case must be specified
    public void introduceVar(int nVar, TypeComposition clz,
                             String sName, int nStyle, ObjectHandle hValue)
        {
        if (clz == null)
            {
            clz = hValue.f_clazz;
            }

        f_aInfo[nVar] = new VarInfo(clz, sName, nStyle);

        if (hValue != null)
            {
            f_ahVar[nVar] = hValue;
            }
        }

    public VarInfo getVarInfo(int nVar)
        {
        VarInfo info = f_aInfo[nVar];
        if (info == null)
            {
            int cArgs;
            String sName;

            if (f_hTarget == null)
                {
                cArgs = f_function.m_cArgs;
                sName = "<arg " + nVar + ">";
                }
            else
                {
                cArgs = f_function.m_cArgs + 1;
                sName = nVar == 0 ? "<this>" : "<arg " + (nVar - 1) + ">";
                }

            if (nVar >= cArgs)
                {
                throw new IllegalStateException("Variable " + nVar + " ouf of scope " + f_function);
                }

            introduceVar(nVar, f_ahVar[nVar].f_clazz, sName, VAR_STANDARD, null);
            info = f_aInfo[nVar];
            }
        return info;
        }

    // construct-finally support
    public void chainFinalizer(FullyBoundHandle hFinalizer)
        {
        if (hFinalizer != null)
            {
            Frame frameTop = this;
            while (frameTop.m_hfnFinally == null)
                {
                frameTop = frameTop.f_framePrev;
                }
            frameTop.m_hfnFinally = hFinalizer.chain(frameTop.m_hfnFinally);
            }
        }

    // temporary
    public String getStackTrace()
        {
        StringBuilder sb = new StringBuilder();
        Frame frame = this;
        int iPC = m_iPC;

        while (true)
            {
            sb.append("\n  - ")
              .append(frame);

            if (iPC >= 0)
                {
                sb.append(" (iPC=").append(iPC)
                  .append(", op=").append(frame.f_aOp[iPC].getClass().getSimpleName())
                  .append(')');
                }

            frame = frame.f_framePrev;
            if (frame == null)
                {
                break;
                }
            iPC = frame.m_iPC - 1;
            }

        sb.append('\n');

        return sb.toString();
        }

    @Override
    public String toString()
        {
        InvocationTemplate fn = f_function;
        int iPC = m_iPC;

        return "Frame: " + (fn == null ? "<none>" :
                fn.getClazzTemplate().f_sName + '.' + f_function.f_sName);
        }

    // try-catch support
    public static class Guard
        {
        public final int f_nStartAddress;
        public final int f_nScope;
        public final int[] f_anClassConstId;
        public final int[] f_anNameConstId;
        public final int[] f_anCatchRelAddress;

        public Guard(int nStartAddr, int nScope, int[] anClassConstId,
                     int[] anNameConstId, int[] anCatchAddress)
            {
            f_nStartAddress = nStartAddr;
            f_nScope = nScope;
            f_anClassConstId = anClassConstId;
            f_anNameConstId = anNameConstId;
            f_anCatchRelAddress = anCatchAddress;
            }
        }

    // variable into (support for Refs and debugger)
    public static class VarInfo
        {
        public final TypeComposition f_clazz;
        public final String f_sVarName;
        public int m_nStyle; // one of the VAR_* values
        public RefHandle m_ref; // an "active" reference to this register

        public VarInfo(TypeComposition clazz, String sName, int nStyle)
            {
            f_clazz = clazz;
            f_sVarName = sName;
            m_nStyle = nStyle;
            }

        // this VarInfo goes out of scope
        public void release()
            {
            if (m_ref != null)
                {
                m_ref.dereference();
                }
            }
        }
    }
