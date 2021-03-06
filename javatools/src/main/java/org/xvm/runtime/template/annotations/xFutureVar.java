package org.xvm.runtime.template.annotations;


import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle.WrapperException;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;

import org.xvm.runtime.template.reflect.xVar;


/**
 * FutureVar native implementation.
 */
public class xFutureVar
        extends xVar
    {
    public static xFutureVar INSTANCE;
    public static TypeConstant TYPE;
    public static xEnum COMPLETION;

    public xFutureVar(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        ConstantPool  pool     = pool();
        ClassConstant idMixin  = (ClassConstant) f_struct.getIdentityConstant();
        Annotation    anno     = pool.ensureAnnotation(idMixin);
        TypeConstant  typeVar  = xVar.INSTANCE.getCanonicalType();
        TYPE = pool.ensureAnnotatedTypeConstant(typeVar, anno);

        COMPLETION = (xEnum) f_templates.getTemplate("annotations.FutureVar.Completion");

        markNativeMethod("whenComplete", null, null);
        markNativeMethod("thenDo", null, null);
        markNativeMethod("passTo", null, null);

        markNativeMethod("get", VOID, null);
        markNativeMethod("set", null, VOID);

        markNativeMethod("completeExceptionally", null, VOID);

        markNativeProperty("assigned");
        markNativeProperty("completion");

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        return TYPE;
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        FutureHandle hThis = (FutureHandle) hTarget;
        CompletableFuture cf = hThis.m_future;

        switch (sPropName)
            {
            case "assigned":
                return frame.assignValue(iReturn, xBoolean.makeHandle(cf != null && cf.isDone()));

            case "failure":
                {
                if (cf != null && cf.isCompletedExceptionally())
                    {
                    try
                        {
                        cf.get();
                        throw new IllegalStateException(); // cannot happen
                        }
                    catch (Exception e)
                        {
                        Throwable eOrig = e.getCause();
                        if (eOrig instanceof WrapperException)
                            {
                            ExceptionHandle hException =
                                    ((WrapperException) eOrig).getExceptionHandle();
                            return frame.assignValue(iReturn, hException);
                            }
                        throw new UnsupportedOperationException("Unexpected exception", e);
                        }
                    }
                return frame.assignValue(iReturn, xNullable.NULL);
                }

            case "completion":
                {
                EnumHandle hValue =
                    cf == null || !cf.isDone() ?
                        COMPLETION.getEnumByName("Pending") :
                    cf.isCompletedExceptionally() ?
                        COMPLETION.getEnumByName("Error") :
                        COMPLETION.getEnumByName("Result");
                return Utils.assignInitializedEnum(frame, hValue, iReturn);
                }

            case "notify":
                // currently unused
                return frame.assignValue(iReturn, xNullable.NULL);

            }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        FutureHandle hThis = (FutureHandle) hTarget;

        switch (method.getName())
            {
            case "completeExceptionally":
                {
                ExceptionHandle hException = (ExceptionHandle) hArg;

                hThis.m_future.completeExceptionally(hException.getException());

                return Op.R_NEXT;
                }

            case "thenDo":
                {
                FunctionHandle hRun = (FunctionHandle) hArg;

                CompletableFuture<ObjectHandle> cf = hThis.m_future;
                if (cf.isDone())
                    {
                    ObjectHandle[] ahArg = extractResult(frame, cf);
                    if (ahArg[1] != xNullable.NULL)
                        {
                        // the future terminated exceptionally; re-throw it
                        return frame.raiseException((ExceptionHandle) ahArg[1]);
                        }

                    switch (hRun.call1(frame, null, Utils.OBJECTS_NONE, Op.A_IGNORE))
                        {
                        case Op.R_NEXT:
                            // we can return the same handle since it's completed
                            return frame.assignValue(iReturn, hThis);

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                frameCaller.assignValue(iReturn, hThis));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                else
                    {
                    CompletableFuture cf0 = cf.thenRun(() ->
                        frame.f_context.callLater(hRun, Utils.OBJECTS_NONE, false));
                    return frame.assignValue(iReturn, makeHandle(cf0));
                    }
                }

            case "passTo":
                {
                FunctionHandle hConsume = (FunctionHandle) hArg;

                CompletableFuture<ObjectHandle> cf = hThis.m_future;
                if (cf.isDone())
                    {
                    ObjectHandle[] ahArg = extractResult(frame, cf);
                    if (ahArg[1] != xNullable.NULL)
                        {
                        // the future terminated exceptionally; re-throw it
                        return frame.raiseException((ExceptionHandle) ahArg[1]);
                        }

                    ahArg[1] = null; // the consumer doesn't need it

                    switch (hConsume.call1(frame, null, ahArg, Op.A_IGNORE))
                        {
                        case Op.R_NEXT:
                            // we can return the same handle since it's completed
                            return frame.assignValue(iReturn, hThis);

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                frameCaller.assignValue(iReturn, hThis));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                else
                    {
                    CompletableFuture cf0 = cf.thenAccept(r ->
                        {
                        ObjectHandle[] ahArg = new ObjectHandle[1];
                        ahArg[0] = r;
                        frame.f_context.callLater(hConsume, ahArg, false);
                        });

                    return frame.assignValue(iReturn, makeHandle(cf0));
                    }
                }

            case "whenComplete":
                {
                FunctionHandle hNotify = (FunctionHandle) hArg;

                CompletableFuture<ObjectHandle> cf = hThis.m_future;
                if (cf.isDone())
                    {
                    ObjectHandle[] ahArg = extractResult(frame, cf);

                    switch (hNotify.call1(frame, null, ahArg, Op.A_IGNORE))
                        {
                        case Op.R_NEXT:
                            // we can return the same handle since it's completed
                            return frame.assignValue(iReturn, hThis);

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                frameCaller.assignValue(iReturn, hThis));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                else
                    {
                    cf = cf.whenComplete((r, x) ->
                        frame.f_context.callLater(hNotify, combineResult(r, x), false));

                    return frame.assignValue(iReturn, makeHandle(cf));
                    }
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    /**
     * Extract the result of the completed future into an ObjectHandle array.
     *
     *
     * @param frame  the current frame
     * @param cf     the future
     *
     * @return an ObjectHandle array containing the result and the exception handles
     */
    protected ObjectHandle[] extractResult(Frame frame, CompletableFuture<ObjectHandle> cf)
        {
        ObjectHandle[] ahArg = new ObjectHandle[2];
        try
            {
            ahArg[0] = cf.get();
            ahArg[1] = xNullable.NULL;
            }
        catch (Throwable e)
            {
            ExceptionHandle hException;
            if (e instanceof ExecutionException)
                {
                hException = ((WrapperException) e.getCause()).getExceptionHandle();
                }
            else if (e instanceof CancellationException)
                {
                hException = xException.makeHandle(frame, "cancelled");
                }
            else
                {
                hException = xException.makeHandle(frame, "interrupted");
                }
            ahArg[0] = xNullable.NULL;
            ahArg[1] = hException;
            }
        return ahArg;
        }

    /**
     * Combine the result of the completed future phase into an ObjectHandle array.
     *
     * @param hResult  the execution result
     * @param exception        an exception
     *
     * @return an ObjectHandle array containing the result and the exception handles
     */
    protected ObjectHandle[] combineResult(ObjectHandle hResult, Throwable exception)
        {
        ObjectHandle[] ahArg = new ObjectHandle[2];
        ahArg[0] = hResult == null
                ? xNullable.NULL
                : hResult;
        ahArg[1] = exception == null
                ? xNullable.NULL
                : ((WrapperException) exception).getExceptionHandle();
        return ahArg;
        }

    @Override
    public RefHandle createRefHandle(Frame frame, TypeComposition clazz, String sName)
        {
        // the future for a property needs to be initialized
        return new FutureHandle(clazz, sName,
            frame == null ? new CompletableFuture<>() : null);
        }

    @Override
    protected int invokeNativeGetReferent(Frame frame, RefHandle hRef, int iReturn)
        {
        return invokeGetReferent(frame, hRef, iReturn);
        }

    @Override
    protected int invokeGetReferent(Frame frame, RefHandle hRef, int iReturn)
        {
        FutureHandle hFuture = (FutureHandle) hRef;

        CompletableFuture<ObjectHandle> cf = hFuture.m_future;
        if (cf == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        if (cf.isDone())
            {
            return assignDone(frame, cf, iReturn);
            }

        // wait for the assignment/completion; the service is responsible for timing out
        return frame.call(Utils.createWaitFrame(frame, cf, iReturn));
        }

    @Override
    protected int invokeSetReferent(Frame frame, RefHandle hTarget, ObjectHandle hValue)
        {
        FutureHandle hFuture = (FutureHandle) hTarget;

        assert hValue != null;

        if (hValue instanceof FutureHandle)
            {
            // this is only possible if this "handle" is a "dynamic ref" and the passed in
            // "handle" is a synthetic or dynamic one (see Frame.assignValue)

            FutureHandle that = (FutureHandle) hValue;
            if (that.m_future == null)
                {
                return frame.raiseException(xException.unassignedReference(frame));
                }

            if (hFuture.m_future == null)
                {
                hFuture.m_future = that.m_future;
                }
            else
                {
                // "connect" the futures
                hFuture.m_future.whenComplete((r, e) ->
                    {
                    if (e == null)
                        {
                        that.m_future.complete(r);
                        }
                    else
                        {
                        that.m_future.completeExceptionally(e);
                        }
                    });
                }
            return Op.R_NEXT;
            }

        CompletableFuture cf = hFuture.m_future;
        if (cf == null)
            {
            hFuture.m_future = CompletableFuture.completedFuture(hValue);
            return Op.R_NEXT;
            }

        if (cf.isDone())
            {
            return frame.raiseException("FutureVar has already been set");
            }

        cf.complete(hValue);
        return Op.R_NEXT;
        }

    /**
     * Helper method to assign a value of a completed future to a frame's register.
     *
     * @param frame    the current frame
     * @param cf       a future
     * @param iReturn  the register id to place the result to
     *
     * @return one of R_NEXT, R_CALL or R_EXCEPTION
     */
    public static int assignDone(Frame frame, CompletableFuture<ObjectHandle> cf, int iReturn)
        {
        assert cf.isDone();

        try
            {
            // services may replace "null" elements of a negative conditional return
            // with the DEFAULT values (see ServiceContext.sendResponse)
            ObjectHandle hValue = cf.get();
            return hValue == ObjectHandle.DEFAULT
                ? Op.R_NEXT
                : frame.assignValue(iReturn, hValue);
            }
        catch (InterruptedException e)
            {
            throw new UnsupportedOperationException("TODO");
            }
        catch (ExecutionException e)
            {
            Throwable eOrig = e.getCause();
            if (eOrig instanceof WrapperException)
                {
                return frame.raiseException((WrapperException) eOrig);
                }
            throw new UnsupportedOperationException("Unexpected exception", eOrig);
            }
        }


    // ----- the handle -----

    public static class FutureHandle
            extends RefHandle
        {
        public CompletableFuture<ObjectHandle> m_future;

        protected FutureHandle(TypeComposition clazz, String sName, CompletableFuture<ObjectHandle> future)
            {
            super(clazz, sName);

            m_future = future;
            }

        @Override
        public boolean isAssigned()
            {
            return m_future != null && m_future.isDone();
            }

        @Override
        public ObjectHandle getReferent()
            {
            // never called
            throw new IllegalStateException();
            }

        /**
         * @return true iff the future represented by this handle completed normally
         */
        public boolean isCompletedNormally()
            {
            return m_future != null && m_future.isDone() && !m_future.isCompletedExceptionally();
            }

        /**
         * Wait for the future completion and assign the specified register to the result.
         *
         * @param frame    the current frame
         * @param iReturn  the register id
         *
         * @return R_NEXT, R_CALL, R_EXCEPTION
         */
        public int waitAndAssign(Frame frame, int iReturn)
            {
            CompletableFuture<ObjectHandle> cf = m_future;
            if (cf == null)
                {
                // since this ref can only be changed by this service,
                // we can safely add a completable future now
                cf = m_future = new CompletableFuture();
                }
            else if (cf.isDone())
                {
                return assignDone(frame, cf, iReturn);
                }

            // add a notification and wait for the assignment/completion;
            // the service is responsible for timing out
            cf.whenComplete(
                (r, x) -> frame.f_fiber.onResponse());

            return frame.call(Utils.createWaitFrame(frame, cf, iReturn));
            }

        @Override
        public String toString()
            {
            return "(" + m_clazz + ") " + (
                    m_future == null ?  "Unassigned" :
                    m_future.isDone() ? "Completed: "  + toSafeString():
                                        "Not completed"
                    );
            }

        private String toSafeString()
            {
            try
                {
                return String.valueOf(m_future.get());
                }
            catch (Throwable e)
                {
                if (e instanceof ExecutionException)
                    {
                    e = e.getCause();
                    }
                return e.toString();
                }
            }
        }

    public static FutureHandle makeHandle(CompletableFuture<ObjectHandle> future)
        {
        return new FutureHandle(INSTANCE.getCanonicalClass(), null, future);
        }
    }
