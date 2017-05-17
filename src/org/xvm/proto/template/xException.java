package org.xvm.proto.template;

import org.xvm.proto.*;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.op.Return_0;
import org.xvm.proto.op.PSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xException
        extends TypeCompositionTemplate
    {
    public static xException INSTANCE;

    public xException(TypeSet types)
        {
        super(types, "x:Exception", "x:Object", Shape.Const);

        addImplement("x:Const");

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        // @inject Iterable<StackFrame> stackTrace;
        ensurePropertyTemplate("text", "x:String");
        ensurePropertyTemplate("cause", "this.Type");
        ensurePropertyTemplate("stackTrace", "x:String"); // TODO: replace "x:String" with "x:Iterable<this.Type.StackFrame>"

        ConstructTemplate ct = ensureConstructTemplate(
                new String[]{"x:Exception", "x:String|x:Nullable", "x:Exception|x:Nullable"});

        ct.m_aop = new Op[] // #0 - this:struct, #1 - text, #2 - cause
            {
            new PSet(0, f_types.f_adapter.getPropertyConstId("x:Exception", "text"), 1),
            new PSet(0, f_types.f_adapter.getPropertyConstId("x:Exception", "cause"), 2),
            new Return_0(),
            };
        ct.m_cVars = 3;
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new ExceptionHandle(f_clazzCanonical, false, null);
        }

    @Override
    public ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        return makeHandle(null, null);
        }

    // ---- ObjectHandle helpers -----

    public static ExceptionHandle immutable()
        {
        return xException.makeHandle("Immutable object");
        }

    public static ExceptionHandle makeHandle(String sMessage)
        {
        ExceptionHandle hException = makeHandle(null, null);

        INSTANCE.setFieldValue(hException,
                INSTANCE.getPropertyTemplate("text"), xString.makeHandle(sMessage));

        return hException;
        }

    public static ExceptionHandle makeHandle(ExceptionHandle hCause, Throwable eCause)
        {
        ExceptionHandle hException = eCause == null ?
                new ExceptionHandle(INSTANCE.f_clazzCanonical, true, null) :
                new ExceptionHandle(INSTANCE.f_clazzCanonical, true, eCause);

        ServiceContext context = ServiceContext.getCurrentContext();
        Frame frame = context.getCurrentFrame();

        INSTANCE.setFieldValue(hException, INSTANCE.getPropertyTemplate("stackTrace"),
                xString.makeHandle(frame.getStackTrace()));
        INSTANCE.setFieldValue(hException, INSTANCE.getPropertyTemplate("cause"), hCause);

        return hException;
        }
    }
