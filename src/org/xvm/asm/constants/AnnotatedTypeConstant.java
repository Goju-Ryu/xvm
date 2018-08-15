package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import java.util.function.Consumer;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.runtime.AnnotationSupport;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.OpSupport;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.checkElementsNonNull;
import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents the annotation of another type constant.
 */
public class AnnotatedTypeConstant
        extends TypeConstant
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
    public AnnotatedTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_annotation = new Annotation(pool, in);
        m_iType      = readIndex(in);
        }

    /**
     * Construct a constant whose value is an annotated data type.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constClass   the class of the annotation
     * @param aconstParam  the parameters of the annotation, or null
     * @param constType    the type being annotated
     */
    public AnnotatedTypeConstant(ConstantPool pool, Constant constClass,
            Constant[] aconstParam, TypeConstant constType)
        {
        super(pool);

        if (constClass == null)
            {
            throw new IllegalArgumentException("annotation class required");
            }

        if (aconstParam != null)
            {
            checkElementsNonNull(aconstParam);
            }

        if (constType == null)
            {
            throw new IllegalArgumentException("annotated type required");
            }

        m_annotation = new Annotation(pool, constClass, aconstParam);
        m_constType  = constType;
        }

    /**
     * Construct a constant whose value is an annotated data type.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param annotation   the annotation
     * @param constType    the type being annotated
     */
    public AnnotatedTypeConstant(ConstantPool pool, Annotation annotation, TypeConstant constType)
        {
        super(pool);

        if (annotation == null)
            {
            throw new IllegalArgumentException("annotation required");
            }

        if (constType == null)
            {
            throw new IllegalArgumentException("annotated type required");
            }

        m_annotation = annotation;
        m_constType  = constType;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the annotation
     */
    public Annotation getAnnotation()
        {
        return m_annotation;
        }

    /**
     * Return the annotation type with any type parameters resolved that overlap with the
     * underlying TypeConstant.
     *
     * For example, an "@Atomic Var<Int>" type should yield AtomicVar<Int>.
     *
     * @return the resolved annotation type
     */
    public TypeConstant getAnnotationType()
        {
        Constant constAnno = m_annotation.getAnnotationClass();

        if (constAnno instanceof ClassConstant)
            {
            ClassStructure struct = (ClassStructure) ((ClassConstant) constAnno).getComponent();

            return struct.getFormalType().resolveGenerics(getConstantPool(), m_constType);
            }

        // REVIEW the only other option is the constAnno to be a PseudoConstant (referring to a virtual
        //        child / sibling that is a mix-in, so some form of "virtual annotation" that has not
        //        yet been defined / evaluated for inclusion in the language)

        return m_annotation.getAnnotationType();
        }

    /**
     * @return the class of the annotation
     */
    public Constant getAnnotationClass()
        {
        return m_annotation.getAnnotationClass();
        }

    /**
     * @return an array of constants which are the parameters for the annotation
     */
    public Constant[] getAnnotationParams()
        {
        return m_annotation.getParams();
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isModifyingType()
        {
        return true;
        }

    @Override
    public TypeConstant getUnderlyingType()
        {
        return m_constType;
        }

    @Override
    public boolean isAnnotated()
        {
        return true;
        }

    @Override
    public boolean isNullable()
        {
        return m_constType.isNullable();
        }

    @Override
    public TypeConstant removeNullable(ConstantPool pool)
        {
        return isNullable()
                ? pool.ensureAnnotatedTypeConstant(getAnnotationClass(),
                        getAnnotationParams(), m_constType.removeNullable(pool))
                : this;
        }

    @Override
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type)
        {
        return pool.ensureAnnotatedTypeConstant(m_annotation.getAnnotationClass(),
                m_annotation.getParams(), type);
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    public List<Component.ContributionChain> collectContributions(
            TypeConstant typeLeft, List<TypeConstant> listRight, List<Component.ContributionChain> chains)
        {
        // this logic is identical to the union of the annotation type and the underlying type
        assert listRight.isEmpty();

        TypeConstant typeAnno = getAnnotationType();
        TypeConstant typeOrig = getUnderlyingType();

        List<Component.ContributionChain> chains1 = typeAnno.collectContributions(typeLeft, listRight, new ArrayList<>());
        List<Component.ContributionChain> chains2 = typeOrig.collectContributions(typeLeft, new ArrayList<>(), new ArrayList<>());

        // any contribution would do
        if (!chains1.isEmpty())
            {
            validateChains(chains1, typeAnno, typeLeft);
            }

        if (!chains2.isEmpty())
            {
            validateChains(chains2, typeOrig, typeLeft);
            }

        chains.addAll(chains1);
        chains.addAll(chains2);

        return chains;
        }

    @Override
    protected List<Component.ContributionChain> collectClassContributions(
            ClassStructure clzRight, List<TypeConstant> listRight, List<Component.ContributionChain> chains)
        {
        // this logic is identical to the union of the annotation type and the underlying type
        assert listRight.isEmpty();

        TypeConstant typeAnno = getUnderlyingType();
        TypeConstant typeOrig = getUnderlyingType2();

        List<Component.ContributionChain> chains1 = typeAnno.collectClassContributions(clzRight, listRight, new ArrayList<>());
        List<Component.ContributionChain> chains2 = typeOrig.collectClassContributions(clzRight, new ArrayList<>(), new ArrayList<>());

        // both branches have to have contributions
        if (!chains1.isEmpty() && !chains2.isEmpty())
            {
            chains.addAll(chains1);
            chains.addAll(chains2);
            }

        return chains;
        }

    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public OpSupport getOpSupport(TemplateRegistry registry)
        {
        OpSupport support = m_support;
        if (support == null)
            {
            support = m_support = new AnnotationSupport(this, registry);
            }
        return support;
        }

    @Override
    public int callEquals(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return Utils.callEqualsSequence(frame,
            m_annotation.getAnnotationType(), m_constType, hValue1, hValue2, iReturn);
        }

    @Override
    public int callCompare(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return Utils.callCompareSequence(frame,
            m_annotation.getAnnotationType(), m_constType, hValue1, hValue2, iReturn);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.AnnotatedType;
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_annotation.containsUnresolved() || m_constType.containsUnresolved();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        m_annotation.forEachUnderlying(visitor);
        visitor.accept(m_constType);
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        AnnotatedTypeConstant that = (AnnotatedTypeConstant) obj;
        int n = this.m_annotation.compareTo(that.m_annotation);

        if (n == 0)
            {
            n = this.m_constType.compareTo(that.m_constType);
            }

        return n;
        }

    @Override
    public String getValueString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(m_annotation.toString())
          .append(' ')
          .append(m_constType.getValueString());

        return sb.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_annotation.disassemble(in);
        m_constType = (TypeConstant) getConstantPool().getConstant(m_iType);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_annotation.registerConstants(pool);
        m_constType = (TypeConstant) pool.register(m_constType);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        m_annotation.assemble(out);
        writePackedLong(out, indexOf(m_constType));
        }

    @Override
    public boolean validate(ErrorListener errs)
        {
        boolean fBad  = false;
        boolean fHalt = false;

        if (!isValidated())
            {
            fHalt |= super.validate(errs);

            // an annotated type constant can modify a parameterized or a terminal type constant
            // that refers to a class/interface
            TypeConstant typeNext = m_constType.resolveTypedefs();
            if (!(typeNext instanceof AnnotatedTypeConstant || typeNext.isExplicitClassIdentity(true)))
                {
                fHalt |= log(errs, Severity.ERROR, VE_ANNOTATION_ILLEGAL, typeNext.getValueString());
                fBad   = true;
                }

            // validate the annotation itself
            fHalt |= m_annotation.validate(errs);

            // make sure that this annotation is not repeated
            Constant constClass = m_annotation.getAnnotationClass();
            while (typeNext instanceof AnnotatedTypeConstant)
                {
                if (((AnnotatedTypeConstant) typeNext).m_annotation.getAnnotationClass().equals(constClass))
                    {
                    fHalt |= log(errs, Severity.ERROR, VE_ANNOTATION_REDUNDANT, constClass.getValueString());
                    fBad   = true;
                    break;
                    }

                typeNext = ((AnnotatedTypeConstant) typeNext).m_constType;
                }

            // verify that the underlying type can be annotated by this annotation
            TypeConstant typeMixin = getAnnotationType();
            if (!fBad && !fHalt && typeMixin.isExplicitClassIdentity(true)
                    && typeMixin.getExplicitClassFormat() == Component.Format.MIXIN)
                {
                TypeConstant typeInto = typeMixin.getExplicitClassInto();
                if (!m_constType.isA(typeInto))
                    {
                    fHalt |= log(errs, Severity.ERROR, VE_ANNOTATION_INCOMPATIBLE,
                            m_constType.getValueString(),
                            constClass.getValueString(),
                            typeInto.getValueString());
                    }
                }
            }

        return fHalt;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_annotation.hashCode() ^ m_constType.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The annotation.
     */
    private Annotation m_annotation;

    /**
     * During disassembly, this holds the index of the type constant of the type being annotated.
     */
    private int m_iType;

    /**
     * The type being annotated.
     */
    private TypeConstant m_constType;

    /**
     * Cached OpSupport reference.
     */
    private transient OpSupport m_support;
    }
