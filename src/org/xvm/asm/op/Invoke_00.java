package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;


/**
 * NVOK_00 rvalue-target, rvalue-method
 */
public class Invoke_00
        extends OpInvocable
    {
    /**
     * Construct an NVOK_00 op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     */
    public Invoke_00(Argument argTarget, MethodConstant constMethod)
        {
        super(argTarget, constMethod);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_00(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_NVOK_00;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);

            if (isDeferred(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller -> complete(frameCaller, ahTarget[0]);

                return new Utils.GetArguments(ahTarget, stepNext).doNext(frame);
                }

            return complete(frame, hTarget);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, ObjectHandle hTarget)
        {
        return getCallChain(frame, hTarget).invoke(frame, hTarget, A_IGNORE);
        }
    }
