package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.IdentityConstant;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.template.xClass.ClassHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * NEW_1G CONST-CONSTRUCT, rvalue-type, rvalue-param, lvalue-return
 *
 * @author gg 2017.03.08
 */
public class New_0G extends OpCallable
    {
    private final int f_nConstructId;
    private final int f_nTypeValue;
    private final int f_nRetValue;

    public New_0G(int nConstructorId, int nType, int nRet)
        {
        f_nConstructId = nConstructorId;
        f_nTypeValue = nType;
        f_nRetValue = nRet;
        }

    public New_0G(DataInput in)
            throws IOException
        {
        f_nConstructId = in.readInt();
        f_nTypeValue = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_NEW_0G);
        out.writeInt(f_nConstructId);
        out.writeInt(f_nTypeValue);
        out.writeInt(f_nRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        MethodStructure constructor = getMethodStructure(frame, f_nConstructId);
        IdentityConstant constClass = constructor.getParent().getParent().getIdentityConstant();

        try
            {
            TypeComposition clzTarget;
            if (f_nTypeValue >= 0)
                {
                ClassHandle hClass = (ClassHandle) frame.getArgument(f_nTypeValue);
                if (hClass == null)
                    {
                    return R_REPEAT;
                    }
                clzTarget = hClass.f_clazz;
                }
            else
                {
                clzTarget = frame.f_context.f_types.ensureComposition(-f_nTypeValue);
                }

            ClassTemplate template = frame.f_context.f_types.getTemplate(constClass);

            ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(constructor)];

            return template.construct(frame, constructor, clzTarget, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }