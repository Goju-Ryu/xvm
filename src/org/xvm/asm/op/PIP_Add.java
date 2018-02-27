package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;

import org.xvm.asm.OpPropInPlaceAssign;
import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * PIP_ADD PROPERTY, rvalue-target, rvalue2 ; T += T
 */
public class PIP_Add
        extends OpPropInPlaceAssign
    {
    /**
     * Construct a PIP_ADD op based on the passed arguments.
     *
     * @param constProperty  the property constant
     * @param argTarget      the target Argument
     * @param argValue       the value Argument
     */
    public PIP_Add(PropertyConstant constProperty, Argument argTarget, Argument argValue)
        {
        super(constProperty, argTarget, argValue);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PIP_Add(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_PIP_ADD;
        }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hValue)
        {
        return hTarget.getTemplate().invokeAdd(frame, hTarget, sPropName, hValue);
        }
    }