/**
 * A Type is an object that represents an Ecstasy data type. The Type class itself is abstract,
 * but it has a number of well-known concrete implementations.
 *
 * At one level, a data type is simply a set of capabilities, and can be thought of as a set of
 * properties and methods. Simplifying further, a property can be thought of as a method that
 * returns a reference; as a result, a type can be thought of as simply a set of methods.
 *
 * A _parameterized type_ is a type that originates from the formal type of a parameterized class. A
 * parameterized type has a number of named _type parameters_, each of which indicates a particular
 * type that is associated with the name. A type parameter whose value is unknown is _unresolved_,
 * and a type that has one or more unresolved type parameters is an _unresolved type_.
 *
 * A type also provides runtime support for relational operators, such as equality ({@code ==}),
 * inequality ({@code !=}), less-than ({@code <}), less-than-or-equal ({@code <=}), greater-than
 * ({@code >}), greater-than-or-equal ({@code >=}), assignability testing ({@code instanceof}), and
 * the ordering operator ({@code <=>}, also known as _the spaceship operator_). Equality (and thus
 * inequality) for a particular type can be determined using the function provided by the {@link
 * compareForEquality} property. In addition to also providing information on equality and
 * inequality, the {@link compareForOrder} property provides a function that supports all of the
 * ordering-related operators. Lastly, the type's {@link isInstance} method  supports the
 * {@code instanceof} relational operator, answering the question of whether or not the specified
 * object is either assignable to (i.e. _assignment-compatible with_) or castable to this type.
 *
 * A type can also represent an option (a selection) of two of more types, as if the type were
 * _"any one of"_ a set of types. Such a type has two specific attributes:
 * # A set of types; and
 * # A set of methods that reflects the intersection of the sets of methods from each of those
 *   types.
 *
 * Unfortunately, Type cannot be declared as a {@code const} because of the potential for circular
 * references. (The property values of a {@code const} are fully known and immutable before the
 * {@code const} object even has a "{@code this}"; as a result, it is impossible to create circular
 * references using {@code const} classes.)
 */
