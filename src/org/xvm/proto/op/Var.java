package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;
import org.xvm.proto.TypeComposition;

/**
 * VAR CONST_CLASS  ; (next register is an uninitialized anonymous variable)
 *
 * @author gg 2017.03.08
 */
public class Var extends Op
    {
    private final int f_nClassConstId;

    public Var(int nClassConstId)
        {
        f_nClassConstId = nClassConstId;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int iScope = frame.f_aiIndex[I_SCOPE];
        int nNextVar = frame.f_anNextVar[iScope];

        TypeComposition clazz = frame.f_context.f_types.ensureConstComposition(f_nClassConstId);
        frame.f_aInfo[nNextVar] = new Frame.VarInfo(clazz);

        frame.f_anNextVar[iScope] = nNextVar+1;
        return iPC + 1;
        }
    }
