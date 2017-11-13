package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent an interval of two constant values.
 */
public class IntervalConstant
        extends ValueConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public IntervalConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iVal1 = readMagnitude(in);
        m_iVal2 = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is an interval or range.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param const1  the value of the first constant
     * @param const2  the value of the second constant
     */
    public IntervalConstant(ConstantPool pool, Constant const1, Constant const2)
        {
        super(pool);

        if (const1 == null)
            {
            throw new IllegalArgumentException("value 1 required");
            }
        if (const2 == null)
            {
            throw new IllegalArgumentException("value 2 required");
            }
        if (const1.getFormat() != const2.getFormat() && !const1.getType().equals(const2.getType()))
            {
            throw new IllegalArgumentException("values must be of the same type");
            }

        m_const1 = const1;
        m_const2 = const2;
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    @Override
    public TypeConstant getType()
        {
        ConstantPool pool = getConstantPool();

        TypeConstant typeInterval;
        switch (m_const1.getFormat())
            {
            case IntLiteral:
            case Bit:
            case Nibble:
            case Int8:
            case Int16:
            case Int32:
            case Int64:
            case Int128:
            case VarInt:
            case UInt8:
            case UInt16:
            case UInt32:
            case UInt64:
            case UInt128:
            case VarUInt:
            case Date:
            case Char:
            case SingletonConst:
                typeInterval = pool.typeRange();
                break;

            default:
                typeInterval = pool.typeInterval();
                break;
            }

        return pool.ensureImmutableTypeConstant(
                pool.ensureParameterizedTypeConstant(typeInterval, m_const1.getType()));
        }

    /**
     * {@inheritDoc}
     * @return  the constant's contents as an array of two constants
     */
    @Override
    public Constant[] getValue()
        {
        return new Constant[] {m_const1, m_const2};
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Interval;
        }

    @Override
    public Constant simplify()
        {
        m_const1 = m_const1.simplify();
        m_const1 = m_const2.simplify();
        return this;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_const1);
        visitor.accept(m_const2);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        int nResult = this.m_const1.compareTo(((IntervalConstant) that).m_const1);
        if (nResult == 0)
            {
            nResult = this.m_const2.compareTo(((IntervalConstant) that).m_const2);
            }
        return nResult;
        }

    @Override
    public String getValueString()
        {
        return m_const1.getValueString() + ".." + m_const2.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        final ConstantPool pool = getConstantPool();
        m_const1 = pool.getConstant(m_iVal1);
        m_const2 = pool.getConstant(m_iVal2);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_const1 = pool.register(m_const1);
        m_const2 = pool.register(m_const2);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_const1.getPosition());
        writePackedLong(out, m_const2.getPosition());
        }

    @Override
    public String getDescription()
        {
        return "lower=" + m_const1.getValueString() + ", upper=" + m_const2.getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_const1.hashCode() ^ m_const2.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Holds the index of the first value during deserialization.
     */
    private transient int m_iVal1;

    /**
     * Holds the index of the second value during deserialization.
     */
    private transient int m_iVal2;

    /**
     * The first value of the interval.
     */
    private Constant m_const1;

    /**
     * The second value of the interval.
     */
    private Constant m_const2;
    }

