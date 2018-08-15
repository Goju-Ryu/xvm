package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Argument;
import org.xvm.asm.Register;
import org.xvm.asm.Version;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.Call_00;
import org.xvm.asm.op.Call_01;
import org.xvm.asm.op.Call_0N;
import org.xvm.asm.op.Call_10;
import org.xvm.asm.op.Call_11;
import org.xvm.asm.op.Call_1N;
import org.xvm.asm.op.Call_N0;
import org.xvm.asm.op.Call_N1;
import org.xvm.asm.op.Call_NN;
import org.xvm.asm.op.Construct_0;
import org.xvm.asm.op.Construct_1;
import org.xvm.asm.op.Construct_N;
import org.xvm.asm.op.FBind;
import org.xvm.asm.op.Invoke_00;
import org.xvm.asm.op.Invoke_01;
import org.xvm.asm.op.Invoke_0N;
import org.xvm.asm.op.Invoke_10;
import org.xvm.asm.op.Invoke_11;
import org.xvm.asm.op.Invoke_1N;
import org.xvm.asm.op.Invoke_N0;
import org.xvm.asm.op.Invoke_N1;
import org.xvm.asm.op.Invoke_NN;
import org.xvm.asm.op.MBind;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;


/**
 * Invocation expression represents calling a method or function. An oversimplification of the
 * model is as follows:
 * <ul>
 * <li><i>"Binding a method"</i>: Reference + Method = Function</li>
 * <li><i>"Binding parameters" (aka currying)</i>: Function + Argument(s) = Function'</li>
 * <li><i>"Calling a function"</i>: Function + () = Return Value(s)</li>
 * </ul>
 * <p/>
 * Most of the time, this is all accomplished in a single syntactic step, but not always:
 * <p/>
 * <pre><code>
 *   // bind target "list" to method "add", bind argument, call function
 *   list.add(item);
 *
 *   // on "List" type, find "add" method with one parameter (four alternatives shown)
 *   Method m = List.&add(?);
 *   Method m = List.&add(&lt;List.ElementType&gt;?);
 *   Method m = List.add(?);
 *   Method m = List.add(&lt;List.ElementType&gt;?);
 *
 *    // bind target "list" to method "add", bind argument
 *   function void () fn = list.&add(item);
 *
 *   // call the function held in "fn"
 *   fn();
 * </code></pre>
 * <p/>
 * There are op codes for:
 * <ul>
 * <li>Binding a method to its target reference to create a function;</li>
 * <li>Binding any subset (including all) parameters of a function to create a new function;</li>
 * <li>Calling a function (16 different ops, including short forms for calling functions whose
 *     parameters have been bound vs. functions that still have unbound parameters);</li>
 * <li>Invoking a method using a target reference (16 different ops);</li>
 * <li>Instantiating a new object and invoking its constructor (16 different ops); and</li>
 * <li>Invoking another constructor from within a constructor (4 different ops);</li>
 * </ul>
 * <p/>
 * Each of these operations is type safe, requiring a provably correct target reference, arguments,
 * and destinations for each of the return values.
 * <p/>
 * <pre><code>
 *                                            bind    bind
 *   description                              target  args    call    result
 *   ---------------------------------------  ------  ------  ------  ------------------------------
 *   obtain reference to method or function                           method or function
 *   function invocation                                      X       result of call
 *   binding function parameters / currying           X               function from a function
 *   function invocation                              X       X       result of call
 *   method binding                           X                       function from a method name
 *   method invocation                        X               X       result of call
 *   method and parameter binding             X       X               function from a method name
 *   method invocation                        X       X       X       result of call
 * </code></pre>
 * <p/>
 * The implementation is specialized when the method or function <b>name</b> is provided. The
 * invocation expression knows this situation exists because its {@link #expr} refers to a {@link
 * NameExpression}. The responsibilities of the InvocationExpression are expanded as follows:
 * <ul>
 * <li>The {@link #expr} itself is <b>NOT</b> asked to validate! All of the information that it
 *     contains is instead validated by and used by the InvocationExpression directly.</li>
 * <li>The NameExpression's own {@link NameExpression#left left} expression (if any) represents
 *     the class/type within which -- or reference on which -- the method or function will be
 *     found; a lack of a left expression implies a possible "this." for non-static code contexts,
 *     and the current name-resolution {@link Context} for both non-static and static code
 *     contexts.</li>
 * <li>The NameExpression's purpose in this case is to provide information to the
 *     InvocationExpression so that it can locate the correct method or function. In addition to
 *     the context and the name, there are optional "redundant returns" on the NameExpression in
 *     {@link NameExpression#params params}. The InvocationExpression must validate these, and for
 *     each redundant return type provided, it must ensure that any method/function that it selects
 *     matches that redundant return.</li>
 * <li>Lastly, the NameExpression includes a no-de-reference indicator, {@link
 *     NameExpression#isSuppressDeref() isSuppressDeref()}, which tells the InvocationExpression
 *     not to perform the "call" portion itself, but rather to yield the method or function
 *     reference as a result. This information may overlap with information that the
 *     InvocationExpression has from its own method arguments, if any is a NonBindingExpression,
 *     since that also indicates that the InvocationExpression must not perform the "call", but
 *     rather yields a method or function reference as its result.</li>
 * <li>...</li>
 * </ul>
 * <p/>
 * The rules for determining the method or function to call when the name is provided:
 * <ol>
 * <li>Validate the (optional) left expression, and all of the (optional) redundant return type
 *     {@link NameExpression#params params} expressions of the NameExpression.</li>
 * <li>Determine whether the search will include methods, functions, or both. Functions are included
 *     if (i) there is no left, or (ii) the left is identity-mode. Methods are included if (i) there
 *     is a left, (ii) there is no left and the context is not static, or (iii) the call itself is
 *     suppressed and no arguments are bound (i.e. no "this" is required to bind the method).</li>
 * <li>If the name has a {@code left} expression, that expression provides the scope to search for
 *     a matching method/function. If the left expression is itself a NameExpression, then the scope
 *     may actually refer to two separate types, because the NameExpression may indicate both (i)
 *     identity mode and (ii) reference mode. In this case, the identity mode is treated as a
 *     first scope, and the reference mode is treated as a second scope.</li>
 * <li>If the name does not have a {@code left} expression, then walk up the AST parent node chain
 *     looking for a registered name, i.e. a local variable of that name, stopping once the
 *     containing method/function (but <b>not</b> a lambda, since it has a permeable barrier to
 *     enable local variable capture) is reached. If a match is found, then that is the function to
 *     use, and it is an error if the type of that variable is not a function, or a reference that
 *     has an @Auto conversion to a function. (Done.)</li>
 * <li>Otherwise, for a name without a {@code left} expression (which provides its scope),
 *     determine the sequence of scopes that will be searched for matching methods/functions. For
 *     example, the point from which the call is occurring could be inside a (i) lambda, inside a
 *     (ii) method, inside a (iii) property, inside a (iv) child class, inside a (v) static child
 *     class, inside a (vi) top level class, inside a (vii) package, inside a (viii) module; in this
 *     example, scope (i) is searched first for any matching methods and functions, then scope (ii),
 *     then scope (ii), (iii), (iv), and (v). Because scope (v) is a static child, when scope (vi)
 *     is searched, it is only searched for functions, <i>unless</i> the InvocationExpression is
 *     <b>not</b> performing a "call" (i.e. no "this" is required), in which case methods are
 *     included. The package and module are omitted from the search; we do not venture past the
 *     top level class barrier in the search.</li>
 * <li>Starting at the first scope, check for a property of that name; if one exists, treat it using
 *     the rules from step 4 above: If a match is found, then that is the method/function to use,
 *     and it is an error if the type of that property/constant is not a method, a function, or a
 *     reference that has an @Auto conversion to a method or function. (Done.)</li>
 * <li>Otherwise, find the methods/functions that match the above criteria, as follows:
 *     (i) including only method and/or functions as appropriate; (ii) matching the name; (iii) for
 *     each named argument, having a matching parameter name on the method/function; (iv) after
 *     accounting for named arguments, having at least as many parameters as the number of provided
 *     arguments, and no more <i>required</i> parameters than the number of provided arguments; (v)
 *     having each argument from steps (iii) and (iv) be isA() or @Auto convertible to the type of
 *     each corresponding parameter; and (vi) matching (i.e. isA()) any specified redundant return
 *     types.</li>
 * <li>If no methods or functions match from steps 6 &amp; 7, then repeat at the next outer scope.
 *     If there are no more outer scopes, then it is an error. (Done.)</li>
 * <li>If one method match from steps 6 &amp; 7, then that method is selected. (Done.)</li>
 * <li>If multiple methods/functions match from steps 6 &amp; 7, then the <i>best</i> one must be
 *     selected. First, the algorithm from {@link TypeConstant#selectBest(SignatureConstant[])} is
 *     used. If that algorithm results in a single selection, then that single selection is used.
 *     Otherwise, the redundant return types are used as a tie breaker; if that results in a single
 *     selection, then that single selection is used. Otherwise, the ambiguity is an error.
 *     (Done.)</li>
 * </ol>
 * <p/>
 * The "construct" name (which is actually a keyword) indicates a simplified set of rules;
 * specifically:
 * <ul>
 * <li>It requires the name to either (i) have no <i>left</i>, or (ii) have a <i>left</i> that is
 *     itself a NameExpression in identity-mode;</li>
 * <li>Only the constructors are searched; the name cannot specify a variable or a property;</li>
 * <li>There cannot / must not be any redundant returns, so any associated rules are ignored.</li>
 * </ul>
 * <p/>
 * Deferred implementation items:
 * <ul><li>TODO default parameter values
 * </li><li>TODO named parameters
 * </li></ul>
 */