class Type<DataType>
        implements Const, ConstAble
    {
    // ----- primary state -------------------------------------------------------------------------

    /**
     * Obtain the raw set of all methods on the type. This includes methods that represent
     * properties.
     */
    Method[] allMethods;

    /**
     * A type can be explicitly immutable. An object can only be assigned to an explicitly immutable
     * type if the object is immutable.
     */
    Boolean explicitlyImmutable;

    // ----- calculated properties -----------------------------------------------------------------

    /**
     * The type's methods (all of them, including those that represent properties), by name.
     */
    @lazy Map<String, MultiMethod> allMethodsByName.calc()
        {
        assert meta.immutable_;

        ListMap<String, Method> map = new ListMap();
        for (Method method : allMethods)
            {
            if (MultiMethod multi : map.get(method.name))
                {
                map.put(method.name, multi.add(method));
                }
            else
                {
                map.put(method.name, new MultiMethod(method.to<Method[]>()));
                }
            }

        return map.makeConst();
        }

    /**
     * Obtain the set of properties that exist on the type.
     */
    @lazy Property[] properties.calc()
        {
        assert meta.immutable_;

        Property[] list = new Property[];
        for (Method method : allMethods)
            {
            Property? property = method.property;
            if (property?)
                {
                list += property;
                }
            }
        return list;
        }

    @lazy Map<String, Property> propertiesByName.calc()
        {
        assert meta.immutable_;

        Map<String, Property> map = new ListMap<>();
        for (Property prop : properties)
            {
            map[prop.name] = prop;
            }
        return map;
        }

    /**
     * Obtain the set of methods on the type that are not present to represent a property. These
     * methods are what developers think of as _methods_.
     */
    @lazy Method[] methods.calc()
        {
        Method[] list = new Method[];
        for (Method method : allMethods)
            {
            if (method.property == null)
                {
                list += method;
                }
            }
        return list;
        }

    @lazy Map<String, MultiMethod> methodsByName.calc()
        {
        assert meta.immutable_;

        ListMap<String, Method> map = new ListMap();
        for (Method method : methods)
            {
            if (MultiMethod multi : map.get(method.name))
                {
                map.put(method.name, multi.add(method));
                }
            else
                {
                map.put(method.name, new MultiMethod(method.to<Method[]>()));
                }
            }

        return map.makeConst();
        }

    /**
     * Determine if references and values of the specified type will be _assignable to_ references
     * of this type.
     *
     * let _T1_ and _T2_ be two types
     * * let _M1_ be the set of all methods in _T1_ (including those representing properties)
     * * let _M2_ be the set of all methods in _T2_ (including those representing properties)
     * * let _T2_ be a "derivative type" of _T1_ iff
     *   1. _T1_ originates from a Class _C1_
     *   2. _T2_ originates from a Class _C2_
     *   3. _C2_ is a derivative Class of _C1_
     * * if _T1_ and _T2_ are both parameterized types, let "same type parameter" be a type
     *   parameter of _T1_ that also is a type parameter of _T2_ because _T2_ is a derivative type
     *   of _T1_, or _T1_ is a derivative type of _T1_, or both _T1_ and _T2_ are derivative types
     *   of some _T3_.
     *
     * Type _T2_ is assignable to a Type _T1_ iff both of the following hold true:
     * 1. for each _m1_ in _M1_, there exists an _m2_ in _M2_ for which all of the following hold
     *    true:
     *    1. _m1_ and _m2_ have the same name
     *    2. _m1_ and _m2_ have the same number of parameters, and for each parameter type _p1_ of
     *       _m1_ and _p2_ of _m2_, at least one of the following holds true:
     *       1. _p1_ is assignable to _p2_
     *       2. both _p1_ and _p2_ are (or are resolved from) the same type parameter, and both of
     *          the following hold true:
     *          1. _p2_ is assignable to _p1_
     *          2. _t1_ produces _p1_
     *    3. _m1_ and _m2_ have the same number of return values, and for each return type _r1_ of
     *       _m1_ and _r2_ of _m2_, the following holds true:
     *      1. _r2_ is assignable to _r1_
     * 2. if _t1_ is explicitly immutable, then _t2_ must also be explicitly immutable.
     */
    Boolean isA(Type that)
        {
        if (this == that)
            {
            return true;
            }

        if (that.explicitlyImmutable && !this.explicitlyImmutable)
            {
            return false;
            }

        // this type must have a matching method for each method of that type
        nextMethod: for (Method thatMethod : that.allMethods)
            {
            // find the corresponding method on this type
            for (Method thisMethod : this.allMethodsByName[thatMethod.name].methods)
                {
                if (thisMethod.isSubstitutableFor(thatMethod))
                    {
                    continue nextMethod;
                    }
                }

            // no such matching method
            return false;
            }

        return true;
        }

    /**
     * Determine if this type _consumes_ that type.
     *
     * @see Method.consumes
     */
    Boolean consumes(Type that)
        {
        return methods.matchAny(method -> method.consumes(that));
        }

    /**
     * Determine if this type _produces_ that type.
     *
     * @see Method.produces
     */
    Boolean produces(Type that)
        {
        // TODO
        return methods.matchAny(method -> method.produces(that));
        }

    /**
     * Test whether the specified object is an {@code instanceof} this type.
     */
    Boolean isInstance(Object o)
        {
        return &o.ActualType.isA(this);
        }

    /**
     * Cast the specified object to this type.
     */
    DataType cast(Object o)
        {
        assert isInstance(o);
        return o.as(DataType);
        }

    // ----- dynamic type manipulation -------------------------------------------------------------

    /**
     * TODO should it be possible to create a new type from the union of two existing types?
     */
    Type add(Type that)
        {
        TODO +
        }

    /**
     * TODO should it be possible to explicitly remove things from a type?
     */
    Type sub(Type that)
        {
        TODO -
        }

    // ----- const contract ------------------------------------------------------------------------

    @Override
    @lazy Int hash.calc()
        {
        TODO hash
        }

    static Boolean equals(Type value1, Type value2)
        {
        TODO ==
        }

    static Order compare(Type value1, Type value2)
        {
        TODO <=>
        }

    // ----- ConstAble interface -------------------------------------------------------------------

    @Override
    immutable Type<DataType> ensureConst()
        {
        return this instanceof immutable Object
                ? this
                : new Type<DataType>(allMethods, explicitlyImmutable).makeConst();
        }

    @Override
    immutable Type<DataType> makeConst()
        {
        allMethods = allMethods.makeConst();
        meta.immutable_ = true;
        return this;
        }
    }
