package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.compiler.Token;

import org.xvm.runtime.ObjectHandle;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a singleton instance of a const class (such as an enum value) as a constant value.
 */
public class SingletonConstant
        extends ValueConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a literal.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param format
     * @param constClass  the class constant for the singleton value
     */
    public SingletonConstant(ConstantPool pool, Format format, IdentityConstant constClass)
        {
        super(pool);

        if (format != Format.SingletonConst && format != Format.SingletonService)
            {
            throw new IllegalArgumentException("format must be SingletonConst or SingletonService");
            }

        if (constClass == null)
            {
            throw new IllegalArgumentException("class of the singleton value required");
            }

        m_fmt        = format;
        m_constClass = constClass;
        }

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public SingletonConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_fmt    = format;
        m_iClass = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        m_constClass = (IdentityConstant) getConstantPool().getConstant(m_iClass);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    @Override
    public TypeConstant getType()
        {
        return m_constClass.getType();
        }

    /**
     * {@inheritDoc}
     * @return  the class constant for the singleton value
     */
    @Override
    public IdentityConstant getValue()
        {
        return m_constClass;
        }


    // ----- run-time support  ---------------------------------------------------------------------

    /**
     * @return an ObjectHandle representing this singleton value
     */
    public ObjectHandle getHandle()
        {
        return m_handle;
        }

    /**
     * Set the handle for this singleton's value.
     *
     * @param handle  the corresponding handle
     */
    public void setHandle(ObjectHandle handle)
        {
        // the only scenario when the singleton value can be reset is when it turns from
        // a struct to an immutable
        assert handle != null;

        if (m_handle != null && handle != m_handle)
            {
            assert m_handle.isStruct() && !handle.isStruct();
            }

        m_handle        = handle;
        m_fInitializing = false;
        }

    /**
     * Mark this ObjectHandle as being initialized.
     *
     * @return false iff the ObjectHandle has already been marked as "initializing"
     */
    public boolean markInitializing()
        {
        if (m_fInitializing)
            {
            return false;
            }
        return m_fInitializing = true;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return m_fmt;
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_constClass.containsUnresolved();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constClass);
        }

    @Override
    public SingletonConstant resolveTypedefs()
        {
        IdentityConstant constOld = m_constClass;
        IdentityConstant constNew = (IdentityConstant) constOld.resolveTypedefs();
        return constNew == constOld
                ? this
                : (SingletonConstant) getConstantPool().register(
                        new SingletonConstant(getConstantPool(), m_fmt, constNew));
        }

    @Override
    public Constant apply(Token.Id op, Constant that)
        {
        if (op.equals(Token.Id.DOTDOT) && that instanceof SingletonConstant)
            {
            IdentityConstant idThis     = this.getValue();
            IdentityConstant idThat     = ((SingletonConstant) that).getValue();
            ClassStructure   structThis = (ClassStructure) idThis.getComponent();
            ClassStructure   structThat = (ClassStructure) idThat.getComponent();

            if (structThis.getFormat() == Component.Format.ENUMVALUE &&
                structThat.getFormat() == Component.Format.ENUMVALUE)
                {
                return getConstantPool().ensureRangeConstant(this, that);
                }
            }

        return super.apply(op, that);
        }

    @Override
    public Object getLocator()
        {
        return getValue();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof SingletonConstant))
            {
            return -1;
            }
        return this.getValue().compareTo(((SingletonConstant) that).getValue());
        }

    @Override
    public String getValueString()
        {
        return m_constClass.getName();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constClass = (IdentityConstant) pool.register(m_constClass);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constClass.getPosition());
        }

    @Override
    public String getDescription()
        {
        return "singleton-" + (m_fmt == Format.SingletonConst ? "const=" : "service=") + m_constClass.getName();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constClass.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The format of the constant; either SingletonConst or SingletonService.
     */
    private Format m_fmt;

    /**
     * Used during deserialization: holds the index of the class constant.
     */
    private transient int m_iClass;

    /**
     * The IdentityConstant for the class of the singleton value.
     */
    private IdentityConstant m_constClass;

    /**
     * The ObjectHandle representing this singleton's value.
     */
    private transient ObjectHandle m_handle;

    /**
     * Set to true when the handle for this singleton is being initialized.
     */
    private transient boolean m_fInitializing;
    }
