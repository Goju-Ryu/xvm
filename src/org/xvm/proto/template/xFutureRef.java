package org.xvm.proto.template;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

import org.xvm.proto.template.xFunction.FunctionHandle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xFutureRef
        extends xRef
    {
    public static xFutureRef INSTANCE;

    public xFutureRef(TypeSet types)
        {
        super(types, "x:FutureRef<RefType>", "x:Ref", Shape.Mixin);

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        //    enum Completion {Pending, Result, Error};
        //    public/private Completion completion = Pending;
        //    private Boolean assignable = false;
        //    private Exception? failure = null;
        //    typedef function Void (Completion, RefType?, Exception?) NotifyDependent;
        //    private NotifyDependent? notify = null;

        // FutureRef.Type<RefType> whenComplete(function Void (RefType?, Exception?) notify)
        MethodTemplate mtWC = ensureMethodTemplate("whenComplete", new String[] {"x:Function"}, THIS);
        mtWC.markNative();
        }

    @Override
    public int invokeNative(Frame frame, ObjectHandle hTarget,
                            MethodTemplate method, ObjectHandle hArg, int iReturn)
        {
        FutureHandle hThis = (FutureHandle) hTarget;

        switch (method.f_sName)
            {
            case "whenComplete":
                FunctionHandle hNotify = (FunctionHandle) hArg;

                CompletableFuture<ObjectHandle> cf = hThis.m_future.whenComplete((r, x) ->
                    {
                    ObjectHandle[] ahArg = new ObjectHandle[2];
                    ahArg[0] = r;
                    ahArg[1] = x == null ? xNullable.NULL :
                                ((ExceptionHandle.WrapperException) x).getExceptionHandle();

                    frame.f_context.callLater(hNotify, ahArg);
                    });

                return frame.assignValue(iReturn, makeHandle(cf));
            }

        return super.invokeNative(frame, hTarget, method, hArg, iReturn);
        }

    @Override
    public RefHandle createRefHandle(TypeComposition clazz)
        {
        return new FutureHandle(clazz, null, false);
        }

    public static class FutureHandle
            extends RefHandle
        {
        public final boolean f_fSynthetic;
        public CompletableFuture<ObjectHandle> m_future;
        protected Frame m_frameNext;

        protected FutureHandle(TypeComposition clazz,
                               CompletableFuture<ObjectHandle> future, boolean fSynthetic)
            {
            super(clazz);

            f_fSynthetic = fSynthetic;
            m_future = future;
            }

        @Override
        protected ObjectHandle getInternal()
                throws ExceptionHandle.WrapperException
            {
            CompletableFuture<ObjectHandle> cf = m_future;
            if (cf.isDone())
                {
                try
                    {
                    return cf.get();
                    }
                catch (InterruptedException e)
                    {
                    throw new UnsupportedOperationException("TODO");
                    }
                catch (ExecutionException e)
                    {
                    Throwable eOrig = e.getCause();
                    if (eOrig instanceof ExceptionHandle.WrapperException)
                        {
                        throw (ExceptionHandle.WrapperException) eOrig;
                        }
                    throw new UnsupportedOperationException("Unexpected exception", eOrig);
                    }
                }
            else
                {
                // wait for the completion;
                // the service is responsible for timing out
                return null;
                }
            }

        @Override
        protected ExceptionHandle setInternal(ObjectHandle handle)
            {
            if (handle instanceof FutureHandle)
                {
                // this is only possible if this "handle" is a "dynamic ref" and the "handle" is
                // is a synthetic one (see Frame.assignValue)
                if (m_future != null)
                    {
                    return xException.makeHandle("Future has already been assigned");
                    }

                FutureHandle that = (FutureHandle) handle;
                assert that.f_fSynthetic;

                m_future = that.m_future.whenComplete((r, x) -> this.m_frameNext = that.m_frameNext);
                return null;
                }

            if (m_future.isDone())
                {
                return xException.makeHandle("Future has already been set");
                }

            m_future.complete(handle);

            return null;
            }

        @Override
        public String toString()
            {
            return "(" + f_clazz + ") " + (
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
            catch (Exception e)
                {
                return e.toString();
                }
            }
        }

    public static FutureHandle makeHandle(CompletableFuture<ObjectHandle> future)
        {
        return new FutureHandle(INSTANCE.f_clazzCanonical, future, false);
        }

    public static FutureHandle makeSyntheticHandle(CompletableFuture<ObjectHandle> future)
        {
        return new FutureHandle(INSTANCE.f_clazzCanonical, future, true);
        }
    }
