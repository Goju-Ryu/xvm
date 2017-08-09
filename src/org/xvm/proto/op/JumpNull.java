package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;

import org.xvm.proto.template.xNullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * JMP_NULL rvalue, rel-addr ; jump if value is null
 *
 * @author gg 2017.03.08
 */
public class JumpNull extends Op
    {
    private final int f_nValue;
    private final int f_nRelAddr;

    public JumpNull(int nValue, int nRelAddr)
        {
        f_nValue = nValue;
        f_nRelAddr = nRelAddr;
        }

    public JumpNull(DataInput in)
            throws IOException
        {
        f_nValue = in.readInt();
        f_nRelAddr = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_JMP_NULL);
        out.writeInt(f_nValue);
        out.writeInt(f_nRelAddr);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTest = frame.getArgument(f_nValue);

            return hTest == xNullable.NULL ? iPC + f_nRelAddr : iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
