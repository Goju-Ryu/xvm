package org.xvm.compiler.ast;


import java.io.PrintWriter;
import java.io.StringWriter;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Source;

import org.xvm.util.ListMap;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.indentLines;


/**
 * Common base class for all statements and expressions.
 */
public abstract class AstNode
        implements Cloneable
    {
    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Obtain the AstNode that contains this node. A parent is configured by the scan phase.
     *
     * @return  the parent node, or null
     */
    public AstNode getParent()
        {
        return m_parent;
        }

    /**
     * Specify a parent for the AstNode.
     *
     * @param parent  the parent node
     */
    protected void setParent(AstNode parent)
        {
        this.m_parent = parent;
        }

    /**
     * This method recurses through the tree of AstNode objects, allowing each node to introduce
     * itself as the parent of each node under it.
     */
    protected void introduceParentage()
        {
        for (AstNode node : children())
            {
            node.setParent(this);
            node.introduceParentage();
            }
        }

    /**
     * Helper: Given an optional Iterable of child AstNode objects, set the parent of each of the
     * children to be this node.
     *
     * @param children  an Iterable of AstNode, or null
     */
    protected void adopt(Iterable<? extends AstNode> children)
        {
        if (children != null)
            {
            for (AstNode child : children)
                {
                child.setParent(this);
                }
            }
        }

    /**
     * Helper: Given an optional AstNode object, set its parent to this node.
     *
     * @param child  an AstNode, or null
     *
     * @return the same child as passed, which may be null
     */
    protected <T extends AstNode> T adopt(T child)
        {
        if (child != null)
            {
            child.setParent(this);
            }

        return child;
        }

    /**
     * Return an Iterable/Iterator that represents all of the child nodes of this node.
     *
     * @return an Iterable of child nodes (from whence an Iterator can be obtained)
     */
    public ChildIterator children()
        {
        Field[] fields = getChildFields();
        return fields.length == 0 ? ChildIterator.EMPTY : new ChildIteratorImpl(fields);
        }

    /**
     * Replace the specified child of this AstNode with a new child.
     *
     * @param nodeOld  the child to replace
     * @param nodeNew  the new child
     */
    public void replaceChild(AstNode nodeOld, AstNode nodeNew)
        {
        ChildIterator children = children();
        for (AstNode node : children)
            {
            if (node == nodeOld)
                {
                children.replaceWith(adopt(nodeNew));
                return;
                }
            }
        throw new IllegalStateException("no such child \"" + nodeOld + "\" on \"" + this + '\"');
        }

    /**
     * @return an array of fields on this AstNode that contain references to child AstNodes
     */
    protected Field[] getChildFields()
        {
        return NO_FIELDS;
        }

    @Override
    public AstNode clone()
        {
        AstNode that;
        try
            {
            that = (AstNode) super.clone();
            }
        catch (CloneNotSupportedException e)
            {
            throw new IllegalStateException(e);
            }

        for (Field field : getChildFields())
            {
            Object oVal;
            try
                {
                oVal = field.get(this);
                }
            catch (NullPointerException e)
                {
                throw new IllegalStateException("class=" + this.getClass().getSimpleName(), e);
                }
            catch (IllegalAccessException e)
                {
                throw new IllegalStateException(e);
                }

            if (oVal != null)
                {
                if (oVal instanceof AstNode)
                    {
                    AstNode nodeNew = ((AstNode) oVal).clone();

                    that.adopt(nodeNew);
                    oVal = nodeNew;
                    }
                else if (oVal instanceof List)
                    {
                    List<AstNode>      listOld = (List<AstNode>) oVal;
                    ArrayList<AstNode> listNew = new ArrayList<>();
                    for (AstNode node : listOld)
                        {
                        listNew.add(node.clone());
                        }

                    that.adopt(listNew);
                    oVal = listNew;
                    }
                else
                    {
                    throw new IllegalStateException(
                            "unsupported container type: " + oVal.getClass().getSimpleName());
                    }

                try
                    {
                    field.set(that, oVal);
                    }
                catch (IllegalAccessException e)
                    {
                    throw new IllegalStateException(e);
                    }
                }
            }

        return that;
        }

    /**
     * @return the current compilation stage for this node
     */
    public Stage getStage()
        {
        return m_stage;
        }

    /**
     * Test if the node has reached the specified stage.
     *
     * @param stage  the compilation stage to test for
     *
     * @return true if the node has already reached or passed the specified stage
     */
    protected boolean alreadyReached(Stage stage)
        {
        assert stage != null;
        return getStage().compareTo(stage) >= 0;
        }

    /**
     * Verify that the node has reached the specified stage.
     *
     * @param stage  the stage that the node must have already reached
     *
     * @throws IllegalStateException  if the node has not reached the specified stage
     */
    protected void ensureReached(Stage stage)
        {
        if (!alreadyReached(stage))
            {
            // TODO remove when done
            notImplemented();

            throw new IllegalStateException("Stage=" + getStage() + " (expected: " + stage + ")");
            }
        }

    /**
     * Update the stage to the specified stage, if the specified stage is later than the current
     * stage.
     *
     * @param stage  the suggested stage
     */
    protected void setStage(Stage stage)
        {
        // stage is a "one way" attribute
        if (stage != null && stage.compareTo(m_stage) > 0)
            {
            m_stage = stage;
            }
        }

    /**
     * Obtain the Source for this AstNode, if any. By default, a node uses the same source as its
     * parent.
     *
     * @return a Source instance
     */
    public Source getSource()
        {
        AstNode parent = getParent();
        return parent == null
                ? null
                : parent.getSource();
        }

    /**
     * Determine the starting position in the source at which this AstNode occurs.
     *
     * @return the Source position of the AstNode
     */
    public abstract long getStartPosition();

    /**
     * Determine the ending position (exclusive) in the source for this AstNode.
     *
     * @return the Source position of the end of the AstNode
     */
    public abstract long getEndPosition();

    /**
     * @return true iff this node holds a component
     */
    public boolean isComponentNode()
        {
        return false;
        }

    /**
     * Obtain the Component for this AstNode, if any.
     *
     * @return the Component containing this AstNode
     */
    public Component getComponent()
        {
        AstNode parent = getParent();
        return parent == null
                ? null
                : parent.getComponent();
        }

    /**
     * @return the "compilation container" for a statement or expression, which is the method
     *         ({@link MethodDeclarationStatement}), anonymous inner class ({@link NewExpression}),
     *         lambda function ({@link LambdaExpression}), or "inlined" lambda
     *         ({@link StatementExpression})
     */
    protected AstNode getCodeContainer()
        {
        AstNode parent = getParent();
        return parent == null
                ? null
                : parent.getCodeContainer();
        }

    /**
     * Code Container method: TODO
     *
     * @return the required return types from the code container, which comes from the signature if
     *         is specified, or from the specified required type during validation, or from the
     *         actual type once the expression is validated
     */
    public TypeConstant[] getReturnTypes()
        {
        throw notCodeContainer();
        }

    /**
     * @return true iff the code container has a conditional return
     */
    public boolean isReturnConditional()
        {
        throw notCodeContainer();
        }

    /**
     * Code Container method: TODO
     *
     * @param atypeRet  the types being returned
     */
    public void collectReturnTypes(TypeConstant[] atypeRet)
        {
        throw notCodeContainer();
        }

    private RuntimeException notCodeContainer()
        {
        throw new IllegalStateException("not code container: " + this.getClass().getSimpleName());
        }

    /**
     * @return true iff this AstNode (or an AstNode that it contains) references "super"
     */
    protected boolean usesSuper()
        {
        return false;
        }

    /**
     * Test if the specified child is used as an R-Value, which is something that yields a value.
     * <p/>
     * In most cases, an expression is used as an R-Value (i.e. it has a value), but an expression
     * can be used as a left side of an assignment, for example, which makes it an L-Value. In a
     * few cases, an expression can be used as both an R-Value and an L-Value, such as with the
     * pre-/post-increment/-decrement operators.
     *
     * @param exprChild  an expression that is a child of this node
     *
     * @return true iff the child is used as an R-Value
     */
    protected boolean isRValue(Expression exprChild)
        {
        return true;
        }

    /**
     * Test if the specified child is allowed to short-circuit.
     *
     * @param exprChild  an expression that is a child of this node
     *
     * @return true iff the child is allowed to short-circuit
     */
    protected boolean allowsShortCircuit(Expression exprChild)
        {
        return false;
        }

    /**
     * This must be overridden by any AST node that supports short circuiting children.
     *
     * @param exprChild  the child that is requesting the label
     *
     * @return the label to jump to when the expression short-circuits.
     */
    protected Label getShortCircuitLabel(Expression exprChild)
        {
        throw new IllegalStateException(this.getClass().getName());
        }

    /**
     * @return the constant pool
     */
    protected ConstantPool pool()
        {
        AstNode nodeParent = getParent();
        return nodeParent == null
                ? null
                : nodeParent.pool();
        }

    /**
     * For nested nodes, determine the default access if the nodes need to specify an accessibility.
     *
     * @return the accessibility that this node should assume if this node has to specify its own
     *         accessibility and no accessibility is specified
     */
    public Access getDefaultAccess()
        {
        AstNode parent = getParent();
        return parent == null
                ? Access.PUBLIC
                : parent.getDefaultAccess();
        }

    /**
     * Helper to log an error related to this AstNode.
     *
     * @param errs        the ErrorListener to log to
     * @param severity    the severity level of the error; one of
     *                    {@link Severity#INFO}, {@link Severity#WARNING},
     *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
     * @param sCode       the error code that identifies the error message
     * @param aoParam     the parameters for the error message; may be null
     *
     * @return true to attempt to abort the process that reported the error, or
     *         false to attempt continue the process
     */
    public boolean log(ErrorListener errs, Severity severity, String sCode, Object... aoParam)
        {
        Source source = getSource();
        return errs == null
                ? severity.ordinal() >= Severity.ERROR.ordinal()
                : errs.log(severity, sCode, aoParam, source,
                source == null ? 0L : getStartPosition(),
                source == null ? 0L : getEndPosition());
        }


    // ----- compile phases ------------------------------------------------------------------------

    /**
     * First logical compiler pass.
     * <p/>
     * <ul>
     * <li>At this point, names are NOT resolvable; we're really just organizing the tree and
     * checking for errors that are obvious from "this point down" (no lateral evaluation of
     * structures, because we can't count on them even existing yet.)</li>
     *
     * <li>The general idea is that this method recurses through the structure, allowing each node
     * to introduce itself as the parent of each node under it, and that the nodes which will be
     * structures in the resulting FileStructure will register themselves.</li>
     *
     * <li>Type parameters for the types must also be registered, because they are also types, and
     * they will be required to already be present when the second pass begins.</li>
     * </ul>
     *
     * @param mgr   the Stage Manager that is conducting the processing
     * @param errs  the error list to log any errors etc. to
     */
    protected void registerStructures(StageMgr mgr, ErrorListener errs)
        {
        }

    /**
     * Second logical compiler pass. This pass has access to imported modules, and is responsible
     * for resolving names.
     * <p/>
     * The rule of thumb is that no questions should be asked of other modules that could not have
     * been answered by this module before this call; in other words, the order of the module
     * compilation is not only unpredictable, but the potential exists for dependencies in either
     * direction (first to last and/or vice versa).
     * <p/>
     * As a result, some questions may come to an AstNode to resolve that it is not yet prepared to
     * resolve, in which case the caller (another AstNode) has to add itself to the list of nodes
     * that require another pass.
     * <p/>
     * <ul>
     * <li>Packages that import modules are able to verify that those modules are available to
     * compile against;</li>
     *
     * <li>Conditionals must be resolvable, e.g. the link-time conditionals defining which types are
     * present and which of their Compositions are in effect.</li>
     * </ul>
     *
     * @param mgr   the Stage Manager that is conducting the processing
     * @param errs  the error list to log any errors etc. to
     */
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        }

    /**
     * Third logical compiler pass. This pass is responsible for resolving types, constant values,
     * and structures within methods. To accomplish this, this pass must be able to resolve type
     * names, which is why the second pass was necessarily a separate pass.
     *
     * @param mgr   the Stage Manager that is conducting the processing
     * @param errs  the error list to log any errors etc. to
     */
    public void validateExpressions(StageMgr mgr, ErrorListener errs)
        {
        }

    /**
     * Fourth logical compiler pass. Emits the resulting, finished structures.
     *
     * @param mgr   the Stage Manager that is conducting the processing
     * @param errs  the error list to log any errors etc. to
     */
    public void generateCode(StageMgr mgr, ErrorListener errs)
        {
        }

    /**
     * Helper to update the line number in the code to the line number on which this AstNode began.
     *
     * @param code  the Code being emitted
     */
    protected void updateLineNumber(Code code)
        {
        code.updateLineNumber(Source.calculateLine(getStartPosition()));
        }

    /**
     * If any of the children of this node have been previously deferred, catch them up now.
     *
     * @param errs  the error list to log to
     *
     * @return true if the children got caught up; false if the catch-up aborted
     */
    protected boolean catchUpChildren(ErrorListener errs)
        {
        // method children are all deferred up until this stage, so we have to "catch them up" at
        // this point, recreating the various compiler stages here
        Stage stageOldest = null;
        for (AstNode node : children())
            {
            Stage stage = node.getStage();
            if (stageOldest == null)
                {
                stageOldest = stage;
                }
            else if (stage.compareTo(stageOldest) < 0)
                {
                stageOldest = stage;
                }
            }
        if (stageOldest == null)
            {
            return true;
            }

        for (Stage stageCurrent = stageOldest.nextTarget(), stageThis = getStage();
                stageCurrent.compareTo(stageThis) < 0;
                stageCurrent = stageCurrent.nextTarget())
            {
            StageMgr mgrKids = new StageMgr(this, stageCurrent, errs);
            while (!mgrKids.processComplete())
                {
                if (errs.isAbortDesired() || mgrKids.getIterations() > 20)
                    {
                    for (AstNode node : mgrKids.takeRevisitList())
                        {
                        // TODO clean up / clarify error logging
                        node.log(errs, Severity.FATAL, org.xvm.compiler.Compiler.INFINITE_RESOLVE_LOOP,
                                node.getComponent().getIdentityConstant().toString());
                        return false;
                        }
                    }
                }
            }

        return true;
        }

    // ----- name resolution -----------------------------------------------------------------------

    /**
     * From the root down, see if one of the parents answers to the specified name.
     *
     * @param sName  a simple name
     *
     * @return the Constant of one of the parents that answers to the specified name, or null
     */
    protected Component resolveParentBySimpleName(String sName)
        {
        AstNode parent = getParent();
        return parent == null ? null : parent.resolveParentBySimpleName(sName);
        }

    /**
     * Determine if this particular node has an import registered on it of the specified name.
     *
     * @param sName  a simple name
     *
     * @return an ImportStatement, or null
     */
    protected ImportStatement resolveImportBySingleName(String sName)
        {
        AstNode parent = getParent();
        return parent == null ? null : parent.resolveImportBySingleName(sName);
        }

    /**
     * @return true iff this AstNode should be able to resolve names
     */
    protected boolean canResolveNames()
        {
        if (this instanceof NameResolver.NameResolving)
            {
            // the problem is this: that a NameResolver that hasn't been invoked as part of the
            // natural pass of the resolveNames() recursion has not had a chance to figure out what
            // the effect its imports may have on name resolution, and thus we can't ask it what a
            // name means
            return !((NameResolver.NameResolving) this).getNameResolver().isFirstTime();
            }

        // for all other components (that don't override this method because they know more about
        // whether or not they can resolve names), we'll assume that if they haven't been resolved,
        // then they don't know how to resolve names
        return alreadyReached(Stage.Resolving);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public abstract String toString();

    public String toDumpString()
        {
        StringWriter sw = new StringWriter();
        dump(new PrintWriter(sw), "", "");
        return sw.toString();
        }

    public void dump()
        {
        dump(new PrintWriter(System.out, true), "", "");
        }

    protected void dump(PrintWriter out, String sIndentFirst, String sIndent)
        {
        // find the children to dump, but prune out any empty categories
        Map<String, Object> cats = getDumpChildren();
        for (Iterator<Map.Entry<String, Object>> iter = cats.entrySet().iterator(); iter.hasNext(); )
            {
            Map.Entry entry = iter.next();
            Object    value = entry.getValue();
            if (value == null)
                {
                iter.remove();
                }
            else if (value instanceof Map)
                {
                if (((Map) value).isEmpty())
                    {
                    iter.remove();
                    }
                }
            else if (value instanceof Collection)
                {
                if (((Collection) value).isEmpty())
                    {
                    iter.remove();
                    }
                }
            else if (value instanceof Collection)
                {
                if (((Collection) value).isEmpty())
                    {
                    iter.remove();
                    }
                }
            else if (value instanceof Object[])
                {
                if (((Object[]) value).length == 0)
                    {
                    iter.remove();
                    }
                }
            }

        // print out a line of info about this node (if more than one line is necessary, then indent
        // the whole thing under the top line)
        if (sIndentFirst.length() > 0)
            {
            out.print(sIndentFirst + "- ");
            }
        out.print(getClass().getSimpleName());

        String sThis = getDumpDesc();
        if (sThis == null || sThis.length() == 0)
            {
            out.println();
            }
        else if (sThis.indexOf('\n') < 0)
            {
            out.println(": " + sThis);
            }
        else
            {
            out.println();
            out.println(indentLines(sThis, sIndent + (cats.isEmpty() ? "      " : " |    ")));
            }

        // for each category, print the category name, then print the child nodes under it; if
        // there's only one child node, then just stick it on the same line
        String sIndentCat   = sIndent + "   |- ";
        String sIndentKid   = sIndent + "   |   |";         // all kids except last cat
        String sIndentLastC = sIndent + "       |";         // last cat kids except last kid
        String sIndentLastK = sIndent + "        ";         // last kid on last cat
        int cCats = 0;
        for (Iterator<Map.Entry<String, Object>> iterC = cats.entrySet().iterator(); iterC.hasNext(); )
            {
            boolean                   fLastC = (++cCats == cats.size());
            Map.Entry<String, Object> entry  = iterC.next();
            String                    sCat   = entry.getKey();
            Object                    value  = entry.getValue();

            // category name
            out.print(sIndentCat + sCat);

            // find the kids
            int      cKids;
            Iterator iterK;
            if (value instanceof Map)
                {
                Map kids = (Map) value;
                cKids = kids.size();
                iterK = kids.entrySet().iterator();
                }
            else if (value instanceof Collection)
                {
                Collection kids = (Collection) value;
                cKids = kids.size();
                iterK = kids.iterator();
                }
            else if (value instanceof Object[])
                {
                Object[] kids = (Object[]) value;
                cKids = kids.length;
                iterK = Arrays.asList(kids).iterator();
                }
            else
                {
                cKids = 1;
                iterK = Collections.singletonList(value).iterator();
                }

            for (int i = 0; i < cKids; ++i)
                {
                Object  kid      = iterK.next();
                boolean fFirstK  = (i == 0);
                boolean fLastK   = (i == cKids - 1);
                String  sIndent1 = fLastC ? sIndentLastC : sIndentKid;
                String  sIndent2 = fLastC ? (fLastK ? sIndentLastK : sIndentLastC) : sIndentKid;

                if (kid instanceof AstNode)
                    {
                    if (fFirstK)
                        {
                        out.println();
                        }
                    ((AstNode) kid).dump(out, sIndent1, sIndent2);
                    }
                else if (kid instanceof Map.Entry)
                    {
                    if (fFirstK)
                        {
                        out.println();
                        }
                    throw new UnsupportedOperationException("TODO");
                    }
                else // any old object
                    {
                    String sKid = String.valueOf(kid);
                    if (sKid.indexOf('\n') < 0)
                        {
                        if (cKids == 1)
                            {
                            out.print(": ");
                            }
                        else
                            {
                            if (fFirstK)
                                {
                                out.println();
                                }
                            out.print(sIndent1 + "- ");
                            }
                        out.println(sKid);
                        }
                    else
                        {
                        if (fFirstK)
                            {
                            out.println();
                            }
                        sKid = indentLines(sKid, sIndent2 + "  ");
                        sKid = sIndent1 + "- " + sKid.substring(sIndent1.length() + 2);
                        out.println(sKid);
                        }
                    }
                }
            }
        }

    public String getDumpDesc()
        {
        return null;
        }

    /**
     * Build and return a map that allows the caller to navigate the children of this node.
     * <p/>
     * Assume some type T which represents either an AstNode instance, or an object that implements
     * toString(). The keys of the map should be strings that describe the categories of the
     * children, while the values should provide the info about the children of this AstNode,
     * either as an object of type T, a Collection of type T, an array of type T, or a Map whose
     * keys and values are of type T.
     *
     * @return a map containing all the child information to dump
     */
    public Map<String, Object> getDumpChildren()
        {
        Field[] fields = getChildFields();
        if (fields.length == 0)
            {
            return Collections.EMPTY_MAP;
            }

        Map<String, Object> map = new ListMap<>();
        for (Field field : fields)
            {
            try
                {
                map.put(field.getName(), field.get(this));
                }
            catch (IllegalAccessException e)
                {
                throw new IllegalStateException(e);
                }
            }
        return map;
        }


    // ----- internal -------------------------------------------------------------------

    /**
     * TODO delete this method when done
     *
     * @return nothing, because the method always throws
     * @throws UnsupportedOperationException this exception is always thrown by this method
     */
    protected UnsupportedOperationException notImplemented()
        {
        throw new UnsupportedOperationException("not implemented by: " + this.getClass().getSimpleName());
        }

    /**
     * Ensure that the passed list is an ArrayList, replacing it with an ArrayList if necessary.
     *
     * @param list  a List
     *
     * @return an ArrayList
     */
    protected static <T> ArrayList<T> ensureArrayList(List<T> list)
        {
        return list instanceof ArrayList
                ? (ArrayList<T>) list
                : new ArrayList<T>(list);
        }

    /**
     * Collect fields by name.
     *
     * @param clz    the class on which the fields exist
     * @param names  the field names
     *
     * @return an array of fields corresponding to the specified names on the specified class
     */
    protected static Field[] fieldsForNames(Class clz, String... names)
        {
        if (names == null || names.length == 0)
            {
            return NO_FIELDS;
            }

        Field[] fields = new Field[names.length];
        NextField: for (int i = 0, c = fields.length; i < c; ++i)
            {
            Class                clzTry = clz;
            NoSuchFieldException eOrig  = null;
            while (clzTry != null)
                {
                try
                    {
                    Field field = clzTry.getDeclaredField(names[i]);
                    assert field != null;
                    if (!field.getType().isInstance(AstNode.class) && field.getType().isInstance(List.class))
                        {
                        throw new IllegalStateException("unsupported field type "
                                + field.getType().getSimpleName() + " on field "
                                + clzTry.getSimpleName() + '.' + names[i]);
                        }
                    fields[i] = field;
                    continue NextField;
                    }
                catch (NoSuchFieldException e)
                    {
                    if (eOrig == null)
                        {
                        eOrig = e;
                        }

                    clzTry = clzTry.getSuperclass();
                    if (clz == null)
                        {
                        throw new IllegalStateException(eOrig);
                        }
                    }
                catch (SecurityException e)
                    {
                    throw new IllegalStateException(e);
                    }
                }
            }

        return fields;
        }


    // ----- inner class: ChildIterator ------------------------------------------------------------

    /**
     * Represents an Iterator that can also replace the most recently iterated element.
     */
    public interface ChildIterator
            extends Iterable<AstNode>, Iterator<AstNode>
        {
        @Override
        default Iterator<AstNode> iterator()
            {
            return this;
            }

        /**
         * Replace the most recently returned node with the specified new node.
         *
         * @param nodeNew  the node to use as a replacement for the node most recently returned from
         *                 the {@link #next()} method
         */
        default void replaceWith(AstNode nodeNew)
            {
            throw new IllegalStateException();
            }

        ChildIterator EMPTY = new ChildIterator()
            {
            @Override
            public boolean hasNext()
                {
                return false;
                }

            @Override
            public AstNode next()
                {
                throw new NoSuchElementException();
                }
            };
        }

    protected final class ChildIteratorImpl
            implements ChildIterator
        {
        /**
         * Construct a ChildIterator that will iterate all of the children that are held in the
         * specified fields, which are either AstNodes themselves, or are container types thereof.
         *
         * @param fields  an array of fields of the AstNode
         */
        protected ChildIteratorImpl(Field[] fields)
            {
            this.fields = fields;
            }

        public boolean hasNext()
            {
            return state == HAS_NEXT || prepareNextElement();
            }

        public AstNode next()
            {
            if (state == HAS_NEXT || prepareNextElement())
                {
                state = HAS_PREV;
                if (value instanceof AstNode)
                    {
                    return (AstNode) value;
                    }
                else
                    {
                    return (AstNode) ((Iterator) value).next();
                    }
                }

            throw new NoSuchElementException();
            }

        private boolean prepareNextElement()
            {
            if (value instanceof Iterator && ((Iterator) value).hasNext())
                {
                state = HAS_NEXT;
                return true;
                }

            boolean prepped = prepareNextField();
            state = prepped ? HAS_NEXT : NOT_PREP;
            return prepped;
            }

        private boolean prepareNextField()
            {
            while (++iField < fields.length)
                {
                Object next;
                try
                    {
                    next = fields[iField].get(AstNode.this);
                    }
                catch (NullPointerException e)
                    {
                    throw new IllegalStateException(
                            "class=" + AstNode.this.getClass().getSimpleName()
                                    + ", field=" + iField);
                    }
                catch (IllegalAccessException e)
                    {
                    throw new IllegalStateException(e);
                    }

                if (next != null)
                    {
                    if (next instanceof List)
                        {
                        List list = (List) next;
                        if (!list.isEmpty())
                            {
                            value = list.iterator();
                            return true;
                            }
                        }
                    else if (next instanceof Collection)
                        {
                        Collection coll = (Collection) next;
                        if (!coll.isEmpty())
                            {
                            value = coll.iterator();
                            return true;
                            }
                        }
                    else
                        {
                        value = next;
                        return true;
                        }
                    }
                }

            value = null;
            return false;
            }

        public void remove()
            {
            if (state == HAS_PREV)
                {
                if (value instanceof AstNode)
                    {
                    // null out the field
                    try
                        {
                        fields[iField].set(AstNode.this, null);
                        }
                    catch (IllegalAccessException e)
                        {
                        throw new IllegalStateException(e);
                        }
                    state = NOT_PREP;
                    return;
                    }

                if (value instanceof Iterator)
                    {
                    // tell the underlying iterator to remove the value
                    ((Iterator) value).remove();
                    state = NOT_PREP;
                    return;
                    }
                }

            throw new IllegalStateException();
            }

        public void replaceWith(AstNode newChild)
            {
            if (state == HAS_PREV)
                {
                if (value instanceof AstNode)
                    {
                    // the field holds a single node; store the new value
                    try
                        {
                        fields[iField].set(AstNode.this, newChild);
                        }
                    catch (IllegalAccessException e)
                        {
                        throw new IllegalStateException(e);
                        }
                    return;
                    }

                if (value instanceof ListIterator)
                    {
                    ((ListIterator) value).set(newChild);
                    return;
                    }
                }

            throw new IllegalStateException();
            }

        private static final int NOT_PREP = 0;
        private static final int HAS_NEXT = 1;
        private static final int HAS_PREV = 2;

        private Field[] fields;
        private int iField = -1;
        private Object value;
        private int state = NOT_PREP;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Constant empty array  of fields.
     */
    protected static final Field[] NO_FIELDS = new Field[0];

    /**
     * The stage of compilation.
     */
    private Stage m_stage = Stage.Initial;

    /**
     * The parent of this AstNode.
     */
    private AstNode m_parent;
    }
