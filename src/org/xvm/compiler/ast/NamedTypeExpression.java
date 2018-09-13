package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ResolvableConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;

import static org.xvm.compiler.Lexer.isValidQualifiedModule;


/**
 * A type expression specifies a named type with optional parameters.
 */
public class NamedTypeExpression
        extends TypeExpression
        implements NameResolver.NameResolving
    {
    // ----- constructors --------------------------------------------------------------------------

    public NamedTypeExpression(Token immutable, List<Token> names, Token access, Token nonnarrow,
            List<TypeExpression> params, long lEndPos)
        {
        this.immutable  = immutable;
        this.names      = names;
        this.access     = access;
        this.nonnarrow  = nonnarrow;
        this.paramTypes = params;
        this.lEndPos    = lEndPos;

        this.m_resolver = new NameResolver(this, names.stream().map(
                token -> (String) token.getValue()).iterator());
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Assemble the qualified name.
     *
     * @return the dot-delimited name
     */
    public String getName()
        {
        StringBuilder sb = new StringBuilder();

        boolean first = true;
        for (Token name : names)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append('.');
                }
            sb.append(name.getValue());
            }

        return sb.toString();
        }

    public String[] getNames()
        {
        List<Token> list = names;
        int         c    = list.size();
        String[]    as   = new String[c];
        for (int i = 0; i < c; ++i)
            {
            as[i] = list.get(i).getValueText();
            }
        return as;
        }

    public Constant getIdentityConstant()
        {
        Constant constId = m_constId;
        if (constId == null)
            {
            m_constId = constId = new UnresolvedNameConstant(pool(), getNames(), isExplicitlyNonAutoNarrowing());
            }
        else if (constId instanceof ResolvableConstant)
            {
            m_constId = constId = ((ResolvableConstant) constId).unwrap();
            }
        return constId;
        }

    protected void setIdentityConstant(Constant constId)
        {
        if (constId instanceof ResolvableConstant)
            {
            constId = ((ResolvableConstant) constId).unwrap();
            }
        m_constId = constId;
        }

    public List<TypeExpression> getParamTypes()
        {
        return paramTypes;
        }

    /**
     * @return null if no access is explicit; otherwise one of PUBLIC, PROTECTED, PRIVATE
     */
    public Access getExplicitAccess()
        {
        if (access == null)
            {
            return null;
            }

        switch (access.getId())
            {
            case PUBLIC:
                return Access.PUBLIC;
            case PROTECTED:
                return Access.PROTECTED;
            case PRIVATE:
                return Access.PRIVATE;
            case STRUCT:
                return Access.STRUCT;
            default:
                throw new IllegalStateException("access=" + access);
            }
        }

    /**
     * @return true iff the type is explicitly non-auto-narrowing (the '!' post-operator)
     */
    public boolean isExplicitlyNonAutoNarrowing()
        {
        return nonnarrow != null;
        }

    /**
     * Auto-narrowing is allowed for a type used in the following scenarios:
     * <ul>
     * <li>Type of a property;</li>
     * <li>Type of a method parameter;</li>
     * <li>Type of a method return value;</li>
     * <li>TODO</li>
     * </ul>
     *
     * @return true iff this type is used as part of a property type, a method return type, a method
     *         parameter type,
     */
    public boolean isAutoNarrowingAllowed()
        {
        TypeExpression type   = this;
        AstNode        parent = getParent();
        while (!(parent instanceof Statement))
            {
            if (parent instanceof TypeExpression)
                {
                type = (TypeExpression) parent;
                }

            parent = parent.getParent();
            }

        // REVIEW GG - do you want this or parent.isComponentNode()
        return parent instanceof ComponentStatement
                && ((ComponentStatement) parent).isAutoNarrowingAllowed(type);
        }

    /**
     * @return the constant to use
     */
    public Constant inferAutoNarrowing(Constant constId)
        {
        // check for auto-narrowing
        if (!constId.containsUnresolved() && isAutoNarrowingAllowed() != isExplicitlyNonAutoNarrowing())
            {
            if (isExplicitlyNonAutoNarrowing())
                {
                throw new IllegalStateException("log error: auto-narrowing override ('!') unexpected");
                }

            if (constId instanceof ClassConstant) // isAutoNarrowingAllowed()
                {
                ClassStructure struct = getComponent().getContainingClass();
                if (struct != null)
                    {
                    ClassConstant constThisClass = (ClassConstant) struct.getIdentityConstant();
                    return constThisClass.calculateAutoNarrowingConstant((ClassConstant) constId);
                    }
                }
            }

        return constId;
        }

    /**
     * Determine if this NamedTypeExpression could be a module name.
     *
     * @return true iff this NamedTypeExpression is just a name, and that name is a legal name for
     *         a module
     */
    public boolean isValidModuleName()
        {
        return immutable == null && access == null && (paramTypes == null || paramTypes.isEmpty())
                && isValidQualifiedModule(getName());
        }

    @Override
    public long getStartPosition()
        {
        return immutable == null ? names.get(0).getStartPosition() : immutable.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- NameResolving methods -----------------------------------------------------------------

    @Override
    public NameResolver getNameResolver()
        {
        return m_resolver;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant()
        {
        Constant             constId    = getIdentityConstant();
        Access               access     = getExplicitAccess();
        List<TypeExpression> paramTypes = this.paramTypes;

        if (constId instanceof TypeConstant)
            {
            // access needs to be null
            if (access != null)
                {
                throw new IllegalStateException("log error: access override unexpected");
                }

            // must be no type params
            if (paramTypes != null && !paramTypes.isEmpty())
                {
                throw new IllegalStateException("log error: type params unexpected");
                }

            return (TypeConstant) constId;
            }

        // constId has been already "auto-narrowed" by resolveNames()
        ConstantPool pool = pool();
        TypeConstant type = pool.ensureTerminalTypeConstant(constId);

        if (paramTypes != null)
            {
            int            cParams      = paramTypes.size();
            TypeConstant[] aconstParams = new TypeConstant[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                aconstParams[i] = paramTypes.get(i).ensureTypeConstant();
                }
            type = pool.ensureParameterizedTypeConstant(type, aconstParams);
            }

        if (access != null && access != Access.PUBLIC)
            {
            type = pool.ensureAccessTypeConstant(type, access);
            }

        if (immutable != null)
            {
            type = pool.ensureImmutableTypeConstant(type);
            }

        return type;
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        NameResolver resolver = getNameResolver();
        switch (resolver.resolve(errs))
            {
            case DEFERRED:
                mgr.requestRevisit();
                return;

            case RESOLVED:
                if (!mgr.processChildren())
                    {
                    mgr.requestRevisit();
                    return;
                    }

                // now that we have the resolved constId, update the unresolved m_constId to point to
                // the resolved one (just in case anyone is holding the wrong one
                Constant constId = inferAutoNarrowing(resolver.getConstant());
                if (m_constId instanceof ResolvableConstant)
                    {
                    ((ResolvableConstant) m_constId).resolve(constId);
                    }

                // store the resolved id
                m_constId = constId;
                ensureTypeConstant();
            }
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool pool = pool();

        boolean fValid = true;

        TypeConstant type = pool.ensureTerminalTypeConstant(m_constId);
        if (paramTypes == null)
            {
            // in a context of "this compilation unit", an absence of type parameters
            // is treated as "formal types" (but only for instance children).
            // Consider an example:
            //
            //  class Parent<T0>
            //    {
            //    void foo()
            //       {
            //       Parent p;  // (1) means Parent<T0>
            //       Child1 c1; // (2) means Child1<Parent<T0>>
            //       Child3 c2; // (3) means Child2<Parent<T0>, ?>
            //       Child2 c3; // (3) means naked Child3 type
            //       }
            //
            //    class Child1
            //      {
            //      void foo()
            //         {
            //         Parent p;  // (4) means Parent<T0>
            //         Child1 c1; // (5) means Child1<Parent<T0>>
            //         Child2 c2; // (6) means Child2<Parent<T0>, ?>
            //         }
            //      }
            //
            //    class Child2<T2>
            //      {
            //      void foo()
            //         {
            //         Parent p;  // (4) means Parent<T0>
            //         Child2 c2; // (5) means Child1<Parent<T0>, T2>
            //         Child1 c1; // (7) means Child1<Parent<T0>>
            //         }
            //      }
            //
            //    static class Child3<T3>
            //      {
            //      void foo()
            //         {
            //         Parent p; // (8) means "naked" Parent type
            //         Child3 c; // (9) means Child3<T3>
            //         }
            //      }
            //    }
            //

            if (m_constId.getFormat() == Format.Class)
                {
                IdentityConstant idTarget  = (IdentityConstant) m_constId;
                ClassStructure   clzTarget = (ClassStructure) idTarget.getComponent();
                if (clzTarget.isParameterized())
                    {
                    ClassStructure   clzClass   = ctx.getThisClass();
                    IdentityConstant idClass    = clzClass.getIdentityConstant();
                    boolean          fUseFormal = false;

                    if (idTarget.equals(idClass))
                        {
                        // scenarios (1), (5) and (9)
                        fUseFormal = true;
                        }
                    else if (idTarget.isNestMate(idClass))
                        {
                        if (clzClass.isInstanceAscendant(clzTarget))
                            {
                            // scenario (2, 3)
                            fUseFormal = true;
                            }
                        else if (clzTarget.isInstanceAscendant(clzClass))
                            {
                            // scenario (4)
                            fUseFormal = true;
                            }
                        // TODO: scenarios (6), (7)
                        }
                    if (fUseFormal)
                        {
                        type = clzTarget.getFormalType();
                        }
                    }
                }
            }
        else
            {
            TypeConstant[] atypeParams = new TypeConstant[paramTypes.size()];
            for (int i = 0, c = paramTypes.size(); i < c; ++i)
                {
                TypeExpression exprOrig = paramTypes.get(i);
                TypeExpression expr     = (TypeExpression) exprOrig.validate(ctx, null, errs);
                if (expr == null)
                    {
                    fValid         = false;
                    atypeParams[i] = pool.typeObject();
                    }
                else
                    {
                    if (expr != exprOrig)
                        {
                        paramTypes.set(i, expr);
                        }

                    TypeConstant   typeParam = expr.getType();
                    TypeConstant[] atypeSub  = typeParam.getParamTypesArray();
                    if (atypeSub.length >= 1 && typeParam.isA(pool.typeType()))
                        {
                        atypeParams[i] = atypeSub[0];
                        }
                    else
                        {
                        expr.log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                            pool.ensureParameterizedTypeConstant(pool.typeType(),
                                pool.typeObject()).getValueString(), typeParam.getValueString());

                        fValid         = false;
                        atypeParams[i] = pool.typeObject();
                        }
                    }
                }
            type = pool.ensureParameterizedTypeConstant(type, atypeParams);
            }

        TypeConstant typeType = pool.ensureParameterizedTypeConstant(pool.typeType(), type);

        return finishValidation(typeRequired, typeType, fValid ? TypeFit.Fit : TypeFit.NoFit, type, errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (immutable != null)
            {
            sb.append("immutable ");
            }

        sb.append(getName());

        if (access != null)
            {
            sb.append(':')
              .append(access.getId().TEXT);
            }

        if (nonnarrow != null)
            {
            sb.append('!');
            }

        if (paramTypes != null)
            {
            sb.append('<');
            boolean first = true;
            for (TypeExpression type : paramTypes)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(type);
                }
            sb.append('>');
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token                immutable;
    protected List<Token>          names;
    protected Token                access;
    protected Token                nonnarrow;
    protected List<TypeExpression> paramTypes;
    protected long                 lEndPos;

    protected transient NameResolver m_resolver;
    protected transient Constant     m_constId;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NamedTypeExpression.class, "paramTypes");
    }
