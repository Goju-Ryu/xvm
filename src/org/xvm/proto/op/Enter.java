package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

import java.io.DataOutput;
import java.io.IOException;

/**
 * ENTER ; (variable scope begin)
 *
 * @author gg 2017.03.08
 */
public class Enter extends Op
    {
    public Enter()
        {
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_ENTER);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        frame.enterScope();

        return iPC + 1;
        }
    }