public class InvocationExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public InvocationExpression(Expression expr, List<Expression> args, long lEndPos)
        {
        this.expr    = expr;
        this.args    = args;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        return expr instanceof NameExpression
                && ((NameExpression) expr).getName().equals("versionMatches")
                && args.size() == 1
                && args.get(0) instanceof VersionExpression
                && ((NameExpression) expr).getLeftExpression() != null
                && ((NameExpression) expr).isOnlyNames()
                || super.validateCondition(errs);
        }

    @Override
    public ConditionalConstant toConditionalConstant()
        {
        if (validateCondition(null))
            {
            // build the qualified module name
            StringBuilder sb    = new StringBuilder();
            List<Token>   names = ((NameExpression) expr).getNameTokens();
            for (int i = 0, c = names.size() - 1; i < c; ++i)
                {
                if (i > 0)
                    {
                    sb.append('.');
                    }
                sb.append(names.get(i).getValueText());
                }

            ConstantPool pool    = pool();
            String       sModule = sb.toString();
            Version      version = ((VersionExpression) args.get(0)).getVersion();
            return pool.ensureImportVersionCondition(
                    pool.ensureModuleConstant(sModule), pool.ensureVersionConstant(version));
            }

        return super.toConditionalConstant();
        }

    @Override
    public long getStartPosition()
        {
        return expr.getStartPosition();
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


    // ----- compilation ---------------------------------------------------------------------------


    @Override
    protected boolean hasSingleValueImpl()
        {
        return false;
        }

    @Override
    protected boolean hasMultiValueImpl()
        {
        return true;
        }

    @Override
    public TypeConstant[] getImplicitTypes(Context ctx)
        {
        ConstantPool pool = pool();

        List<Expression> aArgExprs = args;
        int              cArgs     = aArgExprs == null ? 0 : aArgExprs.size();
        TypeConstant[]   aArgTypes = new TypeConstant[cArgs];
        for (int i = 0; i < cArgs; ++i)
            {
            // note: could be null (will have to be tolerant of this elsewhere); as long as it does
            // not introduce ambiguity, we can still figure out the result type(s) of the invoke
            aArgTypes[i] = aArgExprs.get(i).getImplicitType(ctx);
            }

        if (expr instanceof NameExpression)
            {
            NameExpression exprName = (NameExpression) expr;
            Expression     exprLeft = exprName.left;
            TypeConstant   typeLeft = null;
            if (exprLeft != null)
                {
                typeLeft = exprLeft.getImplicitType(ctx);
                if (typeLeft == null)
                    {
                    return TypeConstant.NO_TYPES;
                    }
                }

            // collect as many redundant return types as possible to help narrow down the
            // possible method/function matches
            TypeConstant[]       aRedundant    = null;
            List<TypeExpression> listRedundant = exprName.params;
            if (listRedundant != null)
                {
                int                     cParams   = listRedundant.size();
                ArrayList<TypeConstant> listTypes = new ArrayList<>(cParams);
                for (int i = 0; i < cParams; ++i)
                    {
                    TypeConstant typeParam = listRedundant.get(i).getImplicitType(ctx);
                    if (typeParam == null)
                        {
                        break;
                        }
                    listTypes.add(typeParam);
                    }

                aRedundant = listTypes.toArray(new TypeConstant[cParams]);
                }

            Argument argMethod = resolveName(ctx, false, typeLeft, aRedundant, aArgTypes, ErrorListener.BLACKHOLE);
            if (argMethod == null)
                {
                return TypeConstant.NO_TYPES;
                }

            // handle conversion to function
            if (m_idConvert != null)
                {
                // the first return type of the idConvert method must be a function, which in turn
                // has two sub-types, the first of which is its "params" and the second of which is
                // its "returns", and the returns is a tuple type parameterized by the types of the
                // return values from the function
                TypeConstant[] atypeConvRets = m_idConvert.getRawReturns();
                return m_fCall
                        ? atypeConvRets[0].getParamTypesArray()[F_RETS].getParamTypesArray()
                        : atypeConvRets;
                // TODO if (m_fBindParams) { // calculate the resulting (partially or fully bound) result type
                }

            // handle method or function
            if (argMethod instanceof MethodConstant)
                {
                MethodConstant constMethod = (MethodConstant) argMethod;
                if (typeLeft == null)
                    {
                    typeLeft = ctx.getThisType();
                    }

                if (m_fCall)
                    {
                    return (m_fMethod ? constMethod.resolveAutoNarrowing(pool, typeLeft)
                                      : constMethod.getSignature()
                           ).getRawReturns();
                    }
                else
                    {
                    return new TypeConstant[] {constMethod.getRefType(typeLeft)};
                    }
                // TODO if (m_fBindTarget) { bind; result will be a Function
                }

            // must be a property or a variable of type function (@Auto conversion possibility
            // already handled above); the function has two tuple sub-types, the second of which is
            // the "return types" of the function
            TypeConstant typeArg;
            if (argMethod instanceof PropertyConstant)
                {
                PropertyConstant idProp = (PropertyConstant) argMethod;
                typeArg = typeLeft.ensureTypeInfo().findProperty(idProp).getType();
                }
            else
                {
                assert argMethod instanceof Register;
                typeArg = argMethod.getType().resolveTypedefs();
                }

            assert typeArg.isA(pool.typeFunction());

            return m_fCall
                    ? typeArg.getParamTypesArray()[F_RETS].getParamTypesArray()
                    : new TypeConstant[] {typeArg};
            // TODO if (m_fBindParams) { // calculate the resulting (partially or fully bound) result type
            }
        else // not a NameExpression
            {
            // it has to either be a function or convertible to a function
            TypeConstant typeFn = expr.getImplicitType(ctx);
            if (typeFn != null)
                {
                typeFn = validateFunction(ctx, typeFn, aArgTypes, ErrorListener.BLACKHOLE);
                if (typeFn != null)
                    {
                    return m_fCall
                            ? typeFn.getParamTypesArray()[F_RETS].getParamTypesArray()
                            : new TypeConstant[] {typeFn};
                    // TODO calculate resulting function type by partially (or completely) binding the method/function as specified by "args"
                    }
                }

            return TypeConstant.NO_TYPES;
            }
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        // validate the invocation arguments, some of which may be left unbound (e.g. "?")
        boolean          fValid   = true;
        ConstantPool     pool     = pool();
        List<Expression> listArgs = args;
        int              cArgs    = listArgs.size();
        TypeConstant[]   aArgs    = new TypeConstant[cArgs];
        for (int i = 0, c = listArgs.size(); i < c; ++i)
            {
            Expression exprOld = listArgs.get(i);
            Expression exprNew = exprOld.validate(ctx, null, errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else
                {
                if (exprNew != exprOld)
                    {
                    listArgs.set(i, exprNew);
                    }
                aArgs[i] = exprNew.getType();   // WARNING: null type if unbound w/o "<Type>" prefix
                }
            }

        // when we have a name expression on our immediate left, we do NOT (!!!) validate it,
        // because the name resolution is the responsibility of this InvocationExpression, and
        // the NameExpression itself will error on resolving a method/function name
        if (expr instanceof NameExpression)
            {
            // if the name expression has an expression on _its_ left, then we are now responsible
            // for validating that "left left" expression
            NameExpression exprName = (NameExpression) expr;
            Expression     exprLeft = exprName.left;
            TypeConstant   typeLeft = null;
            if (exprLeft != null)
                {
                Expression exprNew = exprLeft.validate(ctx, null, errs);
                if (exprNew == null)
                    {
                    fValid = false;
                    }
                else
                    {
                    if (exprNew != exprLeft)
                        {
                        // WARNING: mutating contents of the NameExpression, which has been
                        //          _subsumed_ by this InvocationExpression
                        exprName.left = exprLeft = exprNew;
                        }

                    typeLeft = exprLeft.getType();
                    if (typeLeft == null)
                        {
                        fValid = false;
                        }
                    }
                }

            // validate the "redundant returns" expressions
            TypeConstant[]       aRedundant    = null;
            List<TypeExpression> listRedundant = exprName.params;
            if (listRedundant != null)
                {
                int cRedundant = listRedundant.size();
                aRedundant = new TypeConstant[cRedundant];
                for (int i = 0; i < cRedundant; ++i)
                    {
                    TypeExpression typeOld = listRedundant.get(i);
                    TypeExpression typeNew = (TypeExpression) typeOld.validate(
                            ctx, pool.typeType(), errs);
                    if (typeNew == null)
                        {
                        fValid = false;
                        }
                    else
                        {
                        if (typeNew != typeOld)
                            {
                            // WARNING: mutating contents of the NameExpression, which has been
                            //          _subsumed_ by this InvocationExpression
                            listRedundant.set(i, typeNew);
                            }
                        aRedundant[i] = typeNew.getType();
                        }
                    }
                }

            // the reason for tracking success (fValid) is that we want to get as many things
            // validated as possible, but if some of the expressions didn't validate, we can't
            // predictably find the desired method or function (e.g. without a left expression
            // providing validated type information)
            if (fValid)
                {
                // resolving the name will yield a method, a function, or something else that needs
                // to yield a function, such as a property or variable that holds a function or
                // something that can be converted to a function
                Argument argMethod = resolveName(ctx, true, typeLeft, aRedundant, aArgs, errs);
                if (argMethod != null)
                    {
                    // handle conversion to function
                    if (m_idConvert != null)
                        {
                        // the first return type of the idConvert method must be a function, which in turn
                        // has two sub-types, the first of which is its "params" and the second of which is
                        // its "returns", and the returns is a tuple type parameterized by the types of the
                        // return values from the function
                        TypeConstant[] atypeConvRets = m_idConvert.getRawReturns();
                        TypeConstant[] atypeResult   = m_fCall
                                ? atypeConvRets[0].getParamTypesArray()[F_RETS].getParamTypesArray()
                                : atypeConvRets;
                        // TODO if (m_fBindParams) { // calculate the resulting (partially or fully bound) result type
                        return finishValidations(atypeRequired, atypeResult, TypeFit.Fit, null, errs);
                        }

                    // handle method or function
                    if (argMethod instanceof MethodConstant)
                        {
                        MethodConstant constMethod = (MethodConstant) argMethod;
                        if (typeLeft == null)
                            {
                            typeLeft = ctx.getThisType();
                            }
                        TypeConstant[] atypeResult;
                        if (m_fCall)
                            {
                            atypeResult = (m_fMethod ? constMethod.resolveAutoNarrowing(pool, typeLeft)
                                                     : constMethod.getSignature()
                                          ).getRawReturns();
                            }
                        else
                            {
                            atypeResult = new TypeConstant[] {constMethod.getRefType(typeLeft)};
                            }
                        // TODO if (m_fBindTarget) { // bind; result will be a Function

                        return finishValidations(atypeRequired, atypeResult, TypeFit.Fit, null, errs);
                        }

                    // must be a property or a variable of type function (@Auto conversion possibility
                    // already handled above); the function has two tuple sub-types, the second of which is
                    // the "return types" of the function
                    TypeConstant typeArg;
                    if (argMethod instanceof PropertyConstant)
                        {
                        PropertyConstant idProp = (PropertyConstant) argMethod;
                        typeArg = typeLeft.ensureTypeInfo().findProperty(idProp).getType();
                        }
                    else
                        {
                        assert argMethod instanceof Register;
                        typeArg = argMethod.getType().resolveTypedefs();
                        }

                    assert typeArg.isA(pool.typeFunction());
                    TypeConstant[] atypeResult = m_fCall
                            ? typeArg.getParamTypesArray()[F_RETS].getParamTypesArray()
                            : new TypeConstant[] {typeArg};
                    // TODO if (m_fBindParams) { // calculate the resulting (partially or fully bound) result type
                    return finishValidations(atypeRequired, atypeResult, TypeFit.Fit, null, errs);
                    }
                }
            }
        else // the expr is NOT a NameExpression
            {
            Expression exprNew = expr.validate(ctx, pool().typeFunction(), errs);
            if (exprNew != null)
                {
                expr = exprNew;

                m_fBindTarget = false;
                m_fBindParams = isAnyArgBound();
                m_fCall       = !isSuppressCall();

                // it has to either be a function or convertible to a function
                TypeConstant typeFn = validateFunction(ctx, exprNew.getType(), aArgs, errs);
                if (typeFn != null)
                    {
                    TypeConstant[] atypeResult = m_fCall
                            ? typeFn.getParamTypesArray()[F_RETS].getParamTypesArray()
                            : new TypeConstant[] {typeFn};
                    // TODO calculate resulting function type by partially (or completely) binding the method/function as specified by "args"
                    return finishValidations(atypeRequired, atypeResult, TypeFit.Fit, null, errs);
                    }
                }
            }

        return finishValidations(atypeRequired, atypeRequired == null ?
                TypeConstant.NO_TYPES : atypeRequired, TypeFit.NoFit, null, errs);
        }

    @Override
    public boolean isAborting()
        {
        for (Expression arg : args)
            {
            if (arg.isAborting())
                {
                return true;
                }
            }
        return expr.isAborting();
        }

    @Override
    public boolean isShortCircuiting()
        {
        return expr.isShortCircuiting();
        }

    @Override
    protected boolean allowsShortCircuit(Expression exprChild)
        {
        // invocation does not allow the arguments to short circuit
        return exprChild == expr;
        }

    @Override
    public Argument[] generateArguments(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        // NameExpression cannot (must not!) attempt to resolve method / function names; it is an
        // assertion or error if it tries; that is the responsibility of InvocationExpression
        Argument argFn      = null;
        boolean  fConstruct = false;
        if (expr instanceof NameExpression)
            {
            NameExpression exprName = (NameExpression) expr;
            Expression     exprLeft = exprName.left;
            if (m_argMethod instanceof MethodConstant)
                {
                MethodConstant idMethod = (MethodConstant) m_argMethod;
                boolean        fMethod  = m_fMethod;
                if (fMethod)
                    {
                    // idMethod is a MethodConstant for a method (including "finally")
                    if (m_fBindTarget)
                        {
                        // the method needs a target (its "this")
                        Argument argTarget;
                        if (exprLeft == null)
                            {
                            // use "this"
                            MethodStructure method = code.getMethodStructure();
                            assert !method.isFunction();
                            argTarget = generateReserved(
                                    method.isConstructor() ? Op.A_STRUCT : Op.A_PRIVATE, errs);
                            }
                        else
                            {
                            argTarget = exprLeft.generateArgument(ctx, code, true, true, errs);
                            }

                        if (m_fCall)
                            {
                            // it's a method, and we need to generate the necessary code that calls it;
                            // generate the arguments
                            TypeConstant[] atypeParams = idMethod.getRawParams();
                            int            cArgs       = atypeParams.length;
                            char           chArgs      = '0';
                            Argument       arg         = null;
                            Argument[]     aArgs       = null;
                            assert cArgs == args.size(); // TODO eventually support default arg values
                            // TODO the following code doesn't do argument conversions to the required parameter types
                            if (cArgs == 1)
                                {
                                chArgs = '1';
                                arg    = args.get(0).generateArgument(ctx, code, false, true, errs);
                                }
                            else if (cArgs > 1)
                                {
                                chArgs = 'N';
                                aArgs  = new Argument[cArgs];
                                for (int i = 0; i < cArgs; ++i)
                                    {
                                    aArgs[i] = args.get(i).generateArgument(ctx, code, false, true, errs);
                                    }
                                }

                            // generate registers for the return values
                            TypeConstant[] atypeRets = idMethod.getRawReturns();
                            int            cRets     = atypeRets.length;
                            char           chRets    = '0';
                            Register       ret       = null;
                            Register[]     aRets     = Register.NO_REGS;
                            if (cRets == 1)
                                {
                                chRets = '1';
                                ret    = new Register(atypeRets[0]);
                                aRets  = new Register[] {ret};
                                }
                            else if (cRets > 1)
                                {
                                chRets = 'N';
                                aRets  = new Register[cRets];
                                for (int i = 0; i < cRets; ++i)
                                    {
                                    aRets[i] = new Register(atypeRets[i]);
                                    }
                                }

                            switch (combine(chArgs, chRets))
                                {
                                case _00:
                                    code.add(new Invoke_00(argTarget, idMethod));
                                    break;

                                case _10:
                                    code.add(new Invoke_10(argTarget, idMethod, arg));
                                    break;

                                case _N0:
                                    code.add(new Invoke_N0(argTarget, idMethod, aArgs));
                                    break;

                                case _01:
                                    code.add(new Invoke_01(argTarget, idMethod, ret));
                                    break;

                                case _11:
                                    code.add(new Invoke_11(argTarget, idMethod, arg, ret));
                                    break;

                                case _N1:
                                    code.add(new Invoke_N1(argTarget, idMethod, aArgs, ret));
                                    break;

                                case _0N:
                                    code.add(new Invoke_0N(argTarget, idMethod, aRets));
                                    break;

                                case _1N:
                                    code.add(new Invoke_1N(argTarget, idMethod, arg, aRets));
                                    break;

                                case _NN:
                                    code.add(new Invoke_NN(argTarget, idMethod, aArgs, aRets));
                                    break;

                                default:
                                    throw new UnsupportedOperationException("TODO method invocation");
                                }

                            return aRets;
                            }
                        else // _NOT_ m_fCall
                            {
                            // the method gets bound to become a function; do this and drop through
                            // to the function handling
                            argFn = new Register(idMethod.getSignature().asFunctionType());
                            code.add(new MBind(argTarget, idMethod, argFn));
                            }
                        }
                    else // _NOT_ m_fBindTarget
                        {
                        // the method instance itself is the result, e.g. "Method m = Frog.&jump();"
                        assert m_idConvert == null && !m_fBindParams && !m_fCall;
                        return new Argument[] {m_argMethod};
                        }
                    }
                else // _NOT_ a method (so it must be a function or a constructor)
                    {
                    // use the function identity as the argument & drop through to the function handling
                    assert !m_fBindTarget && (exprLeft == null || !exprLeft.hasSideEffects());
                    argFn = m_argMethod;
                    fConstruct = ((MethodStructure) idMethod.getComponent()).isConstructor();
                    }
                }
            else // it is a NameExpression but _NOT_ a MethodConstant
                {
                // take the argument (e.g. "super") & drop through to the function handling
                assert !m_fBindTarget && (exprLeft == null || !exprLeft.hasSideEffects());
                argFn = m_argMethod;
                }
            }
        else // _NOT_ an InvocationExpression of a NameExpression (i.e. it's just a function)
            {
            // obtain the function that will be bound and/or called
            assert !m_fBindTarget;
            argFn = expr.generateArgument(ctx, code, true, true, errs);
            }

        // bind arguments and/or generate a call to the function specified by argFn; first, convert
        // it to the desired function if necessary
        TypeConstant typeFn = argFn.getType().resolveTypedefs();
        if (m_idConvert != null)
            {
            // argFn isn't a function; convert whatever-it-is into the desired function
            typeFn = m_idConvert.getRawReturns()[0];
            Register regFn  = new Register(typeFn);       // TODO need fStackOk for Op.A_STACK
            code.add(new Invoke_01(argFn, m_idConvert, regFn));
            argFn = regFn;
            }

        if (!m_fCall && !m_fBindParams)
            {
            // not binding anything; not calling anything; just returning the function itself
            return new Argument[] {argFn};
            }

        TypeConstant[] atypeSub    = typeFn.getParamTypesArray();
        TypeConstant[] atypeParams = atypeSub[F_ARGS].getParamTypesArray();
        int            cParams     = atypeParams.length;
        TypeConstant[] atypeRets   = atypeSub[F_RETS].getParamTypesArray();
        int            cRets       = atypeRets.length;
        if (m_fCall)
            {
            int            cArgs = args.size();
            char           chArgs      = '0';
            Argument       arg         = null;
            Argument[]     aArgs       = null;
            assert cArgs == cParams; // TODO eventually support default arg values
            assert m_fBindParams == cParams > 0;
            // TODO the following code doesn't do argument conversions to the required parameter types
            if (cArgs == 1)
                {
                chArgs = '1';
                arg    = args.get(0).generateArgument(ctx, code, false, true, errs);
                }
            else if (cArgs > 1)
                {
                chArgs = 'N';
                aArgs  = new Argument[cArgs];
                for (int i = 0; i < cArgs; ++i)
                    {
                    aArgs[i] = args.get(i).generateArgument(ctx, code, false, true, errs);
                    }
                }

            if (fConstruct)
                {
                MethodConstant idConstruct = (MethodConstant) argFn;
                switch (chArgs)
                    {
                    case '0':
                        code.add(new Construct_0(idConstruct));
                        break;

                    case '1':
                        code.add(new Construct_1(idConstruct, arg));
                        break;

                    case 'N':
                        code.add(new Construct_N(idConstruct, aArgs));
                        break;

                    case 'T':
                    default:
                        throw new UnsupportedOperationException("TODO constructor");
                    }
                return Register.NO_REGS;
                }

            // generate registers for the return values
            char       chRets = '0';
            Register   ret    = null;
            Register[] aRets  = Register.NO_REGS;
            if (cRets == 1)
                {
                chRets = '1';
                ret    = new Register(atypeRets[0]);
                aRets  = new Register[] {ret};
                }
            else if (cRets > 1)
                {
                chRets = 'N';
                aRets  = new Register[cRets];
                for (int i = 0; i < cRets; ++i)
                    {
                    aRets[i] = new Register(atypeRets[i]);
                    }
                }

            switch (combine(chArgs, chRets))
                {
                case _00:
                    code.add(new Call_00(argFn));
                    break;

                case _10:
                    code.add(new Call_10(argFn, arg));
                    break;

                case _N0:
                    code.add(new Call_N0(argFn, aArgs));
                    break;

                case _01:
                    code.add(new Call_01(argFn, ret));
                    break;

                case _11:
                    code.add(new Call_11(argFn, arg, ret));
                    break;

                case _N1:
                    code.add(new Call_N1(argFn, aArgs, ret));
                    break;

                case _0N:
                    code.add(new Call_0N(argFn, aRets));
                    break;

                case _1N:
                    code.add(new Call_1N(argFn, arg, aRets));
                    break;

                case _NN:
                    code.add(new Call_NN(argFn, aArgs, aRets));
                    break;

                default:
                    throw new UnsupportedOperationException("TODO method invocation");
                }

            return aRets;
            }

        // bind (or partially bind) the function
        assert m_fBindParams;

        // count the number of parameters to bind
        int cBind = 0;
        for (int i = 0; i < cParams; ++i)
            {
            if (!args.get(i).isNonBinding())
                {
                ++cBind;
                }
            }

        int[]      aiArg = new int[cBind];
        Argument[] aArg  = new Argument[cBind];
        for (int i = 0, iNext = 0; i < cParams; ++i)
            {
            if (!args.get(i).isNonBinding())
                {
                aiArg[iNext] = i;
                aArg [iNext] = args.get(i).generateArgument(ctx, code, false, true, errs);
                }
            }

        Register regFn = new Register(getType());
        code.add(new FBind(argFn, aiArg, aArg, regFn));
        return new Argument[] {regFn};
        }


    // ----- method resolution helpers -------------------------------------------------------------

    /**
     * @return true iff this expression does not actually result in an invocation, but instead
     *         resolves to a reference to a method or a function as its result
     */
    protected boolean isSuppressCall()
        {
        return (expr instanceof NameExpression && ((NameExpression) expr).isSuppressDeref())
                || isAnyArgUnbound();
        }

    /**
     * @return true iff any argument will be bound
     */
    protected boolean isAnyArgBound()
        {
        for (Expression expr : args)
            {
            if (!expr.isNonBinding())
                {
                return true;
                }
            }

        return false;
        }

    /**
     * @return true iff any argument will be left unbound
     */
    protected boolean isAnyArgUnbound()
        {
        for (Expression expr : args)
            {
            if (expr.isNonBinding())
                {
                return true;
                }
            }

        return false;
        }

    /**
     * @return true iff the parameter is named
     */
    protected boolean isParamNamed(Expression expr)
        {
        return expr instanceof LabeledExpression;
        }

    /**
     * @return the name of the parameter, or null if the parameter is not named
     */
    protected String getParamName(Expression expr)
        {
        return isParamNamed(expr)
                ? ((LabeledExpression) expr).getName()
                : null;
        }

    /**
     * Resolve the expression to determine the referred to method or function. Responsible for
     * setting {@link #m_argMethod}, {@link #m_idConvert}, {@link #m_fBindTarget},
     * {@link #m_fBindParams}, and {@link #m_fCall}.
     *
     * @param ctx         the compiler context
     * @param fForce      true to force the resolution, even if it has been done previously
     * @param typeLeft    the type of the "left" expression of the name, or null if there is no left
     * @param aRedundant  the types of any "redundant return" indicators
     * @param aArgs       array of argument types, with null meaning "any" (i.e. "?") or unknown (if
     *                    this is called before validation)
     * @param errs        the error list to log errors to
     *
     * @return the method constant, or null if it was not determinable
     */
    protected Argument resolveName(
            Context        ctx,
            boolean        fForce,
            TypeConstant   typeLeft,
            TypeConstant[] aRedundant,
            TypeConstant[] aArgs,
            ErrorListener  errs)
        {
        if (!fForce && m_argMethod != null)
            {
            return m_argMethod;
            }

        boolean fNoMBind = false;
        boolean fNoFBind = !isAnyArgBound();
        boolean fNoCall  = isSuppressCall();

        m_argMethod   = null;
        m_fMethod     = false;
        m_idConvert   = null;
        m_fBindTarget = false;
        m_fBindParams = !fNoFBind;
        m_fCall       = !fNoCall;

        // if the name does not have a left expression, then walk up the AST parent node chain
        // looking for a registered name, i.e. a local variable of that name, stopping once the
        // containing method/function (but <b>not</b> a lambda, since it has a permeable barrier to
        // enable local variable capture) is reached
        ConstantPool   pool      = pool();
        NameExpression exprName  = (NameExpression) expr;
        Token          tokName   = exprName.getNameToken();
        String         sName     = exprName.getName();
        Expression     exprLeft  = exprName.left;
        if (exprLeft == null)
            {
            Argument reg = ctx.getVar(tokName, errs);
            if (reg != null)
                {
                // should not be any redundant returns
                if (aRedundant != null && aRedundant.length > 0)
                    {
                    log(errs, Severity.ERROR, Compiler.UNEXPECTED_REDUNDANT_RETURNS);
                    // ignore them and continue
                    }

                if (validateFunction(ctx, reg.getType(), aArgs, errs) == null)
                    {
                    return null;
                    }
                else
                    {
                    m_argMethod = reg;
                    return reg;
                    }
                }

            // for a name without a left expression (which provides its scope), determine the
            // sequence of scopes that will be searched for matching methods/functions. For example,
            // the point from which the call is occurring could be inside a (i) lambda, inside a
            // (ii) method, inside a (iii) property, inside a (iv) child class, inside a (v) static
            // child class, inside a (vi) top level class, inside a (vii) package, inside a (viii)
            // module; in this example, scope (i) is searched first for any matching methods and
            // functions, then scope (ii), then scope (ii), (iii), (iv), and (v). Because scope (v)
            // is a static child, when scope (vi) is searched, it is only searched for functions,
            // unless the InvocationExpression is not performing a "call" (i.e. no "this" is
            // required), in which case methods are included. The package and module are omitted
            // from the search; we do not venture past the top level class barrier in the search
            Component parent   = getComponent();
            boolean   fHasThis = ctx.isMethod();
            NextParent: while (parent != null)
                {
                IdentityConstant idParent = parent.getIdentityConstant();
                switch (idParent.getFormat())
                    {
                    case Module:
                    case Package:
                    case Class:
                        {
                        ClassStructure clz  = (ClassStructure) parent;
                        TypeConstant   type = pool.ensureAccessTypeConstant(clz.getFormalType(),
                                Access.PRIVATE);

                        IdentityConstant idCallable = findCallable(type.ensureTypeInfo(errs), sName,
                                (fNoCall && fNoFBind) || fHasThis, true, aRedundant, aArgs, null);
                        if (idCallable != null)
                            {
                            Component callable = idCallable.getComponent();

                            m_argMethod   = idCallable;
                            m_fMethod     = callable == null || !callable.isStatic();
                            m_fBindTarget = m_fMethod && !fNoMBind;
                            break NextParent;
                            }

                        // we're done once we have searched the top-level class
                        if (parent instanceof ClassStructure && clz.isTopLevel())
                            {
                            break NextParent;
                            }

                        // if the class is a static child, then we lose the "this" when we go up to
                        // the parent class
                        if (clz.isStatic())
                            {
                            fHasThis = false;
                            }
                        break;
                        }

                    case Method:
                        {
                        MethodStructure method = (MethodStructure) parent;

                        int iParam = method.findParameter(sName);
                        if (iParam >= 0)
                            {
                            TypeConstant typeParam = method.getParam(iParam).getType();
                            if (typeParam.isA(pool.typeFunction()))
                                {
                                return m_argMethod = new Register(typeParam, iParam);
                                }
                            else
                                {
                                // TODO: check for @Auto conv
                                // TODO: log an error
                                notImplemented();
                                }
                            }
                        break;
                        }

                    case Property:
                        // Starting at the first scope, check for a property of that name; if one exists, treat it using
                        // the rules from step 4 above: If a match is found, then that is the method/function to use,
                        // and it is an error if the type of that property/constant is not a method, a function, or a
                        // reference that has an @Auto conversion to a method or function. (Done.)</li>
                        // TODO
                        break;
                    }

                parent = parent.getParent();
                }
            }
        else // there is a "left" expression for the name
            {
            if (tokName.isSpecial())
                {
                // TODO handle special names (e.g. ".this")
                throw new UnsupportedOperationException("no handling yet for ." + sName);
                }

            // the left expression provides the scope to search for a matching method/function;
            // if the left expression is itself a NameExpression, and it's in identity mode (i.e. a
            // possible identity), then check the identity first
            Argument arg = null;
            if (exprLeft instanceof NameExpression && ((NameExpression) exprLeft).isIdentityMode(ctx, false))
                {
                // the left identity
                // - methods are included because there is a left, but since it is to obtain a
                //   method reference, there must not be any arg binding or actual invocation
                // - functions are included because the left is identity-mode
                TypeInfo infoLeft = ((NameExpression) exprLeft).getIdentity(ctx).ensureTypeInfo(errs);
                arg = findCallable(infoLeft, sName, fNoFBind && fNoCall, true, aRedundant, aArgs, null);

                if (arg instanceof MethodConstant)
                    {
                    m_fMethod = !infoLeft.getMethodById((MethodConstant) arg).isFunction();
                    }
                }

            if (arg == null)
                {
                // use the type of the left expression to get the TypeInfo that must contain the
                // method/function to call
                // - methods are included because there is a left and it is NOT identity-mode
                // - functions are NOT included because the left is NOT identity-mode
                TypeInfo infoLeft = typeLeft.ensureTypeInfo(errs);
                arg = findCallable(infoLeft, sName, true, false, aRedundant, aArgs, null);

                if (arg != null)
                    {
                    m_fBindTarget = true;
                    m_fMethod     = true;
                    }
                }

            m_argMethod = arg;
            }


        if (m_argMethod == null)
            {
            // error: could not find a matching method
            log(errs, Severity.ERROR, Compiler.MISSING_METHOD, sName);
            }

        return m_argMethod;
        }

    /**
     * Find a named method or function that best matches the specified requirements.
     *
     * @param infoParent  the TypeInfo to search for the method or function on
     * @param sName       the name of the method or function
     * @param fMethods    true to include methods in the search
     * @param fFunctions  true to include functions in the search
     * @param aRedundant  the redundant return type information (helps to clarify which method or
     *                    function to select)
     * @param aArgs       the types of the arguments being provided (some of which may be null to
     *                    indicate "unknown" in a pre-validation stage, or "non-binding unknown")
     * @param asArgNames  optional array of argument names (from LabeledExpressions)
     *
     * @return the matching method, function, or (rarely) property
     */
    protected IdentityConstant findCallable(
            TypeInfo       infoParent,
            String         sName,
            boolean        fMethods,
            boolean        fFunctions,
            TypeConstant[] aRedundant,
            TypeConstant[] aArgs,
            String[]       asArgNames)
        {
        // check for a property of that name; if one exists, it must be of type function, or a type
        // with an @Auto conversion to function - which will be verified by validateFunction()
        PropertyInfo prop = infoParent.findProperty(sName);
        if (prop != null)
            {
            return prop.getIdentity();
            }

        return infoParent.findCallable(sName, fMethods, fFunctions, aRedundant, aArgs, asArgNames);
        }

    /**
     * Check the type of the thing that is either a function or needs to be converted into a
     * function.
     * <p/>
     * Responsible for setting the {@link #m_idConvert} field if a conversion is necessary.
     *
     * @param ctx         the compiler context
     * @param typeFn      the type of the function (or the type of the object that should know how
     *                    to convert itself into a function)
     * @param aArgs       array of argument types, with null meaning "any" (i.e. "?") or unknown (if
     *                    this is called before validation)
     * @param errs        the error list to log errors to
     *
     * @return the type of the function, or null if a type-safe type for the function could not be
     *         determined
     */
    protected TypeConstant validateFunction(
            Context        ctx,
            TypeConstant   typeFn,
            TypeConstant[] aArgs,
            ErrorListener  errs)
        {
        ConstantPool pool = pool();

        // if a match is found, then that is the function to use, and it is an error if the
        // type of that variable is not a function or a reference that has an @Auto
        // conversion to a function. (Done.)
        typeFn = typeFn.resolveTypedefs().resolveGenerics(pool, ctx.getThisType());

        boolean        fFunction = typeFn.isA(pool.typeFunction());
        MethodConstant idConvert = null;
        if (!fFunction)
            {
            idConvert = typeFn.getConverterTo(pool.typeFunction());
            if (idConvert == null)
                {
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE, "Function", typeFn.getValueString());
                return null;
                }
            else
                {
                typeFn = idConvert.getRawReturns()[F_ARGS];
                }
            }

        // function must be parameterized by 2 fields: param types and return types
        // each is a parameterized "tuple" type constant with an array of types
        TypeConstant[] aSubTypes = typeFn.getParamTypesArray();
        if (!typeFn.isParamsSpecified() || aSubTypes.length < 2
                || !aSubTypes[F_ARGS].isTuple() || !aSubTypes[F_RETS].isTuple())
            {
            log(errs, Severity.ERROR, Compiler.MISSING_PARAM_INFORMATION);
            return null;
            }

        if (aArgs != null)
            {
            TypeConstant[] aParams = aSubTypes[F_ARGS].getParamTypesArray();
            int            cParams = aParams.length;
            int            cArgs   = aArgs.length;
            if (cParams != cArgs)
                {
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY, cParams, cArgs);
                // it's an error, but keep going
                }

            for (int i = 0, c = Math.min(cParams, cArgs); i < c; ++i)
                {
                TypeConstant typeParam = aParams[i];
                TypeConstant typeArg   = aArgs[i];
                if (typeArg != null && typeParam != null && !typeArg.isA(typeParam))
                    {
                    log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                            typeParam.getValueString(), typeArg.getValueString());
                    // it's an error, but keep going
                    }
                }
            }

        m_idConvert = idConvert;
        return typeFn;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(expr)
          .append('(');

        boolean first = true;
        for (Expression arg : args)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(arg);
            }

        sb.append(')');
        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @param p  '0', '1', or 'N'
     * @param r  '0', '1', or 'N'
     *
     * @return an int value that combines p|r
     */
    static int combine(int p, int r)
        {
        return (p << 8) | r;
        }

    static final int _00 = ('0' << 8) | '0';
    static final int _01 = ('0' << 8) | '1';
    static final int _0N = ('0' << 8) | 'N';
    static final int _10 = ('1' << 8) | '0';
    static final int _11 = ('1' << 8) | '1';
    static final int _1N = ('1' << 8) | 'N';
    static final int _N0 = ('N' << 8) | '0';
    static final int _N1 = ('N' << 8) | '1';
    static final int _NN = ('N' << 8) | 'N';


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Method type first parameter is the target type that the method binds to:
     * <p/>
     * {@code const Method<TargetType, Tuple<ParamTypes...>, Tuple<ReturnTypes...>>}
     */
    public static final int M_TARG = 0;
    /**
     * Method type second parameter is method param types tuple:
     * <p/>
     * {@code const Method<TargetType, Tuple<ParamTypes...>, Tuple<ReturnTypes...>>}
     */
    public static final int M_ARGS = 1;
    /**
     * Method type third parameter is return types tuple:
     * <p/>
     * {@code const Method<TargetType, Tuple<ParamTypes...>, Tuple<ReturnTypes...>>}
     */
    public static final int M_RETS = 2;
    /**
     * Function type first parameter is function param types tuple:
     * <p/>
     * {@code Function<ParamTypes extends Tuple<Type...>, ReturnTypes extends Tuple<Type...>>}
     */
    public static final int F_ARGS = 0;
    /**
     * Function type second parameter is return types tuple:
     * <p/>
     * {@code Function<ParamTypes extends Tuple<Type...>, ReturnTypes extends Tuple<Type...>>}
     */
    public static final int F_RETS = 1;

    protected Expression       expr;
    protected List<Expression> args;
    protected long             lEndPos;

    private transient boolean        m_fBindTarget;
    private transient boolean        m_fBindParams;
    private transient boolean        m_fCall;
    private transient boolean        m_fMethod; // does m_argMethod represent a method or function
    private transient Argument       m_argMethod;
    private transient MethodConstant m_idConvert;

    private static final Field[] CHILD_FIELDS = fieldsForNames(InvocationExpression.class, "expr", "args");
    }
