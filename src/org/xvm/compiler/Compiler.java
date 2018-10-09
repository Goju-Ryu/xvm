package org.xvm.compiler;


import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.IdentityConstant;

import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.StageMgr;
import org.xvm.compiler.ast.TypeCompositionStatement;

import org.xvm.util.Severity;


/**
 * A module compiler for Ecstasy code.
 * <p/>
 * The compiler is a multi-step state machine. This design is the result of the compiler for one
 * module needing to be able to be coordinated with compilers for other modules that are
 * co-dependent, i.e. that have dependencies on each other that need to be jointly resolved.
 */
public class Compiler
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a module compiler.
     *
     * @param repos   the module repository
     * @param stmtModule  the statement representing all of the code in the module
     * @param errs    the error list to log any errors to during the various phases of compilation
     */
    public Compiler(ModuleRepository repos, TypeCompositionStatement stmtModule, ErrorListener errs)
        {
        if (repos == null)
            {
            throw new IllegalArgumentException("repository required");
            }
        if (stmtModule == null)
            {
            throw new IllegalArgumentException("AST node for module required");
            }
        if (stmtModule.getCategory().getId() != Token.Id.MODULE)
            {
            throw new IllegalArgumentException("AST node for module is not a module statement");
            }
        if (errs == null)
            {
            errs = ErrorListener.RUNTIME;
            }

        m_repos      = repos;
        m_stmtModule = stmtModule;
        m_errs       = errs;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the ModuleRepository that was supplied to the compiler
     */
    public ModuleRepository getRepository()
        {
        validateCompiler();
        return m_repos;
        }

    /**
     * @return the TypeCompositionStatement for the module
     */
    public TypeCompositionStatement getModuleStatement()
        {
        validateCompiler();
        return m_stmtModule;
        }

    /**
     * @return the ErrorListener that the compiler reports errors to
     */
    public ErrorListener getErrorListener()
        {
        validateCompiler();
        return m_errs;
        }

    /**
     * @return the FileStructure if it has been created
     */
    public FileStructure getFileStructure()
        {
        validateCompiler();
        return m_structFile;
        }

    /**
     * @return the current stage of the compiler
     */
    public Stage getStage()
        {
        return m_stage;
        }

    /**
     * Test if the compiler has reached the specified stage.
     *
     * @param stage  the compiler stage to test for
     *
     * @return true if the compiler has already reached or passed the specified stage
     */
    public boolean alreadyReached(Stage stage)
        {
        validateCompiler();
        assert stage != null;
        return getStage().compareTo(stage) >= 0;
        }


    // ----- public API ----------------------------------------------------------------------------

    /**
     * First pass: Create a FileStructure that represents the module, its packages, their classes,
     * their nested classes (recursively), plus the names of properties and methods within each of
     * those.
     * <p/>
     * This method is not permitted to use the ModuleRepository.
     * <p/>
     * Any error results are logged to the ErrorListener.
     *
     * @return the initial file structure
     */
    public FileStructure generateInitialFileStructure()
        {
        validateCompiler();

        // idempotent: allow this to be called more than necessary without any errors/side-effects
        if (getStage() == Stage.Initial)
            {
            setStage(Stage.Registering);

            StageMgr mgr = new StageMgr(m_stmtModule, Stage.Registered, m_errs);
            if (!mgr.processComplete())
                {
                throw new CompilerException("failed to create module");
                }
            m_structFile = m_stmtModule.getComponent().getFileStructure();
            m_structFile.setErrorListener(m_errs);
            setStage(Stage.Registered);
            }

        return m_structFile;
        }

    /**
     * Second pass: Link the modules together based on their declared dependencies.
     * <p/>
     * This method uses the ModuleRepository.
     * <p/>
     * Any error results are logged to the ErrorListener.
     */
    public void linkModules()
        {
        validateCompiler();
        ensureReached(Stage.Registered);

        // idempotent: allow this to be called more than necessary without any errors/side-effects
        if (alreadyReached(Stage.Loaded))
            {
            return;
            }

        enter();

        // first time through, load any module dependencies
        setStage(Stage.Loading);
        for (String sModule : m_structFile.moduleNames())
            {
            if (!sModule.equals(m_structFile.getModuleName()))
                {
                ModuleStructure structFingerprint = m_structFile.getModule(sModule);
                assert structFingerprint.isFingerprint();
                assert structFingerprint.getFingerprintOrigin() == null;

                // load the module against which the compilation will occur
                if (!m_repos.getModuleNames().contains(sModule))
                    {
                    // no error is logged here; the package that imports the module will detect
                    // the error when it is asked to resolve global names; see
                    // TypeCompositionStatement
                    continue;
                    }

                ModuleStructure structActual = m_repos.loadModule(sModule); // TODO versions etc.
                structFingerprint.setFingerprintOrigin(structActual);
                }
            }

        exit();
        setStage(Stage.Loaded);
        }

    /**
     * Third pass: Resolve all of the globally-visible dependencies and names. This pass does not
     * recurse into methods.
     * <p/>
     * This method uses the ModuleRepository.
     * <p/>
     * Any error results are logged to the ErrorListener.
     * <p/>
     * The caller is responsible for calling this method until it returns true.
     *
     * @return true iff the pass is complete; false indicates that this method MUST be called again
     */
    public boolean resolveNames()
        {
        validateCompiler();
        ensureReached(Stage.Registered);

        // idempotent: allow this to be called more than necessary without any errors/side-effects
        if (alreadyReached(Stage.Resolved))
            {
            return true;
            }

        enter();

        // recursively resolve all of the unresolved global names, and if anything couldn't get done
        // in one pass, then store it off in a list to tackle next time
        if (!alreadyReached(Stage.Resolving))
            {
            // first time through: resolve starting from the module, and recurse down
            setStage(Stage.Resolving);
            m_mgr = new StageMgr(m_stmtModule, Stage.Resolved, m_errs);
            }

        if (m_mgr.processComplete())
            {
            setStage(Stage.Resolved);
            }

        exit();
        return m_mgr.isComplete() || m_errs.isAbortDesired();
        }

    /**
     * Fourth pass: Resolve all types and constants. This does recurse to the full depth of the AST
     * tree.
     * <p/>
     * This method uses the ModuleRepository.
     * <p/>
     * Any error results are logged to the ErrorListener.
     * <p/>
     * The caller is responsible for calling this method until it returns true.
     *
     * @return true iff the pass is complete; false indicates that this method MUST be called again
     */
    public boolean validateExpressions()
        {
        validateCompiler();
        ensureReached(Stage.Resolved);

        // idempotent: allow this to be called more than necessary without any errors/side-effects
        if (alreadyReached(Stage.Validated))
            {
            return true;
            }

        enter();

        // recursively resolve all of the unresolved global names, and if anything couldn't get done
        // in one pass, the manager will keep track of what remains to be done
        if (!alreadyReached(Stage.Validating))
            {
            // first time through: resolve starting from the module, and recurse down
            setStage(Stage.Validating);
            m_mgr = new StageMgr(m_stmtModule, Stage.Validated, m_errs);
            }

        if (m_mgr.processComplete())
            {
            setStage(Stage.Validated);
            }

        exit();
        return m_mgr.isComplete() || m_errs.isAbortDesired();
        }

    /**
     * This stage finishes the compilation by emitting any necessary code and any remaining
     * structures.
     * <p/>
     * This method uses the ModuleRepository.
     * <p/>
     * Any error results are logged to the ErrorListener.
     * <p/>
     * The caller is responsible for calling this method until it returns true.
     *
     * @return true iff the pass is complete; false indicates that this method MUST be called again
     */
    public boolean generateCode()
        {
        validateCompiler();
        ensureReached(Stage.Validated);

        // idempotent: allow this to be called more than necessary without any errors/side-effects
        if (alreadyReached(Stage.Emitted))
            {
            return true;
            }

        enter();

        // recursively resolve all of the unresolved global names, and if anything couldn't get done
        // in one pass, then store it off in a list to tackle next time
        if (!alreadyReached(Stage.Emitting))
            {
            // first time through: resolve starting from the module, and recurse down
            setStage(Stage.Emitting);
            m_mgr = new StageMgr(m_stmtModule, Stage.Emitted, m_errs);
            }

        if (m_mgr.processComplete())
            {
            setStage(Stage.Emitted);

            // "purge" the constant pool and do a final validation on the entire module structure
            m_structFile.reregisterConstants(true);
            m_structFile.validate(m_errs);
            m_structFile.setErrorListener(null);
            }

        exit();
        return m_mgr.isComplete() || m_errs.isAbortDesired();
        }

    /**
     * After a certain number of attempts to resolve names by invoking {@link #resolveNames}, this
     * method will report any unresolved names as fatal errors.
     */
    public void logRemainingDeferredAsErrors()
        {
        for (AstNode node : m_mgr.takeRevisitList())
            {
            Component        component = node.getComponent();
            IdentityConstant id        = component == null ? null : component.getIdentityConstant();
            node.log(m_errs, Severity.FATAL, Compiler.INFINITE_RESOLVE_LOOP, id == null
                    ? node.getSource().toString(node.getStartPosition(), node.getEndPosition())
                    : id.toString());
            }
        }

    /**
     * Discard the compiler. This invalidates the compiler; any further attempts to use the compiler
     * will result in an exception.
     */
    public void invalidate()
        {
        setStage(Stage.Discarded);
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "Compiler (Module=" + m_stmtModule.getName() + ", Stage=" + getStage() + ")";
        }


    // ----- internal helpers ----------------------------------------------------------------------

    /**
     * Verify that the compiler has not been invalidated.
     *
     * @throws IllegalStateException  if the compiler has been invalidated
     */
    private void validateCompiler()
        {
        if (getStage() == Stage.Discarded)
            {
            throw new IllegalStateException();
            }
        }

    /**
     * Verify that the compiler has reached the specified stage.
     *
     * @param stage  the stage that the compiler must have already reached
     *
     * @throws IllegalStateException  if the compiler has not reached the specified stage
     */
    private void ensureReached(Stage stage)
        {
        if (!alreadyReached(stage))
            {
            throw new IllegalStateException("Stage=" + getStage() + " (expected: " + stage + ")");
            }
        }

    /**
     * Update the stage to the specified stage, if the specified stage is later than the current
     * stage.
     *
     * @param stage  the suggested stage
     */
    private void setStage(Stage stage)
        {
        // stage is a "one way" attribute
        if (stage != null && stage.compareTo(m_stage) > 0)
            {
            m_stage = stage;
            }
        }

    /**
     * Start a stage processing.
     */
    private void enter()
        {
        ConstantPool.setCurrentPool(m_structFile.getConstantPool());
        }

    /**
     * Finish a stage processing.
     */
    private void exit()
        {
        ConstantPool.setCurrentPool(null);
        }


    // ----- data members --------------------------------------------------------------------------

    /**
     * Current compilation stage.
     */
    private Stage m_stage = Stage.Initial;

    /**
     * The module repository to use.
     */
    private final ModuleRepository m_repos;

    /**
     * The TypeCompositionStatement for the module being compiled. This is an object returned from
     * the Parser, or one assembled from multiple objects returned from the Parser.
     */
    private TypeCompositionStatement m_stmtModule;

    /**
     * The ErrorListener to report errors to.
     */
    private final ErrorListener m_errs;

    /**
     * The FileStructure that this compiler is putting together in a series of passes.
     */
    private FileStructure m_structFile;

    /**
     * Within a compiler stage that may not complete in a single pass, a manager is responsible for
     * getting all of the nodes to complete that stage.
     */
    private StageMgr m_mgr;


    // ----- inner class: Stage enumeration --------------------------------------------------------

    /**
     * The stages of compilation.
     */
    public enum Stage
        {
        Initial,
        Registering,
        Registered,
        Loading,
        Loaded,
        Resolving,
        Resolved,
        Validating,
        Validated,
        Emitting,
        Emitted,
        Discarded;

        /**
         * @return true if this stage is a "target-able" stage, i.e. a stage that a node can be
         *         asked to process towards
         */
        public boolean isTargetable()
            {
            ensureValid();

            // the even ordinals are targets
            int n = ordinal();
            return (n & 0x1) == 0 && n > 0;
            }

        /**
         * @return true if this stage is a intermediate stage, i.e. indicating that a node is in
         *         the process of moving towards a target-able stage
         */
        public boolean isTransition()
            {
            ensureValid();

            // the odd ordinals are intermediates
            return (ordinal() & 0x1) == 1;
            }

        /**
         * @return the transition stage related to this stage
         */
        public Stage getTransitionStage()
            {
            ensureValid();
            return isTransition()
                    ? this
                    : prev();
            }

        /**
         * Determine if this stage is at least as far along as that stage.
         *
         * @param that  another Stage
         *
         * @return true iff this Stage is at least as advanced as that stage
         */
        public boolean isAtLeast(Stage that)
            {
            ensureValid();
            assert that.isTargetable();
            return this.compareTo(that) >= 0;
            }

        /**
         * Make sure that the stage is not Discarded.
         */
        public void ensureValid()
            {
            if (this == Discarded)
                {
                throw new IllegalStateException();
                }
            }

        /**
         * @return the Stage that comes before this Stage
         */
        public Stage prev()
            {
            ensureValid();
            return Stage.valueOf(this.ordinal() - 1);
            }

        /**
         * @return the Stage that comes after this Stage
         */
        public Stage next()
            {
            ensureValid();
            return Stage.valueOf(this.ordinal() + 1);
            }

        /**
         * @return the first "target-able" Stage that comes before this Stage
         */
        public Stage prevTarget()
            {
            Stage that = prev();
            while (!that.isTargetable())
                {
                that = that.prev();
                }
            return that;
            }

        /**
         * @return the first "target-able" Stage that comes after this Stage
         */
        public Stage nextTarget()
            {
            Stage that = next();
            while (!that.isTargetable())
                {
                that = that.next();
                }
            return that;
            }

        /**
         * Look up a Stage enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Stage enum for the specified ordinal
         */
        public static Stage valueOf(int i)
            {
            if (i >= 0 && i < STAGES.length)
                {
                return STAGES[i];
                }

            throw new IllegalArgumentException("no such stage ordinal: " + i);
            }

        /**
         * All of the Stage enums.
         */
        private static final Stage[] STAGES = Stage.values();
        }


    // ----- compiler errors -----------------------------------------------------------------------

    /**
     * Unknown fatal error.
     */
    public static final String FATAL_ERROR                        = "COMPILER-01";
    /**
     * Cannot nest a module.
     */
    public static final String MODULE_UNEXPECTED                  = "COMPILER-02";
    /**
     * Cannot nest a package.
     */
    public static final String PACKAGE_UNEXPECTED                 = "COMPILER-03";
    /**
     * Cannot nest a class etc.
     */
    public static final String CLASS_UNEXPECTED                   = "COMPILER-04";
    /**
     * Another property by the same name exists.
     */
    public static final String PROP_DUPLICATE                     = "COMPILER-05";
    /**
     * Cannot nest a property.
     */
    public static final String PROP_UNEXPECTED                    = "COMPILER-06";
    /**
     * Illegal module name.
     */
    public static final String MODULE_BAD_NAME                    = "COMPILER-07";
    /**
     * Duplicate modifier.
     */
    public static final String DUPLICATE_MODIFIER                 = "COMPILER-08";
    /**
     * Illegal modifier.
     */
    public static final String ILLEGAL_MODIFIER                   = "COMPILER-09";
    /**
     * Conflicting modifier.
     */
    public static final String CONFLICTING_MODIFIER               = "COMPILER-10";
    /**
     * More than one "extends" clause.
     */
    public static final String MULTIPLE_EXTEND_CLAUSES            = "COMPILER-11";
    /**
     * Illegal / unexpected type parameters.
     */
    public static final String TYPE_PARAMS_UNEXPECTED             = "COMPILER-12";
    /**
     * Illegal / unexpected constructor parameters.
     */
    public static final String CONSTRUCTOR_PARAMS_UNEXPECTED      = "COMPILER-13";
    /**
     * Illegal / unexpected constructor parameters.
     */
    public static final String CONSTRUCTOR_PARAM_DEFAULT_REQUIRED = "COMPILER-14";
    /**
     * Unexpected keyword.
     */
    public static final String KEYWORD_UNEXPECTED                 = "COMPILER-15";
    /**
     * Inner const class must be declared static if its outer class is not a const.
     */
    public static final String INNER_CONST_NOT_STATIC             = "COMPILER-16";
    /**
     * Inner service class must be declared static if its outer class is not a const or service.
     */
    public static final String INNER_SERVICE_NOT_STATIC           = "COMPILER-17";
    /**
     * Wrong number of type parameter values.
     */
    public static final String TYPE_PARAMS_MISMATCH               = "COMPILER-18";
    /**
     * Type parameter name is a duplicate.
     */
    public static final String DUPLICATE_TYPE_PARAM               = "COMPILER-19";
    /**
     * More than one "import" clause.
     */
    public static final String MULTIPLE_IMPORT_CLAUSES            = "COMPILER-20";
    /**
     * More than one "into" clause.
     */
    public static final String MULTIPLE_INTO_CLAUSES              = "COMPILER-21";
    /**
     * Package cannot have both body and "import" clause.
     */
    public static final String IMPURE_MODULE_IMPORT               = "COMPILER-22";
    /**
     * A conditional is not allowed on this structure.
     */
    public static final String CONDITIONAL_NOT_ALLOWED            = "COMPILER-23";
    /**
     * Cannot find a module.
     */
    public static final String MODULE_MISSING                     = "COMPILER-24";
    /**
     * Conflicting version clauses.
     */
    public static final String CONFLICTING_VERSIONS               = "COMPILER-25";
    /**
     * Conflicting import composition when importing one's own module.
     */
    public static final String ILLEGAL_SELF_IMPORT                = "COMPILER-26";
    /**
     * Illegal link-time conditional.
     */
    public static final String ILLEGAL_CONDITIONAL                = "COMPILER-27";
    /**
     * Duplicate import with the same alias.
     */
    public static final String DUPLICATE_IMPORT                   = "COMPILER-28";
    /**
     * Import cannot be conditional; condition ignored.
     */
    public static final String CONDITIONAL_IMPORT                 = "COMPILER-29";
    /**
     * Unresolvable names.
     */
    public static final String INFINITE_RESOLVE_LOOP              = "COMPILER-30";
    /**
     * Name collision. For example, anything named "ecstasy" nested under a module, or a property
     * that has the same name as a type parameter or method, etc.
     */
    public static final String NAME_COLLISION                     = "COMPILER-31";
    /**
     * Not a class type.
     */
    public static final String NOT_CLASS_TYPE                     = "COMPILER-32";
    /**
     * Cannot nest a method.
     */
    public static final String METHOD_UNEXPECTED                  = "COMPILER-33";
    /**
     * Cannot nest a typedef.
     */
    public static final String TYPEDEF_UNEXPECTED                 = "COMPILER-34";
    /**
     * Cannot have an annotation here.
     */
    public static final String ANNOTATION_UNEXPECTED              = "COMPILER-35";
    /**
     * Could not find name "{0}" within "{1}".
     */
    public static final String NAME_MISSING                       = "COMPILER-36";
    /**
     * Found name within context, but name is ambiguous.
     */
    public static final String NAME_AMBIGUOUS                     = "COMPILER-37";
    /**
     * Cannot find name.
     */
    public static final String NAME_UNRESOLVABLE                  = "COMPILER-38";
    /**
     * Cannot hide name.
     */
    public static final String NAME_UNHIDEABLE                    = "COMPILER-39";
    /**
     * Return is supposed to be void.
     */
    public static final String RETURN_VOID                        = "COMPILER-40";
    /**
     * Return is supposed to be non-void.
     */
    public static final String RETURN_EXPECTED                    = "COMPILER-41";
    /**
     * Return has the wrong number of arguments: {0} expected, {1} found.
     */
    public static final String RETURN_WRONG_COUNT                 = "COMPILER-42";
    /**
     * Type mismatch: {0} expected, {1} found.
     */
    public static final String WRONG_TYPE                         = "COMPILER-43";
    /**
     * Wrong number of values: {0} expected, {1} found.
     */
    public static final String WRONG_TYPE_ARITY                   = "COMPILER-44";
    /**
     * Value of type {0} is out of range: {1}.
     */
    public static final String VALUE_OUT_OF_RANGE                 = "COMPILER-45";
    /**
     * Statement is not reachable.
     */
    public static final String NOT_REACHABLE                      = "COMPILER-46";
    /**
     * Expression does not evaluate to a constant value.
     */
    public static final String CONSTANT_REQUIRED                  = "COMPILER-47";
    /**
     * A value is required.
     */
    public static final String VALUE_REQUIRED                     = "COMPILER-48";
    /**
     * Return is missing.
     */
    public static final String RETURN_REQUIRED                    = "COMPILER-49";
    /**
     * Invalid operation.
     */
    public static final String INVALID_OPERATION                  = "COMPILER-50";
    /**
     * Variable {0} is already defined.
     */
    public static final String VAR_DEFINED                        = "COMPILER-51";
    /**
     * There is no "this".
     */
    public static final String NO_THIS                            = "COMPILER-52";
    /**
     * There is no "super".
     */
    public static final String NO_SUPER                           = "COMPILER-53";
    /**
     * Unexpected redundant return type information.
     */
    public static final String UNEXPECTED_REDUNDANT_RETURNS       = "COMPILER-54";
    /**
     * Method or function type requires complete parameter and return type information.
     */
    public static final String MISSING_PARAM_INFORMATION          = "COMPILER-55";
    /**
     * Could not find a matching method or function "{0}".
     */
    public static final String MISSING_METHOD                     = "COMPILER-56";
    /**
     * Could not find an "outer this" named "{0}".
     */
    public static final String MISSING_RELATIVE                   = "COMPILER-57";
    /**
     * Unexpected method name "{0}" encountered.
     */
    public static final String UNEXPECTED_METHOD_NAME             = "COMPILER-58";
    /**
     * The ".this" suffix must follow a parent class or parent property identity.
     */
    public static final String INVALID_OUTER_THIS                 = "COMPILER-59";
    /**
     * Because a previous argument specified a parameter name, the argument {0} must specify a parameter name.
     */
    public static final String ARG_NAME_REQUIRED                  = "COMPILER-60";
    /**
     * No matching annotation constructor for {0}.
     */
    public static final String ANNOTATION_DECL_UNRESOLVABLE       = "COMPILER-61";
    /**
     * No-parameter constructor required for {0}.
     */
    public static final String DEFAULT_CONSTRUCTOR_REQUIRED       = "COMPILER-62";
    /**
     * Signature {0} is ambiguous.
     */
    public static final String SIGNATURE_AMBIGUOUS                = "COMPILER-63";
    /**
     * Type {0} has more than one default value for the type.
     */
    public static final String DUPLICATE_DEFAULT_VALUE            = "COMPILER-64";
    /**
     * Could not find a matching constructor for type "{0}".
     */
    public static final String MISSING_CONSTRUCTOR                = "COMPILER-65";
    /**
     * Tuple type has an unexpected number of field types; {0} expected, {1} found.
     */
    public static final String TUPLE_TYPE_WRONG_ARITY             = "COMPILER-66";
    /**
     * Expression yields the type "{1}" that does not support the "{0}" operator.
     */
    public static final String MISSING_OPERATOR                   = "COMPILER-67";
    /**
     * Expression yields the type "{1}" that does not support the "{0}" operator with the {2} specified parameters.
     */
    public static final String MISSING_OPERATOR_SIGNATURE         = "COMPILER-68";
    /**
     * The use of the "{0}" operator does not resolve to a single, unambiguous operator implementation on the type "{1}".
     */
    public static final String AMBIGUOUS_OPERATOR_SIGNATURE       = "COMPILER-69";
    /**
     * The expression cannot be assigned to.
     */
    public static final String ASSIGNABLE_REQUIRED                = "COMPILER-70";
    /**
     * The left-hand-side of the Elvis expression is not nullable.
     */
    public static final String ELVIS_NOT_NULLABLE                 = "COMPILER-71";
    /**
     * The left-hand-side of the Elvis expression is only nullable.
     */
    public static final String ELVIS_ONLY_NULLABLE                = "COMPILER-72";
    /**
     * Short-circuiting expressions are not allowed in this context.
     */
    public static final String SHORT_CIRCUIT_ILLEGAL              = "COMPILER-73";
    /**
     * The expression on the left-hand-side of the colon does not have the potential to use the expression on the right-hand-side.
     */
    public static final String SHORT_CIRCUIT_REQUIRED             = "COMPILER-74";
    /**
     * A "switch" can only contain one "default" statement.
     */
    public static final String SWITCH_DEFAULT_DUPLICATE           = "COMPILER-75";
    /**
     * A "switch" expression must contain a "default" statement.
     */
    public static final String SWITCH_DEFAULT_REQUIRED            = "COMPILER-76";
    /**
     * The "switch" contains more than one "case" statement for the value: {0}.
     */
    public static final String SWITCH_CASE_DUPLICATE              = "COMPILER-77";
    /**
     * A "switch" requires a constant value for the "case" statement.
     */
    public static final String SWITCH_CASE_CONSTANT_REQUIRED      = "COMPILER-78";
    /**
     * A "switch" must begin with a "case" statement.
     */
    public static final String SWITCH_CASE_EXPECTED               = "COMPILER-79";
    /**
     * A "switch" expression cannot end with a "case" statement.
     */
    public static final String SWITCH_CASE_DANGLING               = "COMPILER-80";
    /**
     * The variable {0} is not definitely assigned.
     */
    public static final String VAR_UNASSIGNED                     = "COMPILER-81";
    /**
     * The variable {0} cannot be assigned to.
     */
    public static final String VAR_ASSIGNMENT_ILLEGAL             = "COMPILER-82";
    /**
     * Name required.
     */
    public static final String NAME_REQUIRED                      = "COMPILER-83";
    /**
     * Wrong number of arguments: {0} expected, {1} found.
     */
    public static final String ARGUMENT_WRONG_COUNT               = "COMPILER-84";
    /**
     * Parameter name is a duplicate: {0}.
     */
    public static final String DUPLICATE_PARAMETER                = "COMPILER-85";
    /**
     * Parameter types must be specified.
     */
    public static final String PARAMETER_TYPES_REQUIRED           = "COMPILER-86";
    /**
     * Auto-narrowing override ('!') is not allowed.
     */
    public static final String AUTO_NARROWING_ILLEGAL             = "COMPILER-87";
    /**
     * Could not find the specified label "{0}".
     */
    public static final String MISSING_GROUND_LABEL               = "COMPILER-88";
    /**
     * Could not find an enclosing "for", "do", "while", or "switch" statement.
     */
    public static final String MISSING_GROUND_STATEMENT           = "COMPILER-89";
    /**
     * A "continue" statement can only be applied to a "for", "do", "while", or "switch" statement.
     */
    public static final String ILLEGAL_CONTINUE_TARGET            = "COMPILER-90";
    /**
     * {0} is not yet implemented.
     */
    public static final String NOT_IMPLEMENTED                    = "COMPILER-NI";
    }
